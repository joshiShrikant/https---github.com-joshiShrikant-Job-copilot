package com.eurojobcopilot.modules.matching.ai.service;

import com.eurojobcopilot.core.exception.ResourceConflictException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.dto.JobMatchBreakdownDto;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.ai.model.RawAiSemanticMatchPayload;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.EducationEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.JobAiAnalysisEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.PreferenceEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.ProfileEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.ResumeEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.SkillEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.WorkExperienceEvidence;
import com.eurojobcopilot.modules.matching.ai.model.ValidatedSemanticMatchResult;
import com.eurojobcopilot.modules.matching.engine.CandidateMatchContext;
import com.eurojobcopilot.modules.matching.engine.DeterministicJobMatchingEngine;
import com.eurojobcopilot.modules.matching.engine.DeterministicMatchResult;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.mapper.JobMatchBreakdownMapper;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.user.entity.UserEducation;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Handles short read/write database transactions separately from external LLM network execution:
 * loads detached semantic evidence snapshots before AI calls and persists validated semantic
 * insights without overwriting deterministic rule-based breakdown metrics.
 */
@Component
@RequiredArgsConstructor
public class SemanticJobMatchingPersistenceHelper {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserWorkExperienceRepository userWorkExperienceRepository;
    private final UserEducationRepository userEducationRepository;
    private final UserJobPreferenceRepository userJobPreferenceRepository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final JobAiAnalysisRepository jobAiAnalysisRepository;
    private final CompanyRepository companyRepository;
    private final JobMatchScoreRepository jobMatchScoreRepository;
    private final DeterministicJobMatchingEngine deterministicEngine;
    private final JobNormalizer jobNormalizer;
    private final CanonicalHashGenerator canonicalHashGenerator;

