package com.eurojobcopilot.modules.autopilot.service;

import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.ai.exception.AiProviderException;
import com.eurojobcopilot.modules.ai.exception.AiServiceUnavailableException;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.autopilot.config.AutopilotProperties;
import com.eurojobcopilot.modules.autopilot.dto.AutopilotRunLogDto;
import com.eurojobcopilot.modules.autopilot.dto.AutopilotStatusResponse;
import com.eurojobcopilot.modules.autopilot.dto.PagedAutopilotHistoryResponse;
import com.eurojobcopilot.modules.autopilot.dto.PagedRecommendedJobsResponse;
import com.eurojobcopilot.modules.autopilot.dto.RecommendedJobItemDto;
import com.eurojobcopilot.modules.autopilot.dto.RunAutopilotRequest;
import com.eurojobcopilot.modules.autopilot.entity.AutopilotRunLog;
import com.eurojobcopilot.modules.autopilot.entity.AutopilotRunStatus;
import com.eurojobcopilot.modules.autopilot.entity.AutopilotTriggerType;
import com.eurojobcopilot.modules.autopilot.repository.AutopilotRunLogRepository;
import com.eurojobcopilot.modules.job.dto.JobSummaryDto;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.ingestion.IngestionQuery;
import com.eurojobcopilot.modules.job.ingestion.JobIngestionProperties;
import com.eurojobcopilot.modules.job.ingestion.JobIngestionService;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import com.eurojobcopilot.modules.matching.ai.service.SemanticJobMatchingService;
import com.eurojobcopilot.modules.matching.dto.BatchMatchRecalculationResponse;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.matching.service.JobMatchingService;
import com.eurojobcopilot.modules.notification.config.NotificationProperties;
import com.eurojobcopilot.modules.notification.entity.NotificationReferenceType;
import com.eurojobcopilot.modules.notification.entity.NotificationType;
import com.eurojobcopilot.modules.notification.repository.NotificationRepository;
import com.eurojobcopilot.modules.notification.service.AutopilotEmailNotifier;
import com.eurojobcopilot.modules.notification.service.NotificationService;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
public class AutopilotServiceImpl implements AutopilotService {

    private static final int DEFAULT_MIN_MATCH_SCORE = 60;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int SNIPPET_MAX_LENGTH = 500;
    private static final Duration NOTIFICATION_DEDUPE_WINDOW = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final UserJobPreferenceRepository preferenceRepository;
    private final AutopilotRunLogRepository runLogRepository;
    private final JobRepository jobRepository;
    private final JobAiAnalysisRepository jobAiAnalysisRepository;
    private final JobMatchScoreRepository jobMatchScoreRepository;
    private final UserJobInteractionRepository interactionRepository;
    private final JobApplicationRepository applicationRepository;
    private final CompanyRepository companyRepository;
    private final CanonicalHashGenerator canonicalHashGenerator;
    private final JobMatchingService jobMatchingService;
    private final SemanticJobMatchingService semanticJobMatchingService;
    private final JobIngestionService jobIngestionService;
    private final JobIngestionProperties ingestionProperties;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final AutopilotEmailNotifier emailNotifier;
    private final AutopilotProperties autopilotProperties;
    private final NotificationProperties notificationProperties;

    @Override
    @Transactional
    public AutopilotStatusResponse getStatus(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        UserJobPreference pref = preferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> defaultPreference(user));

        jobMatchingService.ensureActiveMatchesUpToDate(user.getId());

        List<RecommendedJobItemDto> ranked = buildRecommendedItems(user.getId(), pref, null, false, false);
        long unreadCount = notificationRepository.countByUserIdAndReadFalse(user.getId());
        AutopilotRunLogDto latestRun = runLogRepository.findFirstByUserIdOrderByStartedAtDescIdDesc(user.getId())
                .map(this::toRunLogDto)
                .orElse(null);

