package com.eurojobcopilot.modules.dashboard.service;

import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.dashboard.dto.DashboardSummaryResponse;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.matching.service.JobMatchingService;
import com.eurojobcopilot.modules.notification.entity.Notification;
import com.eurojobcopilot.modules.notification.entity.NotificationType;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private UserJobInteractionRepository userJobInteractionRepository;
    @Mock
    private JobMatchScoreRepository jobMatchScoreRepository;
    @Mock
    private JobApplicationRepository jobApplicationRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserSkillRepository userSkillRepository;
    @Mock
    private UserWorkExperienceRepository userWorkExperienceRepository;
    @Mock
    private UserEducationRepository userEducationRepository;
    @Mock
    private ResumeRepository resumeRepository;
    @Mock
    private UserJobPreferenceRepository userJobPreferenceRepository;
    @Mock
    private JobMatchingService jobMatchingService;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Test
    void getSummary_newUserWithDefaults_returnsZeroReadinessAndOrderedPipelineStages() {
        User user = User.builder()
                .id(10L)
                .uuid("user-10")
                .email("new@eurojob.eu")
                .firstName("Lukas")
                .lastName("Meyer")
                .passwordHash("hash")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        AuthenticatedUserPrincipal principal = AuthenticatedUserPrincipal.from(user);

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.empty());
        when(userJobPreferenceRepository.findByUserId(10L)).thenReturn(Optional.empty());
        when(jobRepository.countByActiveTrue()).thenReturn(15L);
        when(jobRepository.countByActiveTrueAndPostedAtGreaterThanEqual(any(Instant.class))).thenReturn(3L);
        when(jobRepository.countByActiveTrueAndSponsorStatusIn(anyCollection())).thenReturn(8L);
        when(companyRepository.countByVerifiedSponsorTrue()).thenReturn(4L);
        when(jobMatchScoreRepository.countActiveHighMatchJobs(eq(10L), eq(BigDecimal.valueOf(60)))).thenReturn(0L);
        when(jobApplicationRepository.countGroupedByStatusForUser(10L)).thenReturn(List.of());
        when(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        DashboardSummaryResponse summary = dashboardService.getSummary(principal);

        assertThat(summary.getTotalJobsDiscovered()).isEqualTo(15L);
        assertThat(summary.getNewJobsToday()).isEqualTo(3L);
        assertThat(summary.getSponsorshipOpportunitiesCount()).isEqualTo(8L);
        assertThat(summary.getVerifiedSponsorsCount()).isEqualTo(4L);
        assertThat(summary.getReadiness().isProfileExists()).isFalse();
        assertThat(summary.getReadiness().getProfileCompletenessPercent()).isZero();
        assertThat(summary.getReadiness().getMinMatchScore()).isEqualTo(60);
        assertThat(summary.getPipelineBreakdown()).hasSize(ApplicationStatus.values().length);
        assertThat(summary.getRecentActivity()).isEmpty();
        verify(jobMatchScoreRepository).countActiveHighMatchJobs(10L, BigDecimal.valueOf(60));
    }

    @Test
    void getSummary_configuredUser_computesReadinessPipelineAndChronologicalActivity() {
        User user = User.builder()
                .id(42L)
                .uuid("user-42")
                .email("alex@eurojob.eu")
                .firstName("Alex")
                .lastName("Mueller")
                .passwordHash("hash")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        AuthenticatedUserPrincipal principal = AuthenticatedUserPrincipal.from(user);

        UserProfile profile = UserProfile.builder()
                .currentDesignation("Senior Java Engineer")
                .currentCountry("India")
                .linkedinUrl("https://linkedin.com/in/alex")
                .build();
        UserJobPreference prefs = UserJobPreference.builder()
                .minMatchScore(80)
                .autopilotEnabled(true)
                .preferredCountries(List.of("Germany", "Netherlands"))
                .preferredJobTitles(List.of("Senior Java Engineer"))
                .build();

        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(42L)).thenReturn(Optional.of(profile));
        when(userJobPreferenceRepository.findByUserId(42L)).thenReturn(Optional.of(prefs));
        when(userSkillRepository.countByUserId(42L)).thenReturn(5L);
        when(userWorkExperienceRepository.countByUserId(42L)).thenReturn(2L);
        when(userEducationRepository.countByUserId(42L)).thenReturn(1L);
        when(resumeRepository.countByUserId(42L)).thenReturn(1L);
        when(resumeRepository.existsByUserIdAndMasterApprovedTrue(42L)).thenReturn(true);
        when(jobRepository.countByActiveTrue()).thenReturn(120L);
        when(jobRepository.countByActiveTrueAndPostedAtGreaterThanEqual(any(Instant.class))).thenReturn(11L);
        when(jobRepository.countByActiveTrueAndSponsorStatusIn(anyCollection())).thenReturn(45L);
        when(companyRepository.countByVerifiedSponsorTrue()).thenReturn(19L);
        when(jobMatchScoreRepository.countActiveHighMatchJobs(eq(42L), eq(BigDecimal.valueOf(80)))).thenReturn(9L);
        when(userJobInteractionRepository.countByUserIdAndSavedTrue(42L)).thenReturn(4L);
        when(userJobInteractionRepository.countByUserIdAndInterestedTrue(42L)).thenReturn(6L);
        when(jobApplicationRepository.countByUserIdAndStatusNotIn(eq(42L), anyCollection())).thenReturn(3L);
        when(notificationRepository.countByUserIdAndReadFalse(42L)).thenReturn(2L);

        when(jobApplicationRepository.countGroupedByStatusForUser(42L)).thenReturn(List.of(
                new Object[]{ApplicationStatus.APPLIED, 2L},
                new Object[]{ApplicationStatus.TECH_INTERVIEW, 1L}
        ));

        Job bookingJob = Job.builder().title("Staff Java Engineer").companyName("Booking.com").country("Netherlands").build();
        JobApplication latestApp = JobApplication.builder()
                .job(bookingJob)
                .status(ApplicationStatus.TECH_INTERVIEW)
                .appliedDate(Instant.parse("2026-11-08T10:00:00Z"))
                .build();
        latestApp.setUpdatedAt(Instant.parse("2026-11-09T08:00:00Z"));

        Job adyenJob = Job.builder().title("Backend Engineer").companyName("Adyen").country("Netherlands").build();
        UserJobInteraction latestSaved = UserJobInteraction.builder()
                .job(adyenJob)
                .saved(true)
                .savedAt(Instant.parse("2026-11-09T07:00:00Z"))
                .build();

        Resume latestResume = Resume.builder().title("Master Java CV").versionNumber(2).masterApproved(true).build();
        latestResume.setUpdatedAt(Instant.parse("2026-11-08T18:00:00Z"));

        Notification latestNote = Notification.builder()
                .type(NotificationType.HIGH_MATCH_JOB)
                .title("92% match found in Amsterdam")
                .message("Adyen Backend Engineer")
                .read(false)
                .build();
        latestNote.setCreatedAt(Instant.parse("2026-11-09T08:30:00Z"));

        when(jobApplicationRepository.findRecentWithJob(eq(42L), any(Pageable.class))).thenReturn(List.of(latestApp));
        when(userJobInteractionRepository.findRecentSavedWithJob(eq(42L), any(Pageable.class)))
                .thenReturn(List.of(latestSaved));
        when(resumeRepository.findTop3ByUserIdOrderByUpdatedAtDesc(42L)).thenReturn(List.of(latestResume));
        when(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(eq(42L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(latestNote)));

        DashboardSummaryResponse summary = dashboardService.getSummary(principal);

        assertThat(summary.getHighMatchJobsCount()).isEqualTo(9L);
        assertThat(summary.getActiveApplicationsCount()).isEqualTo(3L);
        assertThat(summary.getUnreadNotificationsCount()).isEqualTo(2L);
        assertThat(summary.getReadiness().getProfileCompletenessPercent()).isEqualTo(100);
        assertThat(summary.getReadiness().isAutopilotEnabled()).isTrue();
        assertThat(summary.getReadiness().isMasterResumeApproved()).isTrue();
        assertThat(summary.getPipelineBreakdown())
                .anyMatch(stage -> "APPLIED".equals(stage.getStatus()) && stage.getCount() == 2L)
                .anyMatch(stage -> "TECH_INTERVIEW".equals(stage.getStatus()) && stage.getCount() == 1L);
        assertThat(summary.getRecentActivity()).hasSize(4);
        assertThat(summary.getRecentActivity().get(0).getType()).isEqualTo("NOTIFICATION");
        assertThat(summary.getRecentActivity().get(1).getType()).isEqualTo("APPLICATION");
        assertThat(summary.getRecentActivity().get(2).getType()).isEqualTo("SAVED_JOB");
        assertThat(summary.getRecentActivity().get(3).getType()).isEqualTo("RESUME");
    }
}

