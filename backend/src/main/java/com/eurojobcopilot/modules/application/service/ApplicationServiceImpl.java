package com.eurojobcopilot.modules.application.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceConflictException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.application.dto.ApplicationDocumentSummaryDto;
import com.eurojobcopilot.modules.application.dto.ApplicationStatusHistoryDto;
import com.eurojobcopilot.modules.application.dto.CreateApplicationRequest;
import com.eurojobcopilot.modules.application.dto.JobApplicationDetailResponse;
import com.eurojobcopilot.modules.application.dto.UpdateApplicationRequest;
import com.eurojobcopilot.modules.application.dto.UpdateApplicationStatusRequest;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.entity.ApplicationStatusHistory;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.application.repository.ApplicationStatusHistoryRepository;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetter;
import com.eurojobcopilot.modules.coverletter.repository.CoverLetterRepository;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterInteractionRepository;
import com.eurojobcopilot.modules.resume.entity.TailoredResume;
import com.eurojobcopilot.modules.resume.repository.TailoredResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private static final Set<ApplicationStatus> POST_SUBMISSION_STATUSES = Set.of(
            ApplicationStatus.APPLIED,
            ApplicationStatus.ASSESSMENT,
            ApplicationStatus.TECH_INTERVIEW,
            ApplicationStatus.HR_INTERVIEW,
            ApplicationStatus.FINAL_INTERVIEW,
            ApplicationStatus.OFFER
    );

    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final UserJobInteractionRepository userJobInteractionRepository;
    private final JobMatchScoreRepository jobMatchScoreRepository;
    private final RecruiterInteractionRepository recruiterInteractionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<JobApplicationDetailResponse> listApplications(
            AuthenticatedUserPrincipal currentUser,
            ApplicationStatus status,
            String query,
            Boolean dueFollowUpOnly,
            Boolean hasInterviewOnly
    ) {
        User user = requireAuthenticatedUser(currentUser);
        Long userId = user.getId();

        List<JobApplication> all = jobApplicationRepository.findAllWithDetailsByUserId(userId);
        if (all.isEmpty()) {
            return List.of();
        }

        String normalizedQuery = hasText(query) ? query.trim().toLowerCase(Locale.ROOT) : null;
        LocalDate today = LocalDate.now();

        List<JobApplication> filtered = all.stream()
                .filter(app -> status == null || app.getStatus() == status)
                .filter(app -> matchesQuery(app, normalizedQuery))
                .filter(app -> !Boolean.TRUE.equals(dueFollowUpOnly)
                        || (app.getNextFollowUpDate() != null
                        && !app.isFollowUpCompleted()
                        && !app.getNextFollowUpDate().isAfter(today)))
                .filter(app -> !Boolean.TRUE.equals(hasInterviewOnly) || app.getNextInterviewDate() != null)
                .toList();

        if (filtered.isEmpty()) {
            return List.of();
        }

        List<Long> appIds = filtered.stream().map(JobApplication::getId).toList();
        List<Long> jobIds = filtered.stream().map(a -> a.getJob().getId()).distinct().toList();

        Map<Long, List<ApplicationStatusHistoryDto>> historyByAppId = statusHistoryRepository
                .findAllByApplicationIdInOrderByTransitionedAtDesc(appIds)
                .stream()
                .collect(Collectors.groupingBy(
                        h -> h.getApplication().getId(),
                        Collectors.mapping(this::toHistoryDto, Collectors.toList())
                ));

        Map<Long, Integer> matchPercentByJobId = jobMatchScoreRepository
                .findAllByUserIdAndJobIdIn(userId, jobIds)
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getJob().getId(),
                        this::toMatchPercent,
                        (first, second) -> first
                ));

        Map<Long, Long> interactionCountByAppId = recruiterInteractionRepository
                .countGroupedByApplicationIdIn(appIds)
                .stream()
                .filter(row -> row != null && row.length >= 2 && row[0] instanceof Long && row[1] instanceof Number)
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).longValue(),
                        (first, second) -> first
                ));

        return filtered.stream()
                .map(app -> toDetailResponse(
                        app,
                        historyByAppId.getOrDefault(app.getId(), Collections.emptyList()),
                        matchPercentByJobId.get(app.getJob().getId()),
                        interactionCountByAppId.getOrDefault(app.getId(), 0L),
                        today
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public JobApplicationDetailResponse getApplication(AuthenticatedUserPrincipal currentUser, String uuid) {
        User user = requireAuthenticatedUser(currentUser);
        JobApplication application = jobApplicationRepository
                .findWithDetailsByUuidAndUserId(uuid.trim(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tracked application not found"));

        return buildSingleDetailResponse(user.getId(), application);
    }

    @Override
    @Transactional
    public JobApplicationDetailResponse createApplication(
            AuthenticatedUserPrincipal currentUser,
            CreateApplicationRequest request
    ) {
        User user = requireAuthenticatedUser(currentUser);
        Job job = jobRepository.findByIdWithCompany(request.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + request.getJobId()));

        if (jobApplicationRepository.existsByUserIdAndJobId(user.getId(), job.getId())) {
            throw new ResourceConflictException("An application for this job is already tracked in your pipeline.");
        }

        ApplicationStatus initialStatus = request.getStatus() != null
                ? request.getStatus()
                : ApplicationStatus.INTERESTED;

        Instant effectiveAppliedDate = request.getAppliedDate();
        if (effectiveAppliedDate == null && POST_SUBMISSION_STATUSES.contains(initialStatus)) {
            effectiveAppliedDate = Instant.now();
        }

        TailoredResume tailoredResume = resolveTailoredResume(user.getId(), request.getTailoredResumeUuid());
        CoverLetter coverLetter = resolveCoverLetter(user.getId(), request.getCoverLetterUuid());

        String normalizedCurrency = normalizeOfferedCurrency(
                request.getOfferedCurrency(),
                request.getCurrentSalaryOffered(),
                null,
                job
        );

        JobApplication entity = JobApplication.builder()
                .user(user)
                .job(job)
                .status(initialStatus)
                .appliedDate(effectiveAppliedDate)
                .tailoredResume(tailoredResume)
                .coverLetter(coverLetter)
                .currentSalaryOffered(request.getCurrentSalaryOffered())
                .offeredCurrency(normalizedCurrency)
                .nextFollowUpDate(request.getNextFollowUpDate())
                .followUpCompleted(false)
                .nextInterviewDate(request.getNextInterviewDate())
                .notes(trimToNull(request.getNotes()))
                .build();

        try {
            entity = jobApplicationRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceConflictException("An application for this job is already tracked in your pipeline.");
        }

        String historyNote = trimToNull(request.getTransitionNotes());
        if (historyNote == null) {
            historyNote = "Added to application tracker in " + formatStatusLabel(initialStatus) + " stage.";
        }
        statusHistoryRepository.saveAndFlush(ApplicationStatusHistory.builder()
                .application(entity)
                .previousStatus(null)
                .newStatus(initialStatus)
                .transitionNotes(historyNote)
                .transitionedAt(Instant.now())
                .build());

        syncUserJobInteraction(user, job, initialStatus, effectiveAppliedDate);

        log.info("Created tracked application uuid={} userId={} jobId={} status={}",
                entity.getUuid(), user.getId(), job.getId(), initialStatus);

        JobApplication reloaded = jobApplicationRepository
                .findWithDetailsByUuidAndUserId(entity.getUuid(), user.getId())
                .orElse(entity);
        return buildSingleDetailResponse(user.getId(), reloaded);
    }

    @Override
    @Transactional
    public JobApplicationDetailResponse updateApplication(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            UpdateApplicationRequest request
    ) {
        User user = requireAuthenticatedUser(currentUser);
        JobApplication entity = jobApplicationRepository
                .findWithDetailsByUuidAndUserId(uuid.trim(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tracked application not found"));

        if (request.getExpectedVersion() != null && !Objects.equals(request.getExpectedVersion(), entity.getVersion())) {
            throw new ResourceConflictException("Application record was modified concurrently. Please refresh and retry.");
        }

        if (request.isClearTailoredResume()) {
            entity.setTailoredResume(null);
        } else if (hasText(request.getTailoredResumeUuid())) {
            entity.setTailoredResume(resolveTailoredResume(user.getId(), request.getTailoredResumeUuid()));
        }

        if (request.isClearCoverLetter()) {
            entity.setCoverLetter(null);
        } else if (hasText(request.getCoverLetterUuid())) {
            entity.setCoverLetter(resolveCoverLetter(user.getId(), request.getCoverLetterUuid()));
        }

        if (request.isClearAppliedDate()) {
            entity.setAppliedDate(null);
        } else if (request.getAppliedDate() != null) {
            entity.setAppliedDate(request.getAppliedDate());
        }

        if (request.isClearNextFollowUpDate()) {
            entity.setNextFollowUpDate(null);
            entity.setFollowUpCompleted(false);
        } else if (request.getNextFollowUpDate() != null) {
            boolean dateChanged = !Objects.equals(entity.getNextFollowUpDate(), request.getNextFollowUpDate());
            entity.setNextFollowUpDate(request.getNextFollowUpDate());
            if (dateChanged && request.getFollowUpCompleted() == null) {
                entity.setFollowUpCompleted(false);
            }
        }

        if (request.getFollowUpCompleted() != null) {
            entity.setFollowUpCompleted(request.getFollowUpCompleted());
        }

        if (request.isClearNextInterviewDate()) {
            entity.setNextInterviewDate(null);
        } else if (request.getNextInterviewDate() != null) {
            entity.setNextInterviewDate(request.getNextInterviewDate());
        }

        if (request.isClearSalaryOffer()) {
            entity.setCurrentSalaryOffered(null);
            entity.setOfferedCurrency(null);
        } else {
            if (request.getCurrentSalaryOffered() != null) {
                entity.setCurrentSalaryOffered(request.getCurrentSalaryOffered());
            }
            if (hasText(request.getOfferedCurrency()) || entity.getCurrentSalaryOffered() != null) {
                entity.setOfferedCurrency(normalizeOfferedCurrency(
                        request.getOfferedCurrency(),
                        entity.getCurrentSalaryOffered(),
                        entity.getOfferedCurrency(),
                        entity.getJob()
                ));
            }
        }

        if (request.getNotes() != null) {
            entity.setNotes(trimToNull(request.getNotes()));
        }

        entity = jobApplicationRepository.saveAndFlush(entity);
        log.info("Updated tracked application uuid={} userId={}", entity.getUuid(), user.getId());

        return buildSingleDetailResponse(user.getId(), entity);
    }

    @Override
    @Transactional
    public JobApplicationDetailResponse updateApplicationStatus(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            UpdateApplicationStatusRequest request
    ) {
        User user = requireAuthenticatedUser(currentUser);
        JobApplication entity = jobApplicationRepository
                .findWithDetailsByUuidAndUserId(uuid.trim(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tracked application not found"));

        ApplicationStatus previousStatus = entity.getStatus();
        ApplicationStatus nextStatus = request.getNewStatus();
        String transitionNotes = trimToNull(request.getTransitionNotes());

        entity.setStatus(nextStatus);

        if (request.getAppliedDate() != null) {
            entity.setAppliedDate(request.getAppliedDate());
        } else if (entity.getAppliedDate() == null && POST_SUBMISSION_STATUSES.contains(nextStatus)) {
            entity.setAppliedDate(Instant.now());
        }

        if (request.getNextInterviewDate() != null) {
            entity.setNextInterviewDate(request.getNextInterviewDate());
        }
        if (request.getNextFollowUpDate() != null) {
            boolean dateChanged = !Objects.equals(entity.getNextFollowUpDate(), request.getNextFollowUpDate());
            entity.setNextFollowUpDate(request.getNextFollowUpDate());
            if (dateChanged) {
                entity.setFollowUpCompleted(false);
            }
        }

        entity = jobApplicationRepository.saveAndFlush(entity);

        if (previousStatus != nextStatus || transitionNotes != null) {
            String note = transitionNotes != null
                    ? transitionNotes
                    : "Moved from " + formatStatusLabel(previousStatus) + " to " + formatStatusLabel(nextStatus) + ".";
            statusHistoryRepository.saveAndFlush(ApplicationStatusHistory.builder()
                    .application(entity)
                    .previousStatus(previousStatus)
                    .newStatus(nextStatus)
                    .transitionNotes(note)
                    .transitionedAt(Instant.now())
                    .build());
        }

        syncUserJobInteraction(user, entity.getJob(), nextStatus, entity.getAppliedDate());

        log.info("Transitioned application uuid={} userId={} {} -> {}",
                entity.getUuid(), user.getId(), previousStatus, nextStatus);

        return buildSingleDetailResponse(user.getId(), entity);
    }

    @Override
    @Transactional
    public void deleteApplication(AuthenticatedUserPrincipal currentUser, String uuid) {
        User user = requireAuthenticatedUser(currentUser);
        JobApplication entity = jobApplicationRepository
                .findWithDetailsByUuidAndUserId(uuid.trim(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tracked application not found"));

        recruiterInteractionRepository.clearApplicationReferences(entity.getId());
        statusHistoryRepository.deleteAllByApplicationId(entity.getId());
        jobApplicationRepository.delete(entity);
        jobApplicationRepository.flush();

        log.info("Deleted tracked application uuid={} userId={}", uuid, user.getId());
    }

    private JobApplicationDetailResponse buildSingleDetailResponse(Long userId, JobApplication app) {
        List<ApplicationStatusHistoryDto> history = statusHistoryRepository
                .findAllByApplicationIdOrderByTransitionedAtDesc(app.getId())
                .stream()
                .map(this::toHistoryDto)
                .toList();

        Integer matchPct = jobMatchScoreRepository
                .findByUserIdAndJobId(userId, app.getJob().getId())
                .map(this::toMatchPercent)
                .orElse(null);

        long interactionsCount = recruiterInteractionRepository
                .findAllByApplicationIdOrderByCreatedAtDesc(app.getId())
                .size();

        return toDetailResponse(app, history, matchPct, interactionsCount, LocalDate.now());
    }

    private JobApplicationDetailResponse toDetailResponse(
            JobApplication app,
            List<ApplicationStatusHistoryDto> history,
            Integer matchPercent,
            long recruiterInteractionsCount,
            LocalDate today
    ) {
        Job job = app.getJob();
        boolean overdue = app.getNextFollowUpDate() != null
                && !app.isFollowUpCompleted()
                && app.getNextFollowUpDate().isBefore(today);

        return JobApplicationDetailResponse.builder()
                .uuid(app.getUuid())
                .jobId(job.getId())
                .jobTitle(job.getTitle())
                .companyId(job.getCompany() != null ? job.getCompany().getId() : null)
                .companyName(job.getCompanyName())
                .jobCountry(job.getCountry())
                .jobCity(job.getCity())
                .jobLocation(job.getLocation())
                .workArrangement(job.getWorkArrangement())
                .employmentType(job.getEmploymentType())
                .sponsorStatus(job.getSponsorStatus())
                .jobSalaryMin(job.getSalaryMin())
                .jobSalaryMax(job.getSalaryMax())
                .jobSalaryCurrency(job.getSalaryCurrency())
                .jobOriginalUrl(job.getOriginalUrl())
                .jobApplicationUrl(job.getApplicationUrl())
                .matchPercentage(matchPercent)
                .status(app.getStatus())
                .appliedDate(app.getAppliedDate())
                .tailoredResume(toTailoredResumeSummary(app.getTailoredResume()))
                .coverLetter(toCoverLetterSummary(app.getCoverLetter()))
                .currentSalaryOffered(app.getCurrentSalaryOffered())
                .offeredCurrency(app.getOfferedCurrency())
                .nextFollowUpDate(app.getNextFollowUpDate())
                .followUpCompleted(app.isFollowUpCompleted())
                .overdueFollowUp(overdue)
                .nextInterviewDate(app.getNextInterviewDate())
                .notes(app.getNotes())
                .version(app.getVersion())
                .recruiterInteractionsCount(recruiterInteractionsCount)
                .statusHistory(history)
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }

    private ApplicationDocumentSummaryDto toTailoredResumeSummary(TailoredResume tr) {
        if (tr == null) {
            return null;
        }
        return ApplicationDocumentSummaryDto.builder()
                .uuid(tr.getUuid())
                .title(tr.getTitle())
                .versionNumber(tr.getVersionNumber())
                .subtitle(tr.getTemplateName() != null ? tr.getTemplateName().name() : null)
                .updatedAt(tr.getUpdatedAt())
                .build();
    }

    private ApplicationDocumentSummaryDto toCoverLetterSummary(CoverLetter cl) {
        if (cl == null) {
            return null;
        }
        return ApplicationDocumentSummaryDto.builder()
                .uuid(cl.getUuid())
                .title(cl.getTitle())
                .versionNumber(cl.getVersionNumber())
                .subtitle(cl.getTone() != null ? cl.getTone().name() : null)
                .updatedAt(cl.getUpdatedAt())
                .build();
    }

    private ApplicationStatusHistoryDto toHistoryDto(ApplicationStatusHistory history) {
        return ApplicationStatusHistoryDto.builder()
                .id(history.getId())
                .previousStatus(history.getPreviousStatus())
                .newStatus(history.getNewStatus())
                .transitionNotes(history.getTransitionNotes())
                .transitionedAt(history.getTransitionedAt())
                .build();
    }

    private void syncUserJobInteraction(User user, Job job, ApplicationStatus status, Instant appliedDate) {
        if (user == null || job == null) {
            return;
        }
        UserJobInteraction interaction = userJobInteractionRepository
                .findByUserIdAndJobId(user.getId(), job.getId())
                .orElseGet(() -> UserJobInteraction.builder()
                        .user(user)
                        .job(job)
                        .build());

        boolean dirty = interaction.getId() == null;
        Instant now = Instant.now();

        if (status == ApplicationStatus.SAVED && !interaction.isSaved()) {
            interaction.setSaved(true);
            interaction.setSavedAt(now);
            dirty = true;
        }
        if (status == ApplicationStatus.INTERESTED && !interaction.isInterested()) {
            interaction.setInterested(true);
            interaction.setInterestedAt(now);
            dirty = true;
        }
        if (POST_SUBMISSION_STATUSES.contains(status) && !interaction.isHasApplied()) {
            interaction.setHasApplied(true);
            interaction.setAppliedAt(appliedDate != null ? appliedDate : now);
            dirty = true;
        }

        if (dirty) {
            userJobInteractionRepository.saveAndFlush(interaction);
        }
    }

    private TailoredResume resolveTailoredResume(Long userId, String uuid) {
        if (!hasText(uuid)) {
            return null;
        }
        return tailoredResumeRepository.findWithDetailsByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tailored resume not found"));
    }

    private CoverLetter resolveCoverLetter(Long userId, String uuid) {
        if (!hasText(uuid)) {
            return null;
        }
        return coverLetterRepository.findWithDetailsByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cover letter not found"));
    }

    private User requireAuthenticatedUser(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
    }

    private String normalizeOfferedCurrency(
            String requestedCurrency,
            java.math.BigDecimal effectiveSalary,
            String existingCurrency,
            Job job
    ) {
        if (hasText(requestedCurrency)) {
            return requestedCurrency.trim().toUpperCase(Locale.ROOT);
        }
        if (effectiveSalary != null) {
            if (hasText(existingCurrency)) {
                return existingCurrency.trim().toUpperCase(Locale.ROOT);
            }
            if (job != null && hasText(job.getSalaryCurrency()) && job.getSalaryCurrency().trim().length() == 3) {
                return job.getSalaryCurrency().trim().toUpperCase(Locale.ROOT);
            }
            return "EUR";
        }
        return existingCurrency;
    }

    private boolean matchesQuery(JobApplication app, String normalizedQuery) {
        if (normalizedQuery == null) {
            return true;
        }
        Job job = app.getJob();
        return containsLower(job.getTitle(), normalizedQuery)
                || containsLower(job.getCompanyName(), normalizedQuery)
                || containsLower(job.getCountry(), normalizedQuery)
                || containsLower(job.getCity(), normalizedQuery)
                || containsLower(app.getNotes(), normalizedQuery);
    }

    private Integer toMatchPercent(JobMatchScore score) {
        if (score == null || score.getFinalScore() == null) {
            return null;
        }
        int rounded = score.getFinalScore().setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, rounded));
    }

    private String formatStatusLabel(ApplicationStatus status) {
        if (status == null) {
            return "Pipeline";
        }
        return switch (status) {
            case INTERESTED -> "Interested";
            case SAVED -> "Saved";
            case APPLIED -> "Applied";
            case ASSESSMENT -> "Assessment";
            case TECH_INTERVIEW -> "Tech Interview";
            case HR_INTERVIEW -> "HR Interview";
            case FINAL_INTERVIEW -> "Final Interview";
            case OFFER -> "Offer";
            case REJECTED -> "Rejected";
            case WITHDRAWN -> "Withdrawn";
        };
    }

    private boolean containsLower(String source, String normalizedQuery) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