    @Transactional(readOnly = true)
    public SemanticMatchContextSnapshot loadEvaluationSnapshot(Long userId, Long jobId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        Job job = jobRepository.findByIdWithCompany(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        List<UserSkill> skills = userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(userId);
        List<UserWorkExperience> workExperiences = userWorkExperienceRepository.findAllByUserIdOrderByStartDateDesc(userId);
        List<UserEducation> educations = userEducationRepository.findAllByUserIdOrderByStartDateDesc(userId);
        UserJobPreference preference = userJobPreferenceRepository.findByUserId(userId).orElse(null);
        Resume masterResume = resolveMasterResume(userId);
        Company resolvedCompany = resolveCompany(job);
        JobAiAnalysis jobAiAnalysis = jobAiAnalysisRepository.findByJobId(jobId).orElse(null);
        JobMatchScore existingScore = jobMatchScoreRepository.findByUserIdAndJobId(userId, jobId).orElse(null);

        JobMatchBreakdownDto cachedAi = (existingScore != null && existingScore.getAiEvaluatedAt() != null)
                ? JobMatchBreakdownMapper.toDto(existingScore)
                : null;

        return new SemanticMatchContextSnapshot(
                userId,
                jobId,
                toProfileEvidence(user, profile),
                toPreferenceEvidence(preference),
                skills.stream().map(this::toSkillEvidence).toList(),
                workExperiences.stream().map(this::toWorkExperienceEvidence).toList(),
                educations.stream().map(this::toEducationEvidence).toList(),
                toResumeEvidence(masterResume),
                toJobEvidence(job, resolvedCompany),
                toJobAiAnalysisEvidence(jobAiAnalysis),
                cachedAi
        );
    }

    @Transactional
    public JobMatchBreakdownDto saveValidatedSemanticMatch(
            Long userId,
            Long jobId,
            ValidatedSemanticMatchResult validated,
            AiStructuredResponse<RawAiSemanticMatchPayload> aiResponse
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        Job job = jobRepository.findByIdWithCompany(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        JobMatchScore existing = jobMatchScoreRepository.findByUserIdAndJobId(userId, jobId).orElse(null);
        Instant now = Instant.now();

        JobMatchScore entity;
        if (existing == null) {
            UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
            List<UserSkill> skills = userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(userId);
            List<UserWorkExperience> workExperiences = userWorkExperienceRepository.findAllByUserIdOrderByStartDateDesc(userId);
            UserJobPreference preference = userJobPreferenceRepository.findByUserId(userId).orElse(null);
            CandidateMatchContext context = new CandidateMatchContext(profile, skills, workExperiences, preference);
            Company resolvedCompany = resolveCompany(job);

            DeterministicMatchResult detResult = deterministicEngine.evaluate(
                    context,
                    job,
                    resolvedCompany,
                    validated.aiScore()
            );

            entity = JobMatchScore.builder()
                    .user(user)
                    .job(job)
                    .deterministicScore(detResult.deterministicScore())
                    .finalScore(detResult.finalScore())
                    .skillScore(detResult.skillScore())
                    .experienceScore(detResult.experienceScore())
                    .titleScore(detResult.titleScore())
                    .locationScore(detResult.locationScore())
                    .sponsorshipScore(detResult.sponsorshipScore())
                    .salaryScore(detResult.salaryScore())
                    .matchingSkills(new ArrayList<>(detResult.matchingSkills()))
                    .missingSkills(new ArrayList<>(detResult.missingSkills()))
                    .experienceCompatibility(detResult.experienceCompatibility())
                    .recommendationReasons(new ArrayList<>(detResult.recommendationReasons()))
                    .calculatedAt(now)
                    .build();
        } else {
            entity = existing;
            BigDecimal recomputedFinal = deterministicEngine.computeFinalScore(
                    existing.getDeterministicScore(),
                    validated.aiScore()
            );
            entity.setFinalScore(recomputedFinal);
        }

        entity.setAiScore(validated.aiScore());
        entity.setAiRationale(validated.rationale());
        entity.setAiStrongMatches(new ArrayList<>(validated.strongMatchingAreas()));
        entity.setAiMissingSkills(new ArrayList<>(validated.missingSkills()));
        entity.setAiExperienceGaps(new ArrayList<>(validated.experienceGaps()));
        entity.setAiPotentialConcerns(new ArrayList<>(validated.potentialConcerns()));
        entity.setAiImprovementSuggestions(new ArrayList<>(validated.improvementSuggestions()));
        entity.setAiProvider(aiResponse != null ? aiResponse.provider() : null);
        entity.setAiModelUsed(aiResponse != null ? aiResponse.model() : null);
        entity.setAiEvaluatedAt(now);

        try {
            JobMatchScore saved = jobMatchScoreRepository.saveAndFlush(entity);
            return JobMatchBreakdownMapper.toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceConflictException("Concurrent AI match score update conflict. Please retry.");
        }
    }

    private Resume resolveMasterResume(Long userId) {
        return resumeRepository.findFirstByUserIdAndMasterApprovedTrueOrderByVersionNumberDesc(userId)
                .or(() -> resumeRepository.findAllByUserIdOrderByVersionNumberDescCreatedAtDesc(userId).stream().findFirst())
                .orElse(null);
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

    private ProfileEvidence toProfileEvidence(User user, UserProfile profile) {
        if (profile == null) {
            return null;
        }
        String fullName = ((user.getFirstName() != null ? user.getFirstName().trim() : "")
                + " " + (user.getLastName() != null ? user.getLastName().trim() : "")).trim();
        return new ProfileEvidence(
                fullName,
                profile.getCurrentDesignation(),
                profile.getYearsOfExperience(),
                profile.getCurrentCountry(),
                profile.getCurrentCity(),
                profile.getExpectedSalaryMin(),
                profile.getExpectedSalaryCurrency(),
                profile.getNoticePeriodDays(),
                profile.getWorkAuthorizationStatus() != null ? profile.getWorkAuthorizationStatus().name() : null,
                profile.getRelocationPreference() != null ? profile.getRelocationPreference().name() : null,
                profile.getLinkedinUrl(),
                profile.getGithubUrl(),
                profile.getPortfolioUrl(),
                profile.getBio()
        );
    }

    private PreferenceEvidence toPreferenceEvidence(UserJobPreference pref) {
        if (pref == null) {
            return null;
        }
        return new PreferenceEvidence(
                pref.getPreferredJobTitles() == null ? List.of() : List.copyOf(pref.getPreferredJobTitles()),
                pref.getPreferredCountries() == null ? List.of() : List.copyOf(pref.getPreferredCountries()),
                pref.getPreferredCities() == null ? List.of() : List.copyOf(pref.getPreferredCities()),
                pref.getMinSalary(),
                pref.getSalaryCurrency(),
                pref.isRequiresSponsorship(),
                pref.isRequiresRelocation(),
                pref.getWorkArrangement() != null ? pref.getWorkArrangement().name() : null,
                pref.isEnglishSpeakingOnly()
        );
    }

    private SkillEvidence toSkillEvidence(UserSkill skill) {
        return new SkillEvidence(
                skill.getSkillName(),
                skill.getYearsExperience(),
                skill.getProficiencyLevel() != null ? skill.getProficiencyLevel().name() : null,
                skill.isPrimary()
        );
    }

    private WorkExperienceEvidence toWorkExperienceEvidence(UserWorkExperience exp) {
        return new WorkExperienceEvidence(
                exp.getCompanyName(),
                exp.getDesignation(),
                exp.getLocation(),
                exp.getStartDate(),
                exp.getEndDate(),
                exp.isCurrent(),
                exp.getDescription(),
                exp.getSkillsUsed() == null ? List.of() : List.copyOf(exp.getSkillsUsed())
        );
    }

    private EducationEvidence toEducationEvidence(UserEducation edu) {
        return new EducationEvidence(
                edu.getInstitution(),
                edu.getDegree(),
                edu.getFieldOfStudy(),
                edu.getStartDate(),
                edu.getEndDate(),
                edu.getGradeOrGpa(),
                edu.getCountry()
        );
    }

    private ResumeEvidence toResumeEvidence(Resume resume) {
        if (resume == null) {
            return null;
        }
        Map<String, Object> parsedCopy = resume.getParsedContentJson() == null
                ? Map.of()
                : new LinkedHashMap<>(resume.getParsedContentJson());
        return new ResumeEvidence(
                resume.getUuid(),
                resume.getTitle(),
                resume.getFileName(),
                resume.getVersionNumber(),
                resume.isMasterApproved(),
                parsedCopy
        );
    }

    private JobEvidence toJobEvidence(Job job, Company resolvedCompany) {
        String cleanDesc = jobNormalizer.sanitizeHtmlToText(job.getDescription());
        if (cleanDesc.isBlank() && job.getDescription() != null) {
            cleanDesc = job.getDescription().trim();
        }
        return new JobEvidence(
                job.getTitle(),
                job.getCompanyName(),
                job.getCountry(),
                job.getCity(),
                job.getLocation(),
                job.getWorkArrangement() != null ? job.getWorkArrangement().name() : null,
                job.getEmploymentType() != null ? job.getEmploymentType().name() : null,
                job.getExperienceLevel() != null ? job.getExperienceLevel().name() : null,
                job.getMinExperienceYears(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency(),
                job.getSalaryPeriod() != null ? job.getSalaryPeriod().name() : null,
                job.getSponsorStatus() != null ? job.getSponsorStatus().name() : null,
                resolvedCompany != null && resolvedCompany.isVerifiedSponsor(),
                resolvedCompany != null && resolvedCompany.getSponsorConfidenceLevel() != null
                        ? resolvedCompany.getSponsorConfidenceLevel().name()
                        : null,
                resolvedCompany != null ? resolvedCompany.getIndustry() : null,
                job.getSkills() == null ? List.of() : List.copyOf(job.getSkills()),
                cleanDesc
        );
    }

    private JobAiAnalysisEvidence toJobAiAnalysisEvidence(JobAiAnalysis ai) {
        if (ai == null) {
            return null;
        }
        return new JobAiAnalysisEvidence(
                ai.getRequiredExperienceSummary(),
                ai.getRequiredSkills() == null ? List.of() : List.copyOf(ai.getRequiredSkills()),
                ai.getNiceToHaveSkills() == null ? List.of() : List.copyOf(ai.getNiceToHaveSkills()),
                ai.getResponsibilities() == null ? List.of() : List.copyOf(ai.getResponsibilities()),
                ai.getLanguageRequirements() == null ? List.of() : List.copyOf(ai.getLanguageRequirements()),
                ai.getVisaSponsorshipAnalysis(),
                ai.getRelocationAnalysis(),
                ai.getSummary()
        );
    }
}

