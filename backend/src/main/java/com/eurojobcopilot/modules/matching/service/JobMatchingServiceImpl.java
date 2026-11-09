package com.eurojobcopilot.modules.matching.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceConflictException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobMatchBreakdownDto;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.config.MatchingProperties;
import com.eurojobcopilot.modules.matching.dto.BatchMatchRecalculationResponse;
import com.eurojobcopilot.modules.matching.dto.JobMatchSummaryItemDto;
import com.eurojobcopilot.modules.matching.dto.MatchingWeightsDto;
import com.eurojobcopilot.modules.matching.engine.CandidateMatchContext;
import com.eurojobcopilot.modules.matching.engine.DeterministicJobMatchingEngine;
import com.eurojobcopilot.modules.matching.engine.DeterministicMatchResult;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.mapper.JobMatchBreakdownMapper;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobMatchingServiceImpl implements JobMatchingService {

    private static final int DEFAULT_MIN_MATCH_THRESHOLD = 60;
    private static final int TOP_MATCHES_LIMIT = 10;

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserWorkExperienceRepository userWorkExperienceRepository;
    private final UserJobPreferenceRepository userJobPreferenceRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final JobMatchScoreRepository jobMatchScoreRepository;
    private final CanonicalHashGenerator canonicalHashGenerator;
    private final MatchingProperties matchingProperties;
    private final DeterministicJobMatchingEngine matchingEngine;

    @Override
    @Transactional
    public JobMatchBreakdownDto evaluateAndPersistJobMatch(AuthenticatedUserPrincipal currentUser, Long jobId) {
        User user = requireAuthenticatedUser(currentUser);
        if (jobId == null || jobId <= 0) {
            throw new BusinessValidationException("Job ID must be a positive integer");
        }

        Job job = jobRepository.findByIdWithCompany(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        CandidateMatchContext context = loadCandidateContext(user.getId());
        Company resolvedCompany = resolveCompany(job);

        JobMatchScore existing = jobMatchScoreRepository.findByUserIdAndJobId(user.getId(), jobId).orElse(null);
        DeterministicMatchResult result = matchingEngine.evaluate(
                context,
                job,
                resolvedCompany,
                existing != null ? existing.getAiScore() : null
        );

        Instant now = Instant.now();
        JobMatchScore entity = applyResultToEntity(existing, user, job, result, now);
        try {
            JobMatchScore saved = jobMatchScoreRepository.saveAndFlush(entity);
            log.info("Evaluated deterministic match userUuid={} jobId={} score={}%",
                    user.getUuid(), jobId, result.matchPercentage());
            return JobMatchBreakdownMapper.toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceConflictException("Concurrent match score update conflict. Please retry.");
        }
    }

    @Override
    @Transactional
    public BatchMatchRecalculationResponse recalculateActiveJobs(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        return recalculateActiveJobsInternal(user);
    }

    @Override
    @Transactional
    public BatchMatchRecalculationResponse recalculateActiveJobsForUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        return recalculateActiveJobsInternal(user);
    }

    @Override
    @Transactional
    public int ensureActiveMatchesUpToDate(Long userId) {
        if (userId == null) {
            return 0;
        }

        Optional<UserProfile> profileOpt = userProfileRepository.findByUserId(userId);
        Optional<UserJobPreference> prefOpt = userJobPreferenceRepository.findByUserId(userId);
        long skillsCount = userSkillRepository.countByUserId(userId);
        long workExpCount = userWorkExperienceRepository.countByUserId(userId);

        if (profileOpt.isEmpty() && prefOpt.isEmpty() && skillsCount == 0 && workExpCount == 0) {
            return 0;
        }

        Instant now = Instant.now();
        List<JobRepository.ActiveJobVersionView> activeVersions = jobRepository.findActiveNonExpiredVersions(
                now,
                PageRequest.of(0, matchingProperties.getMaxBatchJobs())
        );
        if (activeVersions.isEmpty()) {
            return 0;
        }

        List<Long> activeJobIds = activeVersions.stream()
                .map(JobRepository.ActiveJobVersionView::getId)
                .toList();

        long existingCount = jobMatchScoreRepository.countByUserIdAndJobIdIn(userId, activeJobIds);
        boolean needsRefresh = existingCount < activeJobIds.size();

        if (!needsRefresh) {
            Instant minCalculatedAt = jobMatchScoreRepository
                    .findMinCalculatedAtByUserIdAndJobIdIn(userId, activeJobIds)
                    .orElse(null);

            if (minCalculatedAt == null) {
                needsRefresh = true;
            } else {
                Instant latestDataChangeAt = maxInstant(
                        profileOpt.map(p -> p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt()).orElse(null),
                        prefOpt.map(p -> p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt()).orElse(null),
                        userSkillRepository.findLatestTimestampByUserId(userId).orElse(null),
                        userWorkExperienceRepository.findLatestTimestampByUserId(userId).orElse(null),
                        activeVersions.stream()
                                .map(JobRepository.ActiveJobVersionView::getChangedAt)
                                .filter(java.util.Objects::nonNull)
                                .max(Instant::compareTo)
                                .orElse(null)
                );

                if (latestDataChangeAt != null && minCalculatedAt.isBefore(latestDataChangeAt)) {
                    needsRefresh = true;
                }
            }
        }

        if (!needsRefresh) {
            return 0;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return 0;
        }
        return recalculateActiveJobsInternal(user).getEvaluatedJobsCount();
    }

    private BatchMatchRecalculationResponse recalculateActiveJobsInternal(User user) {
        Long userId = user.getId();

        CandidateMatchContext context = loadCandidateContext(userId);
        Instant now = Instant.now();

        List<Job> activeJobs = jobRepository.findActiveNonExpiredWithCompany(
                now,
                PageRequest.of(0, matchingProperties.getMaxBatchJobs())
        );

        int threshold = (context.preference() != null && context.preference().getMinMatchScore() != null)
                ? context.preference().getMinMatchScore()
                : DEFAULT_MIN_MATCH_THRESHOLD;

        if (activeJobs.isEmpty()) {
            return BatchMatchRecalculationResponse.builder()
                    .evaluatedJobsCount(0)
                    .highMatchJobsCount(0)
                    .minMatchThreshold(threshold)
                    .calculatedAt(now)
                    .weightsUsed(toWeightsDto())
                    .topMatches(List.of())
                    .build();
        }

        List<Long> jobIds = activeJobs.stream().map(Job::getId).toList();
        Map<Long, JobMatchScore> existingByJobId = jobMatchScoreRepository
                .findAllByUserIdAndJobIdIn(userId, jobIds)
                .stream()
                .collect(Collectors.toMap(
                        score -> score.getJob().getId(),
                        Function.identity(),
                        (first, second) -> first
                ));

        Map<String, Optional<Company>> fallbackCompanyCache = new HashMap<>();
        List<JobMatchScore> entitiesToSave = new ArrayList<>(activeJobs.size());
        List<JobMatchSummaryItemDto> scoredSummaries = new ArrayList<>(activeJobs.size());
        int highMatchCount = 0;

        for (Job job : activeJobs) {
            Company resolvedCompany = resolveCompanyCached(job, fallbackCompanyCache);
            JobMatchScore existing = existingByJobId.get(job.getId());
            DeterministicMatchResult result = matchingEngine.evaluate(
                    context,
                    job,
                    resolvedCompany,
                    existing != null ? existing.getAiScore() : null
            );

            entitiesToSave.add(applyResultToEntity(existing, user, job, result, now));
            if (result.matchPercentage() >= threshold) {
                highMatchCount++;
            }
            scoredSummaries.add(JobMatchSummaryItemDto.builder()
                    .jobId(job.getId())
                    .title(job.getTitle())
                    .companyName(job.getCompanyName())
                    .country(job.getCountry())
                    .matchPercentage(result.matchPercentage())
                    .finalScore(result.finalScore())
                    .experienceCompatibility(result.experienceCompatibility())
                    .build());
        }

        try {
            jobMatchScoreRepository.saveAllAndFlush(entitiesToSave);
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceConflictException("Concurrent match score batch update conflict. Please retry.");
        }

        List<JobMatchSummaryItemDto> topMatches = scoredSummaries.stream()
                .sorted(Comparator.comparing(JobMatchSummaryItemDto::getFinalScore).reversed()
                        .thenComparing(JobMatchSummaryItemDto::getJobId))
                .limit(TOP_MATCHES_LIMIT)
                .toList();

        log.info("Batch recalculated deterministic matches userUuid={} evaluated={} highMatch={}",
                user.getUuid(), activeJobs.size(), highMatchCount);

        return BatchMatchRecalculationResponse.builder()
                .evaluatedJobsCount(activeJobs.size())
                .highMatchJobsCount(highMatchCount)
                .minMatchThreshold(threshold)
                .calculatedAt(now)
                .weightsUsed(toWeightsDto())
                .topMatches(topMatches)
                .build();
    }

    private User requireAuthenticatedUser(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
    }

    private CandidateMatchContext loadCandidateContext(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        List<UserSkill> skills = userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(userId);
        List<UserWorkExperience> workExperiences = userWorkExperienceRepository.findAllByUserIdOrderByStartDateDesc(userId);
        UserJobPreference preference = userJobPreferenceRepository.findByUserId(userId).orElse(null);
        return new CandidateMatchContext(profile, skills, workExperiences, preference);
    }

    private Company resolveCompany(Job job) {
        if (job.getCompany() != null) {
            return job.getCompany();
        }
        String rawName = job.getCompanyName();
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String canonicalKey = canonicalHashGenerator.normalizeCompanyKey(rawName);
        if (!canonicalKey.isBlank()) {
            Optional<Company> byCanonical = companyRepository.findByNormalizedName(canonicalKey);
            if (byCanonical.isPresent()) {
                return byCanonical.get();
            }
        }
        String lowerTrimmed = rawName.trim().toLowerCase(Locale.ROOT);
        if (!lowerTrimmed.equals(canonicalKey)) {
            return companyRepository.findByNormalizedName(lowerTrimmed).orElse(null);
        }
        return null;
    }

    private Company resolveCompanyCached(Job job, Map<String, Optional<Company>> cache) {
        if (job.getCompany() != null) {
            return job.getCompany();
        }
        String rawName = job.getCompanyName();
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String cacheKey = rawName.trim().toLowerCase(Locale.ROOT);
        return cache.computeIfAbsent(cacheKey, ignored -> Optional.ofNullable(resolveCompany(job))).orElse(null);
    }

    private JobMatchScore applyResultToEntity(
            JobMatchScore existing,
            User user,
            Job job,
            DeterministicMatchResult result,
            Instant calculatedAt
    ) {
        JobMatchScore target = existing != null
                ? existing
                : JobMatchScore.builder()
                        .user(user)
                        .job(job)
                        .build();

        target.setDeterministicScore(result.deterministicScore());
        target.setFinalScore(result.finalScore());
        target.setSkillScore(result.skillScore());
        target.setExperienceScore(result.experienceScore());
        target.setTitleScore(result.titleScore());
        target.setLocationScore(result.locationScore());
        target.setSponsorshipScore(result.sponsorshipScore());
        target.setSalaryScore(result.salaryScore());
        target.setMatchingSkills(new ArrayList<>(result.matchingSkills()));
        target.setMissingSkills(new ArrayList<>(result.missingSkills()));
        target.setExperienceCompatibility(result.experienceCompatibility());
        target.setRecommendationReasons(new ArrayList<>(result.recommendationReasons()));
        target.setCalculatedAt(calculatedAt);
        return target;
    }

    private Instant maxInstant(Instant... candidates) {
        Instant latest = null;
        for (Instant candidate : candidates) {
            if (candidate != null && (latest == null || candidate.isAfter(latest))) {
                latest = candidate;
            }
        }
        return latest;
    }

    private MatchingWeightsDto toWeightsDto() {
        MatchingProperties.Weights w = matchingProperties.getWeights();
        return MatchingWeightsDto.builder()
                .technicalSkills(w.getTechnicalSkills())
                .experience(w.getExperience())
                .jobTitle(w.getJobTitle())
                .location(w.getLocation())
                .sponsorship(w.getSponsorship())
                .salary(w.getSalary())
                .build();
    }
}

