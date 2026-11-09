package com.eurojobcopilot.modules.job.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceConflictException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.ApplyRedirectRequest;
import com.eurojobcopilot.modules.job.dto.ApplyRedirectResponse;
import com.eurojobcopilot.modules.job.dto.JobInteractionResponse;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobInteractionServiceImplTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserJobInteractionRepository userJobInteractionRepository;

    private JobInteractionServiceImpl service;
    private User user;
    private AuthenticatedUserPrincipal principal;
    private Job job;

    @BeforeEach
    void setUp() {
        service = new JobInteractionServiceImpl(
                jobRepository,
                userRepository,
                userJobInteractionRepository
        );
        user = User.builder()
                .id(42L)
                .uuid("user-uuid-42")
                .email("candidate@eurojob.eu")
                .passwordHash("hashed")
                .firstName("Alex")
                .lastName("Mueller")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build();
        principal = AuthenticatedUserPrincipal.from(user);
        job = Job.builder()
                .id(1042L)
                .canonicalHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .companyName("Adyen")
                .title("Senior Java Engineer")
                .normalizedTitle("senior java engineer")
                .country("Netherlands")
                .description("Payment platform engineering role.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.adyen.com/jobs/1042")
                .applicationUrl("https://careers.adyen.com/jobs/1042/apply")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .active(true)
                .build();
    }

    @Test
    void interactions_validatePrincipalPositiveJobIdAndJobExistence() {
        assertThatThrownBy(() -> service.saveJob(null, 1042L))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Authenticated user");

        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.saveJob(principal, 0L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("positive");

        assertThatThrownBy(() -> service.unsaveJob(principal, -5L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("positive");

        when(jobRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.saveJob(principal, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");

        when(jobRepository.existsById(999L)).thenReturn(false);
        assertThatThrownBy(() -> service.unhideJob(principal, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void saveAndUnsaveJob_areIdempotentAndPreserveOtherFlags() {
        Instant existingInterestedAt = Instant.now().minus(2, ChronoUnit.DAYS);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(jobRepository.findById(1042L)).thenReturn(Optional.of(job));
        when(userJobInteractionRepository.findByUserIdAndJobId(42L, 1042L)).thenReturn(Optional.empty());
        when(userJobInteractionRepository.saveAndFlush(any(UserJobInteraction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JobInteractionResponse created = service.saveJob(principal, 1042L);
        assertThat(created.getJobId()).isEqualTo(1042L);
        assertThat(created.isSaved()).isTrue();
        assertThat(created.getSavedAt()).isNotNull();
        assertThat(created.isInterested()).isFalse();

        // Repeat saveJob when already saved should preserve original savedAt and skip flush
        UserJobInteraction alreadySaved = UserJobInteraction.builder()
                .user(user)
                .job(job)
                .saved(true)
                .savedAt(created.getSavedAt())
                .interested(true)
                .interestedAt(existingInterestedAt)
                .build();
        when(userJobInteractionRepository.findByUserIdAndJobId(42L, 1042L))
                .thenReturn(Optional.of(alreadySaved));

        JobInteractionResponse repeatSaved = service.saveJob(principal, 1042L);
        assertThat(repeatSaved.isSaved()).isTrue();
        assertThat(repeatSaved.getSavedAt()).isEqualTo(created.getSavedAt());
        assertThat(repeatSaved.isInterested()).isTrue();
        verify(userJobInteractionRepository).saveAndFlush(any(UserJobInteraction.class));

        // Unsave clears saved + savedAt while leaving interested untouched
        when(jobRepository.existsById(1042L)).thenReturn(true);
        JobInteractionResponse unsaved = service.unsaveJob(principal, 1042L);
        assertThat(unsaved.isSaved()).isFalse();
        assertThat(unsaved.getSavedAt()).isNull();
        assertThat(unsaved.isInterested()).isTrue();
        assertThat(unsaved.getInterestedAt()).isEqualTo(existingInterestedAt);
    }

    @Test
    void deleteEndpoints_whenNoInteractionRowExists_doNotInsertRow() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(jobRepository.existsById(1042L)).thenReturn(true);
        when(userJobInteractionRepository.findByUserIdAndJobId(42L, 1042L)).thenReturn(Optional.empty());

        JobInteractionResponse unsave = service.unsaveJob(principal, 1042L);
        JobInteractionResponse clearInterested = service.clearInterested(principal, 1042L);
        JobInteractionResponse unhide = service.unhideJob(principal, 1042L);

        assertThat(unsave.isSaved()).isFalse();
        assertThat(clearInterested.isInterested()).isFalse();
        assertThat(unhide.isHidden()).isFalse();
        verify(userJobInteractionRepository, never()).saveAndFlush(any());
    }

    @Test
    void markInterestedAndHideJob_toggleFlagsCorrectlyAndHandleConflicts() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(jobRepository.findById(1042L)).thenReturn(Optional.of(job));
        when(jobRepository.existsById(1042L)).thenReturn(true);
        when(userJobInteractionRepository.findByUserIdAndJobId(42L, 1042L)).thenReturn(Optional.empty());
        when(userJobInteractionRepository.saveAndFlush(any(UserJobInteraction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JobInteractionResponse interestedResp = service.markInterested(principal, 1042L);
        assertThat(interestedResp.isInterested()).isTrue();
        assertThat(interestedResp.getInterestedAt()).isNotNull();

        JobInteractionResponse hiddenResp = service.hideJob(principal, 1042L);
        assertThat(hiddenResp.isHidden()).isTrue();
        assertThat(hiddenResp.getHiddenAt()).isNotNull();

        UserJobInteraction hiddenEntity = UserJobInteraction.builder()
                .user(user)
                .job(job)
                .hidden(true)
                .hiddenAt(hiddenResp.getHiddenAt())
                .build();
        when(userJobInteractionRepository.findByUserIdAndJobId(42L, 1042L))
                .thenReturn(Optional.of(hiddenEntity));
        JobInteractionResponse unhidResp = service.unhideJob(principal, 1042L);
        assertThat(unhidResp.isHidden()).isFalse();
        assertThat(unhidResp.getHiddenAt()).isNull();

        // Concurrent unique constraint race throws ResourceConflictException
        when(userJobInteractionRepository.findByUserIdAndJobId(42L, 1042L)).thenReturn(Optional.empty());
        when(userJobInteractionRepository.saveAndFlush(any(UserJobInteraction.class)))
                .thenThrow(new DataIntegrityViolationException("uk_interactions_user_job"));

        assertThatThrownBy(() -> service.hideJob(principal, 1042L))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("Concurrent");
    }

    @Test
    void trackApplyRedirect_validatesExternalUrlsAndTracksRedirectAndAppliedTimestamps() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(jobRepository.findById(1042L)).thenReturn(Optional.of(job));
        when(userJobInteractionRepository.findByUserIdAndJobId(42L, 1042L)).thenReturn(Optional.empty());
        when(userJobInteractionRepository.saveAndFlush(any(UserJobInteraction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // 1. Prefers applicationUrl when valid; null request keeps hasApplied=false
        ApplyRedirectResponse redirectOnly = service.trackApplyRedirect(principal, 1042L, null);
        assertThat(redirectOnly.getTargetUrl()).isEqualTo("https://careers.adyen.com/jobs/1042/apply");
        assertThat(redirectOnly.getRedirectClickedAt()).isNotNull();
        assertThat(redirectOnly.isHasApplied()).isFalse();
        assertThat(redirectOnly.getAppliedAt()).isNull();
        assertThat(redirectOnly.getInteraction().isHasApplied()).isFalse();

        // 2. Falls back cleanly to originalUrl when applicationUrl has unsafe scheme
        job.setApplicationUrl("javascript:alert(1)");
        job.setOriginalUrl("  https://jobs.example.eu/safe-fallback  ");
        ApplyRedirectResponse markedApplied = service.trackApplyRedirect(
                principal,
                1042L,
                ApplyRedirectRequest.builder().markApplied(true).build()
        );
        assertThat(markedApplied.getTargetUrl()).isEqualTo("https://jobs.example.eu/safe-fallback");
        assertThat(markedApplied.isHasApplied()).isTrue();
        assertThat(markedApplied.getAppliedAt()).isNotNull();

        // 3. Subsequent redirect click preserves initial appliedAt while updating redirectClickedAt
        Instant firstAppliedAt = markedApplied.getAppliedAt().minus(1, ChronoUnit.DAYS);
        UserJobInteraction alreadyApplied = UserJobInteraction.builder()
                .user(user)
                .job(job)
                .hasApplied(true)
                .appliedAt(firstAppliedAt)
                .redirectClickedAt(firstAppliedAt)
                .build();
        when(userJobInteractionRepository.findByUserIdAndJobId(42L, 1042L))
                .thenReturn(Optional.of(alreadyApplied));

        ApplyRedirectResponse subsequent = service.trackApplyRedirect(
                principal,
                1042L,
                ApplyRedirectRequest.builder().markApplied(true).build()
        );
        assertThat(subsequent.getAppliedAt()).isEqualTo(firstAppliedAt);
        assertThat(subsequent.getRedirectClickedAt()).isAfter(firstAppliedAt);

        // 4. Rejects jobs without a valid http/https URL
        job.setApplicationUrl("ftp://invalid.example.com/job");
        job.setOriginalUrl("data:text/html,xss");
        assertThatThrownBy(() -> service.trackApplyRedirect(principal, 1042L, null))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("valid external application URL");
    }
}

