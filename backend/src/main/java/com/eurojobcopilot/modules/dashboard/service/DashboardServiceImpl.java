package com.eurojobcopilot.modules.dashboard.service;

import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.dashboard.dto.DashboardActivityItemDto;
import com.eurojobcopilot.modules.dashboard.dto.DashboardReadinessDto;
import com.eurojobcopilot.modules.dashboard.dto.DashboardSummaryResponse;
import com.eurojobcopilot.modules.dashboard.dto.PipelineStageCountDto;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.matching.service.JobMatchingService;
import com.eurojobcopilot.modules.notification.entity.Notification;
import com.eurojobcopilot.modules.notification.repository.NotificationRepository;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int DEFAULT_MIN_MATCH_SCORE = 60;
    private static final int RECENT_ACTIVITY_LIMIT = 8;

    private static final List<JobSponsorStatus> SPONSORSHIP_OPPORTUNITY_STATUSES = List.of(
            JobSponsorStatus.VERIFIED_SPONSOR,
            JobSponsorStatus.SPONSORSHIP_MENTIONED,
            JobSponsorStatus.RELOCATION_OFFERED
    );

    private static final List<ApplicationStatus> TERMINAL_APPLICATION_STATUSES = List.of(
            ApplicationStatus.REJECTED,
            ApplicationStatus.WITHDRAWN
    );

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final UserJobInteractionRepository userJobInteractionRepository;
    private final JobMatchScoreRepository jobMatchScoreRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final NotificationRepository notificationRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserWorkExperienceRepository userWorkExperienceRepository;
    private final UserEducationRepository userEducationRepository;
    private final ResumeRepository resumeRepository;
    private final UserJobPreferenceRepository userJobPreferenceRepository;
    private final JobMatchingService jobMatchingService;

    @Override
    @Transactional
    public DashboardSummaryResponse getSummary(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        Long userId = user.getId();

        jobMatchingService.ensureActiveMatchesUpToDate(userId);

        Instant now = Instant.now();
        Instant utcDayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);

        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        UserJobPreference preference = userJobPreferenceRepository.findByUserId(userId).orElse(null);

        int effectiveMinMatchScore = (preference != null && preference.getMinMatchScore() != null)
                ? preference.getMinMatchScore()
                : DEFAULT_MIN_MATCH_SCORE;

        long totalJobsDiscovered = jobRepository.countByActiveTrue();
        long newJobsToday = jobRepository.countByActiveTrueAndPostedAtGreaterThanEqual(utcDayStart);
        long highMatchJobsCount = jobMatchScoreRepository.countActiveHighMatchJobs(
                userId, BigDecimal.valueOf(effectiveMinMatchScore)
        );
        long sponsorshipOpportunitiesCount = jobRepository.countByActiveTrueAndSponsorStatusIn(
                SPONSORSHIP_OPPORTUNITY_STATUSES
        );
        long verifiedSponsorsCount = companyRepository.countByVerifiedSponsorTrue();

        long savedJobsCount = userJobInteractionRepository.countByUserIdAndSavedTrue(userId);
        long interestedJobsCount = userJobInteractionRepository.countByUserIdAndInterestedTrue(userId);
        long activeApplicationsCount = jobApplicationRepository.countByUserIdAndStatusNotIn(
                userId, TERMINAL_APPLICATION_STATUSES
        );
        long unreadNotificationsCount = notificationRepository.countByUserIdAndReadFalse(userId);

        long skillsCount = userSkillRepository.countByUserId(userId);
        long experiencesCount = userWorkExperienceRepository.countByUserId(userId);
        long educationsCount = userEducationRepository.countByUserId(userId);
        long resumesCount = resumeRepository.countByUserId(userId);
        boolean masterResumeApproved = resumeRepository.existsByUserIdAndMasterApprovedTrue(userId);

        int profileCompletenessPercent = computeProfileCompleteness(
                profile, skillsCount, experiencesCount, educationsCount
        );

        DashboardReadinessDto readiness = DashboardReadinessDto.builder()
                .profileExists(profile != null)
                .profileCompletenessPercent(profileCompletenessPercent)
                .skillsCount(skillsCount)
                .experiencesCount(experiencesCount)
                .educationsCount(educationsCount)
                .resumesCount(resumesCount)
                .masterResumeApproved(masterResumeApproved)
                .preferencesConfigured(preference != null)
                .autopilotEnabled(preference != null && preference.isAutopilotEnabled())
                .minMatchScore(effectiveMinMatchScore)
                .preferredCountries(safeCopy(preference != null ? preference.getPreferredCountries() : null))
                .preferredJobTitles(safeCopy(preference != null ? preference.getPreferredJobTitles() : null))
                .build();

        List<PipelineStageCountDto> pipelineBreakdown = buildPipelineBreakdown(userId);
        List<DashboardActivityItemDto> recentActivity = buildRecentActivity(userId, now);

        log.debug("Computed dashboard summary for userUuid={} activeApps={} savedJobs={}",
                user.getUuid(), activeApplicationsCount, savedJobsCount);

        return DashboardSummaryResponse.builder()
                .totalJobsDiscovered(totalJobsDiscovered)
                .newJobsToday(newJobsToday)
                .highMatchJobsCount(highMatchJobsCount)
                .sponsorshipOpportunitiesCount(sponsorshipOpportunitiesCount)
                .verifiedSponsorsCount(verifiedSponsorsCount)
                .savedJobsCount(savedJobsCount)
                .interestedJobsCount(interestedJobsCount)
                .activeApplicationsCount(activeApplicationsCount)
                .unreadNotificationsCount(unreadNotificationsCount)
                .readiness(readiness)
                .pipelineBreakdown(pipelineBreakdown)
                .recentActivity(recentActivity)
                .generatedAt(now)
                .build();
    }

    private User requireAuthenticatedUser(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
    }

    private int computeProfileCompleteness(
            UserProfile profile,
            long skillsCount,
            long experiencesCount,
            long educationsCount
    ) {
        int score = 0;
        if (profile != null && hasText(profile.getCurrentDesignation()) && hasText(profile.getCurrentCountry())) {
            score += 25;
        }
        if (profile != null && (hasText(profile.getBio())
                || hasText(profile.getLinkedinUrl())
                || hasText(profile.getGithubUrl()))) {
            score += 15;
        }
        if (skillsCount > 0) {
            score += 20;
        }
        if (experiencesCount > 0) {
            score += 20;
        }
        if (educationsCount > 0) {
            score += 20;
        }
        return score;
    }

    private List<PipelineStageCountDto> buildPipelineBreakdown(Long userId) {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        List<Object[]> rows = jobApplicationRepository.countGroupedByStatusForUser(userId);
        if (rows != null) {
            for (Object[] row : rows) {
                if (row != null && row.length >= 2 && row[0] instanceof ApplicationStatus status
                        && row[1] instanceof Number number) {
                    counts.put(status, number.longValue());
                }
            }
        }

        List<PipelineStageCountDto> breakdown = new ArrayList<>(ApplicationStatus.values().length);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            breakdown.add(PipelineStageCountDto.builder()
                    .status(status.name())
                    .label(formatStatusLabel(status))
                    .count(counts.getOrDefault(status, 0L))
                    .build());
        }
        return List.copyOf(breakdown);
    }

    private List<DashboardActivityItemDto> buildRecentActivity(Long userId, Instant fallbackTimestamp) {
        List<DashboardActivityItemDto> items = new ArrayList<>();

        List<JobApplication> recentApps = jobApplicationRepository.findRecentWithJob(userId, PageRequest.of(0, 5));
        if (recentApps != null) {
            for (JobApplication app : recentApps) {
                String jobTitle = app.getJob() != null ? app.getJob().getTitle() : "Tracked Position";
                String companyName = app.getJob() != null ? app.getJob().getCompanyName() : "Employer";
                Instant ts = firstNonNull(app.getUpdatedAt(), app.getAppliedDate(), app.getCreatedAt(), fallbackTimestamp);
                items.add(DashboardActivityItemDto.builder()
                        .type("APPLICATION")
                        .title(jobTitle + " · " + companyName)
                        .subtitle("Pipeline stage: " + formatStatusLabel(app.getStatus()))
                        .timestamp(ts)
                        .targetRoute("/applications")
                        .build());
            }
        }

        List<UserJobInteraction> recentSaved = userJobInteractionRepository.findRecentSavedWithJob(
                userId, PageRequest.of(0, 5)
        );
        if (recentSaved != null) {
            for (UserJobInteraction saved : recentSaved) {
                String jobTitle = saved.getJob() != null ? saved.getJob().getTitle() : "Saved Job";
                String companyName = saved.getJob() != null ? saved.getJob().getCompanyName() : "Employer";
                Instant ts = firstNonNull(saved.getSavedAt(), saved.getUpdatedAt(), saved.getCreatedAt(), fallbackTimestamp);
                items.add(DashboardActivityItemDto.builder()
                        .type("SAVED_JOB")
                        .title(jobTitle + " · " + companyName)
                        .subtitle("Saved for targeted review")
                        .timestamp(ts)
                        .targetRoute("/jobs")
                        .build());
            }
        }

        List<Resume> recentResumes = resumeRepository.findTop3ByUserIdOrderByUpdatedAtDesc(userId);
        if (recentResumes != null) {
            for (Resume resume : recentResumes) {
                Instant ts = firstNonNull(resume.getUpdatedAt(), resume.getCreatedAt(), fallbackTimestamp);
                String approvalText = resume.isMasterApproved() ? "Approved Master CV" : "Uploaded Draft";
                items.add(DashboardActivityItemDto.builder()
                        .type("RESUME")
                        .title(resume.getTitle())
                        .subtitle(approvalText + " · v" + resume.getVersionNumber())
                        .timestamp(ts)
                        .targetRoute("/resumes")
                        .build());
            }
        }

        var notificationsPage = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, 5)
        );
        if (notificationsPage != null) {
            for (Notification note : notificationsPage.getContent()) {
                Instant ts = firstNonNull(note.getCreatedAt(), fallbackTimestamp);
                items.add(DashboardActivityItemDto.builder()
                        .type("NOTIFICATION")
                        .title(note.getTitle())
                        .subtitle(note.isRead() ? "Read alert" : "Unread notification")
                        .timestamp(ts)
                        .targetRoute("/notifications")
                        .build());
            }
        }

        return items.stream()
                .sorted(Comparator.comparing(DashboardActivityItemDto::getTimestamp, Comparator.reverseOrder()))
                .limit(RECENT_ACTIVITY_LIMIT)
                .toList();
    }

    private String formatStatusLabel(ApplicationStatus status) {
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

    private Instant firstNonNull(Instant... candidates) {
        for (Instant candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return Instant.now();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> safeCopy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}