        boolean serverEmailEnabled = notificationProperties.getEmail() != null
                && notificationProperties.getEmail().isEnabled();

        return AutopilotStatusResponse.builder()
                .autopilotEnabled(pref.isAutopilotEnabled())
                .autopilotPaused(pref.isAutopilotPaused())
                .emailNotificationsEnabled(pref.isEmailNotificationsEnabled())
                .serverEmailRelayEnabled(serverEmailEnabled)
                .lastAutopilotRunAt(pref.getLastAutopilotRunAt())
                .minMatchScore(resolveMinMatchScore(pref))
                .preferredJobTitles(safeCopy(pref.getPreferredJobTitles()))
                .preferredCountries(safeCopy(pref.getPreferredCountries()))
                .preferredCities(safeCopy(pref.getPreferredCities()))
                .recommendedJobsCount(ranked.size())
                .unreadNotificationsCount(unreadCount)
                .latestRun(latestRun)
                .build();
    }

    @Override
    @Transactional
    public AutopilotRunLogDto runManualScan(AuthenticatedUserPrincipal currentUser, RunAutopilotRequest request) {
        User user = requireAuthenticatedUser(currentUser);
        RunAutopilotRequest effectiveRequest = request != null ? request : new RunAutopilotRequest();
        return executeScanInternal(currentUser, user, AutopilotTriggerType.MANUAL, effectiveRequest);
    }

    @Override
    @Transactional
    public AutopilotRunLogDto executeScheduledScan(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        return executeScanInternal(
                currentUser,
                user,
                AutopilotTriggerType.SCHEDULED,
                RunAutopilotRequest.builder()
                        .refreshExternalJobs(false)
                        .analyzeTopMatchesWithAi(false)
                        .build()
        );
    }

    @Override
    @Transactional
    public AutopilotStatusResponse pauseAutopilot(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        UserJobPreference pref = getOrCreatePersistentPreference(user);
        pref.setAutopilotPaused(true);
        preferenceRepository.saveAndFlush(pref);
        log.info("Paused job search autopilot for userUuid={}", user.getUuid());
        return getStatus(currentUser);
    }

    @Override
    @Transactional
    public AutopilotStatusResponse resumeAutopilot(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        UserJobPreference pref = getOrCreatePersistentPreference(user);
        pref.setAutopilotEnabled(true);
        pref.setAutopilotPaused(false);
        preferenceRepository.saveAndFlush(pref);
        log.info("Resumed job search autopilot for userUuid={}", user.getUuid());
        return getStatus(currentUser);
    }

    @Override
    @Transactional
    public PagedRecommendedJobsResponse getRecommendedJobs(
            AuthenticatedUserPrincipal currentUser,
            Integer minMatchScore,
            boolean verifiedSponsorsOnly,
            boolean excludeApplied,
            int page,
            int size
    ) {
        User user = requireAuthenticatedUser(currentUser);
        UserJobPreference pref = preferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> defaultPreference(user));

        int effectiveMinScore = minMatchScore != null
                ? Math.max(0, Math.min(100, minMatchScore))
                : resolveMinMatchScore(pref);

        jobMatchingService.ensureActiveMatchesUpToDate(user.getId());

        List<RecommendedJobItemDto> allRecommended = buildRecommendedItems(
                user.getId(),
                pref,
                effectiveMinScore,
                verifiedSponsorsOnly,
                excludeApplied
        );

        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, size > 0 ? size : DEFAULT_PAGE_SIZE);
        int totalElements = allRecommended.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);

        int fromIdx = Math.min(safePage * safeSize, totalElements);
        int toIdx = Math.min(fromIdx + safeSize, totalElements);
        List<RecommendedJobItemDto> slice = allRecommended.subList(fromIdx, toIdx);

        long verifiedSponsorCount = allRecommended.stream().filter(RecommendedJobItemDto::isVerifiedSponsor).count();
        long highMatchCount = allRecommended.stream().filter(RecommendedJobItemDto::isMeetsMinMatchScore).count();

        return PagedRecommendedJobsResponse.builder()
                .content(slice)
                .pageNumber(safePage)
                .pageSize(safeSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(safePage == 0)
                .last(totalPages == 0 || safePage >= totalPages - 1)
                .hasNext(totalPages > 0 && safePage < totalPages - 1)
                .hasPrevious(safePage > 0)
                .minMatchScoreUsed(effectiveMinScore)
                .highMatchCount(highMatchCount)
                .verifiedSponsorCount(verifiedSponsorCount)
                .autopilotPaused(pref.isAutopilotPaused())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedAutopilotHistoryResponse getRunHistory(
            AuthenticatedUserPrincipal currentUser,
            int page,
            int size
    ) {
        User user = requireAuthenticatedUser(currentUser);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, size > 0 ? size : DEFAULT_PAGE_SIZE);
        Page<AutopilotRunLog> logPage = runLogRepository.findAllByUserIdOrderByStartedAtDescIdDesc(
                user.getId(),
                PageRequest.of(safePage, safeSize)
        );
        List<AutopilotRunLogDto> dtos = logPage.getContent().stream().map(this::toRunLogDto).toList();

        return PagedAutopilotHistoryResponse.builder()
                .content(dtos)
                .pageNumber(logPage.getNumber())
                .pageSize(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .first(logPage.isFirst())
                .last(logPage.isLast())
                .hasNext(logPage.hasNext())
                .hasPrevious(logPage.hasPrevious())
                .build();
    }

    private AutopilotRunLogDto executeScanInternal(
            AuthenticatedUserPrincipal principal,
            User user,
            AutopilotTriggerType triggerType,
            RunAutopilotRequest request
    ) {
        Instant startedAt = Instant.now();
        UserJobPreference pref = getOrCreatePersistentPreference(user);

        if (triggerType == AutopilotTriggerType.SCHEDULED && !pref.isAutopilotEnabled()) {
            AutopilotRunLog skipped = runLogRepository.saveAndFlush(AutopilotRunLog.builder()
                    .user(user)
                    .triggerType(triggerType)
                    .status(AutopilotRunStatus.SKIPPED_DISABLED)
                    .summaryMessage("Scheduled autopilot skipped because autopilot is disabled in preferences.")
                    .startedAt(startedAt)
                    .completedAt(Instant.now())
                    .build());
            return toRunLogDto(skipped);
        }

        if (triggerType == AutopilotTriggerType.SCHEDULED && pref.isAutopilotPaused()) {
            AutopilotRunLog skipped = runLogRepository.saveAndFlush(AutopilotRunLog.builder()
                    .user(user)
                    .triggerType(triggerType)
                    .status(AutopilotRunStatus.SKIPPED_PAUSED)
                    .summaryMessage("Scheduled autopilot skipped because user paused autopilot.")
                    .startedAt(startedAt)
                    .completedAt(Instant.now())
                    .build());
            return toRunLogDto(skipped);
        }

        boolean ingestionTriggered = false;
        try {
            if (request.isRefreshExternalJobs() && canTriggerExternalIngestion(pref, startedAt)) {
                IngestionQuery query = buildIngestionQuery(pref);
                ingestionTriggered = jobIngestionService.tryScheduledIngestAll(query).isPresent();
            }

            BatchMatchRecalculationResponse matchBatch = jobMatchingService.recalculateActiveJobs(principal);
            List<RecommendedJobItemDto> recommendations = buildRecommendedItems(
                    user.getId(),
                    pref,
                    resolveMinMatchScore(pref),
                    false,
                    true
            );

            int aiAnalyzed = 0;
            if (request.isAnalyzeTopMatchesWithAi() && !recommendations.isEmpty()) {
                int maxAi = Math.max(0, autopilotProperties.getMaxAiAnalysesPerRun());
                for (RecommendedJobItemDto rec : recommendations) {
                    if (aiAnalyzed >= maxAi) {
                        break;
                    }
                    try {
                        semanticJobMatchingService.evaluateSemanticMatch(principal, rec.getJob().getId(), false);
                        aiAnalyzed++;
                    } catch (AiServiceUnavailableException | AiProviderException ex) {
                        log.debug("Autopilot optional AI match evaluation skipped/unavailable for jobId={}: {}",
                                rec.getJob().getId(), ex.getMessage());
                        break;
                    }
                }
            }

            int notificationsCreated = 0;
            int maxAlerts = Math.max(1, autopilotProperties.getMaxNotificationsPerRun());
            List<String> digestHighlights = new ArrayList<>();

            for (RecommendedJobItemDto rec : recommendations) {
                if (notificationsCreated >= maxAlerts) {
                    break;
                }
                JobSummaryDto job = rec.getJob();
                boolean created;
                if (pref.isRequiresSponsorship() && rec.isVerifiedSponsor()) {
                    created = notificationService.createNotificationIfNotDuplicate(
                            user,
                            NotificationType.SPONSORSHIP_VERIFIED,
                            "Verified Sponsor Match (" + job.getMatchPercentage() + "%): " + job.getTitle(),
                            job.getCompanyName() + " in " + safeJoin(job.getCity(), job.getCountry())
                                    + " matches your profile and has verified sponsor evidence.",
                            NotificationReferenceType.JOB,
                            job.getId(),
                            NOTIFICATION_DEDUPE_WINDOW
                    );
                } else {
                    created = notificationService.createNotificationIfNotDuplicate(
                            user,
                            NotificationType.HIGH_MATCH_JOB,
                            "High Match (" + job.getMatchPercentage() + "%): " + job.getTitle(),
                            job.getCompanyName() + " · " + safeJoin(job.getCity(), job.getCountry())
                                    + " meets your minimum match threshold (" + resolveMinMatchScore(pref) + "%).",
                            NotificationReferenceType.JOB,
                            job.getId(),
                            NOTIFICATION_DEDUPE_WINDOW
                    );
                }
                if (created) {
                    notificationsCreated++;
                    digestHighlights.add(job.getTitle() + " @ " + job.getCompanyName() + " (" + job.getMatchPercentage() + "%)");
                }
            }

            notificationsCreated += createPipelineReminderNotifications(user, startedAt, maxAlerts - notificationsCreated);

            boolean emailDispatched = emailNotifier.dispatchDigestIfEnabled(
                    user,
                    pref,
                    digestHighlights,
                    notificationsCreated
            );

            Instant completedAt = Instant.now();
            pref.setLastAutopilotRunAt(completedAt);
            preferenceRepository.saveAndFlush(pref);

            String summary = String.format(
                    "Evaluated %d active job(s); %d meet >=%d%% threshold; %d new notification(s)%s%s.",
                    matchBatch.getEvaluatedJobsCount(),
                    recommendations.size(),
                    resolveMinMatchScore(pref),
                    notificationsCreated,
                    aiAnalyzed > 0 ? "; AI-reviewed top " + aiAnalyzed : "",
                    emailDispatched ? "; email digest queued" : ""
            );
            if (summary.length() > 495) {
                summary = summary.substring(0, 492) + "...";
            }

            AutopilotRunLog saved = runLogRepository.saveAndFlush(AutopilotRunLog.builder()
                    .user(user)
                    .triggerType(triggerType)
                    .status(AutopilotRunStatus.COMPLETED)
                    .ingestionTriggered(ingestionTriggered)
                    .jobsEvaluated(matchBatch.getEvaluatedJobsCount())
                    .newMatchesFound(matchBatch.getHighMatchJobsCount())
                    .recommendedJobsCount(recommendations.size())
                    .notificationsCreated(notificationsCreated)
                    .emailDispatched(emailDispatched)
                    .summaryMessage(summary)
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .build());

            log.info("Completed autopilot run id={} userUuid={} trigger={} recommendations={} alerts={}",
                    saved.getId(), user.getUuid(), triggerType, recommendations.size(), notificationsCreated);
            return toRunLogDto(saved);
        } catch (RuntimeException ex) {
            log.error("Autopilot run failed userUuid={} trigger={}: {}", user.getUuid(), triggerType, ex.getMessage(), ex);
            AutopilotRunLog failed = runLogRepository.saveAndFlush(AutopilotRunLog.builder()
                    .user(user)
                    .triggerType(triggerType)
                    .status(AutopilotRunStatus.FAILED)
                    .ingestionTriggered(ingestionTriggered)
                    .summaryMessage("Autopilot scan encountered an unexpected error.")
                    .errorMessage(truncate(ex.getMessage(), 1000))
                    .startedAt(startedAt)
                    .completedAt(Instant.now())
                    .build());
            return toRunLogDto(failed);
        }
    }

    private int createPipelineReminderNotifications(User user, Instant now, int remainingBudget) {
        if (remainingBudget <= 0) {
            return 0;
        }
        int created = 0;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        List<JobApplication> followUps = applicationRepository.findAllWithFollowUpReminderByUserId(user.getId());
        for (JobApplication app : followUps) {
            if (created >= remainingBudget) {
                break;
            }
            if (app.getStatus() == ApplicationStatus.REJECTED || app.getStatus() == ApplicationStatus.WITHDRAWN) {
                continue;
            }
            if (app.getNextFollowUpDate() != null && !app.getNextFollowUpDate().isAfter(tomorrow)) {
                boolean added = notificationService.createNotificationIfNotDuplicate(
                        user,
                        NotificationType.APPLICATION_FOLLOW_UP,
                        "Follow-up due: " + app.getJob().getTitle(),
                        "Follow-up scheduled for " + app.getNextFollowUpDate() + " with " + app.getJob().getCompanyName() + ".",
                        NotificationReferenceType.JOB_APPLICATION,
                        app.getId(),
                        NOTIFICATION_DEDUPE_WINDOW
                );
                if (added) {
                    created++;
                }
            }
            if (created < remainingBudget && app.getNextInterviewDate() != null
                    && app.getNextInterviewDate().isAfter(now.minus(2, ChronoUnit.HOURS))
                    && app.getNextInterviewDate().isBefore(now.plus(48, ChronoUnit.HOURS))) {
                boolean added = notificationService.createNotificationIfNotDuplicate(
                        user,
                        NotificationType.INTERVIEW_REMINDER,
                        "Upcoming interview: " + app.getJob().getTitle(),
                        "Interview scheduled at " + app.getJob().getCompanyName() + " (" + app.getNextInterviewDate() + ").",
                        NotificationReferenceType.JOB_APPLICATION,
                        app.getId(),
                        NOTIFICATION_DEDUPE_WINDOW
                );
                if (added) {
                    created++;
                }
            }
        }
        return created;
    }

    private boolean canTriggerExternalIngestion(UserJobPreference pref, Instant now) {
        if (!ingestionProperties.isEnabled() || jobIngestionService.isRunning()) {
            return false;
        }
        Instant lastRun = pref.getLastAutopilotRunAt();
        if (lastRun == null) {
            return true;
        }
        long cooldownSec = Math.max(0L, autopilotProperties.getManualRefreshCooldownSeconds());
        return Duration.between(lastRun, now).getSeconds() >= cooldownSec;
    }

    private IngestionQuery buildIngestionQuery(UserJobPreference pref) {
        List<String> countries = pref.getPreferredCountries() != null && !pref.getPreferredCountries().isEmpty()
                ? pref.getPreferredCountries()
                : ingestionProperties.getTargetCountries();
        List<String> keywords = pref.getPreferredJobTitles() != null && !pref.getPreferredJobTitles().isEmpty()
                ? pref.getPreferredJobTitles()
                : List.of("Java", "Angular", "Backend", "Full Stack", "Software Engineer");
        return IngestionQuery.builder()
                .countries(countries)
                .keywords(keywords)
                .maxPages(ingestionProperties.getDefaultMaxPages())
                .pageSize(50)
                .remoteOnly(false)
                .build();
    }

    private List<RecommendedJobItemDto> buildRecommendedItems(
            Long userId,
            UserJobPreference pref,
            Integer minScoreOverride,
            boolean verifiedSponsorsOnly,
            boolean excludeApplied
    ) {
        Instant now = Instant.now();
        int threshold = minScoreOverride != null ? minScoreOverride : resolveMinMatchScore(pref);

        List<Job> activeJobs = jobRepository.findAllByActiveTrue().stream()
                .filter(job -> job.getExpiresAt() == null || !job.getExpiresAt().isBefore(now))
                .toList();
        if (activeJobs.isEmpty()) {
            return List.of();
        }

        List<Long> jobIds = activeJobs.stream().map(Job::getId).toList();
        Map<Long, JobMatchScore> matchByJobId = jobMatchScoreRepository.findAllByUserIdAndJobIdIn(userId, jobIds)
                .stream()
                .collect(Collectors.toMap(m -> m.getJob().getId(), Function.identity(), (a, b) -> a));

        Map<Long, UserJobInteraction> interactionByJobId = interactionRepository.findAllByUserIdAndJobIdIn(userId, jobIds)
                .stream()
                .collect(Collectors.toMap(i -> i.getJob().getId(), Function.identity(), (a, b) -> a));

        Map<Long, JobAiAnalysis> aiByJobId = jobAiAnalysisRepository.findAllByJobIdIn(jobIds)
                .stream()
                .collect(Collectors.toMap(a -> a.getJob().getId(), Function.identity(), (a, b) -> a));

        Map<Long, JobApplication> applicationByJobId = applicationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .collect(Collectors.toMap(a -> a.getJob().getId(), Function.identity(), (a, b) -> a));

        Map<String, Optional<Company>> companyCache = new HashMap<>();

        List<RecommendedJobItemDto> items = new ArrayList<>();
        for (Job job : activeJobs) {
            UserJobInteraction interaction = interactionByJobId.get(job.getId());
            if (interaction != null && interaction.isHidden()) {
                continue;
            }
            if (pref.isRequiresSponsorship() && job.getSponsorStatus() == JobSponsorStatus.NO_SPONSORSHIP) {
                continue;
            }

            JobMatchScore matchScore = matchByJobId.get(job.getId());
            if (matchScore == null || matchScore.getFinalScore() == null) {
                continue;
            }
            int matchPct = Math.max(0, Math.min(100, matchScore.getFinalScore().setScale(0, RoundingMode.HALF_UP).intValue()));
            boolean meetsMin = matchPct >= threshold;
            if (!meetsMin) {
                continue;
            }

            Company resolvedCompany = resolveCompanyCached(job, companyCache);
            boolean verifiedSponsor = job.getSponsorStatus() == JobSponsorStatus.VERIFIED_SPONSOR
                    || (resolvedCompany != null && resolvedCompany.isVerifiedSponsor()
                    && job.getSponsorStatus() != JobSponsorStatus.NO_SPONSORSHIP);
            if (verifiedSponsorsOnly && !verifiedSponsor) {
                continue;
            }

            JobApplication app = applicationByJobId.get(job.getId());
            boolean alreadyApplied = (interaction != null && interaction.isHasApplied())
                    || (app != null && app.getStatus() != ApplicationStatus.SAVED
                    && app.getStatus() != ApplicationStatus.INTERESTED);
            if (excludeApplied && alreadyApplied) {
                continue;
            }

            boolean matchesCountry = matchesPreferredCountry(job.getCountry(), pref.getPreferredCountries());
            boolean matchesTitle = matchesPreferredTitle(job.getTitle(), pref.getPreferredJobTitles());
            JobAiAnalysis ai = aiByJobId.get(job.getId());

            items.add(RecommendedJobItemDto.builder()
                    .job(toJobSummaryDto(job, interaction, matchPct))
                    .matchingSkills(safeCopy(matchScore.getMatchingSkills()))
                    .missingSkills(safeCopy(matchScore.getMissingSkills()))
                    .recommendationReasons(safeCopy(matchScore.getRecommendationReasons()))
                    .experienceCompatibility(matchScore.getExperienceCompatibility())
                    .meetsMinMatchScore(true)
                    .matchesPreferredCountry(matchesCountry)
                    .matchesPreferredTitle(matchesTitle)
                    .verifiedSponsor(verifiedSponsor)
                    .sponsorshipEvidenceSummary(resolveSponsorshipSummary(job, resolvedCompany, verifiedSponsor))
                    .aiAnalysisSummary(ai != null ? ai.getSummary() : null)
                    .alreadyApplied(alreadyApplied)
                    .build());
        }

        items.sort(Comparator
                .comparing((RecommendedJobItemDto r) -> r.getJob().getMatchPercentage(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RecommendedJobItemDto::isVerifiedSponsor, Comparator.reverseOrder())
                .thenComparing(RecommendedJobItemDto::isMatchesPreferredCountry, Comparator.reverseOrder())
                .thenComparing(RecommendedJobItemDto::isMatchesPreferredTitle, Comparator.reverseOrder())
                .thenComparing(r -> r.getJob().getPostedAt(), Comparator.nullsLast(Comparator.reverseOrder())));

        return items;
    }

    private Company resolveCompanyCached(Job job, Map<String, Optional<Company>> cache) {
        if (job.getCompany() != null) {
            return job.getCompany();
        }
        String raw = job.getCompanyName();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return cache.computeIfAbsent(key, ignored -> {
            String canonical = canonicalHashGenerator.normalizeCompanyKey(raw);
            if (!canonical.isBlank()) {
                Optional<Company> match = companyRepository.findByNormalizedName(canonical);
                if (match.isPresent()) {
                    return match;
                }
            }
            return companyRepository.findByNormalizedName(key);
        }).orElse(null);
    }

    private boolean matchesPreferredCountry(String jobCountry, List<String> preferredCountries) {
        if (preferredCountries == null || preferredCountries.isEmpty() || jobCountry == null) {
            return false;
        }
        String lower = jobCountry.trim().toLowerCase(Locale.ROOT);
        return preferredCountries.stream().anyMatch(c -> c != null && lower.contains(c.trim().toLowerCase(Locale.ROOT)));
    }

    private boolean matchesPreferredTitle(String jobTitle, List<String> preferredTitles) {
        if (preferredTitles == null || preferredTitles.isEmpty() || jobTitle == null) {
            return false;
        }
        String lower = jobTitle.trim().toLowerCase(Locale.ROOT);
        return preferredTitles.stream().anyMatch(t -> t != null && !t.isBlank() && lower.contains(t.trim().toLowerCase(Locale.ROOT)));
    }

    private String resolveSponsorshipSummary(Job job, Company company, boolean verifiedSponsor) {
        if (verifiedSponsor) {
            return company != null
                    ? "Verified sponsor (" + company.getName() + ") · verify specific vacancy eligibility"
                    : "Verified sponsor registry evidence · verify vacancy eligibility";
        }
        if (job.getSponsorStatus() == JobSponsorStatus.SPONSORSHIP_MENTIONED) {
            return "Posting text mentions visa sponsorship support";
        }
        if (job.getSponsorStatus() == JobSponsorStatus.RELOCATION_OFFERED) {
            return "Posting mentions relocation support";
        }
        return "Sponsorship unconfirmed in posting or sponsor registers";
    }

    private JobSummaryDto toJobSummaryDto(Job job, UserJobInteraction interaction, Integer matchPercentage) {
        return JobSummaryDto.builder()
                .id(job.getId())
                .title(job.getTitle())
                .companyName(job.getCompanyName())
                .location(job.getLocation())
                .city(job.getCity())
                .country(job.getCountry())
                .workArrangement(job.getWorkArrangement())
                .employmentType(job.getEmploymentType())
                .experienceLevel(job.getExperienceLevel())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .salaryCurrency(job.getSalaryCurrency())
                .salaryPeriod(job.getSalaryPeriod())
                .sponsorStatus(job.getSponsorStatus())
                .matchPercentage(matchPercentage)
                .sourceProvider(job.getSourceProvider())
                .postedAt(job.getPostedAt())
                .saved(interaction != null && interaction.isSaved())
                .interested(interaction != null && interaction.isInterested())
                .hidden(interaction != null && interaction.isHidden())
                .skills(safeCopy(job.getSkills()))
                .descriptionSnippet(resolveSnippet(job))
                .originalUrl(job.getOriginalUrl())
                .build();
    }

    private String resolveSnippet(Job job) {
        if (job.getDescriptionSnippet() != null && !job.getDescriptionSnippet().isBlank()) {
            return job.getDescriptionSnippet();
        }
        String description = job.getDescription();
        if (description == null || description.isBlank()) {
            return null;
        }
        String clean = description.trim().replaceAll("\\s+", " ");
        return clean.length() <= SNIPPET_MAX_LENGTH ? clean : clean.substring(0, SNIPPET_MAX_LENGTH - 3).trim() + "...";
    }

    private AutopilotRunLogDto toRunLogDto(AutopilotRunLog log) {
        return AutopilotRunLogDto.builder()
                .id(log.getId())
                .triggerType(log.getTriggerType())
                .status(log.getStatus())
                .ingestionTriggered(log.isIngestionTriggered())
                .jobsEvaluated(log.getJobsEvaluated())
                .newMatchesFound(log.getNewMatchesFound())
                .recommendedJobsCount(log.getRecommendedJobsCount())
                .notificationsCreated(log.getNotificationsCreated())
                .emailDispatched(log.isEmailDispatched())
                .summaryMessage(log.getSummaryMessage())
                .errorMessage(log.getErrorMessage())
                .startedAt(log.getStartedAt())
                .completedAt(log.getCompletedAt())
                .build();
    }

    private User requireAuthenticatedUser(AuthenticatedUserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
    }

    private UserJobPreference getOrCreatePersistentPreference(User user) {
        return preferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> preferenceRepository.saveAndFlush(defaultPreference(user)));
    }

    private UserJobPreference defaultPreference(User user) {
        return UserJobPreference.builder()
                .user(user)
                .minMatchScore(DEFAULT_MIN_MATCH_SCORE)
                .autopilotEnabled(false)
                .autopilotPaused(false)
                .emailNotificationsEnabled(false)
                .requiresSponsorship(true)
                .requiresRelocation(true)
                .englishSpeakingOnly(true)
                .salaryCurrency("EUR")
                .build();
    }

    private int resolveMinMatchScore(UserJobPreference pref) {
        return pref != null && pref.getMinMatchScore() != null ? pref.getMinMatchScore() : DEFAULT_MIN_MATCH_SCORE;
    }

    private List<String> safeCopy(List<String> list) {
        return list == null ? List.of() : List.copyOf(list);
    }

    private String safeJoin(String a, String b) {
        if (a != null && !a.isBlank() && b != null && !b.isBlank()) {
            return a.trim() + ", " + b.trim();
        }
        return (a != null && !a.isBlank()) ? a.trim() : (b != null ? b.trim() : "Europe");
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}

