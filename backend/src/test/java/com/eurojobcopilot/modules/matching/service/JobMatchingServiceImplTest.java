package com.eurojobcopilot.modules.matching.service;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobMatchBreakdownDto;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.config.MatchingProperties;
import com.eurojobcopilot.modules.matching.dto.BatchMatchRecalculationResponse;
import com.eurojobcopilot.modules.matching.engine.DeterministicJobMatchingEngine;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobMatchingServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserSkillRepository userSkillRepository;
    @Mock
    private UserWorkExperienceRepository userWorkExperienceRepository;
    @Mock
    private UserJobPreferenceRepository userJobPreferenceRepository;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private JobMatchScoreRepository jobMatchScoreRepository;

    private JobMatchingServiceImpl service;
    private User user;
    private AuthenticatedUserPrincipal principal;

    @BeforeEach
    void setUp() {
        CanonicalHashGenerator hashGen = new CanonicalHashGenerator();
        JobNormalizer normalizer = new JobNormalizer(hashGen);
        MatchingProperties properties = new MatchingProperties();
        DeterministicJobMatchingEngine engine = new DeterministicJobMatchingEngine(hashGen, normalizer, properties);

        service = new JobMatchingServiceImpl(
                userRepository,
                userProfileRepository,
                userSkillRepository,
                userWorkExperienceRepository,
                userJobPreferenceRepository,
                jobRepository,
                companyRepository,
                jobMatchScoreRepository,
                hashGen,
                properties,
                engine
        );

        user = User.builder()
                .id(11L)
                .uuid("user-uuid-11")
                .email("match.test@eurojob.eu")
                .firstName("Lukas")
                .lastName("Weber")
                .passwordHash("hash")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build();
        principal = AuthenticatedUserPrincipal.from(user);
    }

    @Test
    void evaluateAndPersistJobMatch_invalidInputsOrMissingJob_throwExpectedExceptions() {
        assertThatThrownBy(() -> service.evaluateAndPersistJobMatch(null, 1L))
                .isInstanceOf(UnauthorizedAccessException.class);

        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.evaluateAndPersistJobMatch(principal, 0L))
                .isInstanceOf(BusinessValidationException.class);

        when(jobRepository.findByIdWithCompany(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.evaluateAndPersistJobMatch(principal, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void evaluateAndPersistJobMatch_existingMatchWithAiScore_preservesAiScoreAndRationale() {
        Job job = Job.builder()
                .id(701L)
                .canonicalHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .companyName("Adyen")
                .title("Senior Java Engineer")
                .country("Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .experienceLevel(ExperienceLevel.SENIOR)
                .skills(List.of("Java"))
                .sponsorStatus(JobSponsorStatus.UNCONFIRMED)
                .description("Java backend systems.")
                .postedAt(Instant.now())
                .active(true)
                .build();

        JobMatchScore existing = JobMatchScore.builder()
                .id(55L)
                .user(user)
                .job(job)
                .aiScore(new BigDecimal("84.00"))
                .aiRationale("Strong semantic alignment.")
                .calculatedAt(Instant.now().minusSeconds(3600))
                .build();

        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(jobRepository.findByIdWithCompany(701L)).thenReturn(Optional.of(job));
        when(userProfileRepository.findByUserId(11L)).thenReturn(Optional.empty());
        when(userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(11L)).thenReturn(List.of());
        when(userWorkExperienceRepository.findAllByUserIdOrderByStartDateDesc(11L)).thenReturn(List.of());
        when(userJobPreferenceRepository.findByUserId(11L)).thenReturn(Optional.empty());
        when(jobMatchScoreRepository.findByUserIdAndJobId(11L, 701L)).thenReturn(Optional.of(existing));
        when(jobMatchScoreRepository.saveAndFlush(any(JobMatchScore.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JobMatchBreakdownDto dto = service.evaluateAndPersistJobMatch(principal, 701L);

        assertThat(dto.getDeterministicScore()).isEqualByComparingTo("0.00");
        assertThat(dto.getAiScore()).isEqualByComparingTo("84.00");
        assertThat(dto.getFinalScore()).isEqualByComparingTo("42.00");
        assertThat(dto.getMatchPercentage()).isEqualTo(42);
        assertThat(dto.getAiRationale()).isEqualTo("Strong semantic alignment.");
        assertThat(dto.getExperienceCompatibility()).contains("Missing Data");
        assertThat(dto.getRecommendationReasons()).isNotEmpty();
        verify(jobMatchScoreRepository).saveAndFlush(existing);
    }

    @Test
    void recalculateActiveJobs_whenNoActiveJobs_returnsZeroSummaryWithConfiguredWeights() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(11L)).thenReturn(Optional.empty());
        when(userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(11L)).thenReturn(List.of());
        when(userWorkExperienceRepository.findAllByUserIdOrderByStartDateDesc(11L)).thenReturn(List.of());
        when(userJobPreferenceRepository.findByUserId(11L)).thenReturn(Optional.empty());
        when(jobRepository.findActiveNonExpiredWithCompany(any(Instant.class), any())).thenReturn(List.of());

        BatchMatchRecalculationResponse response = service.recalculateActiveJobs(principal);

        assertThat(response.getEvaluatedJobsCount()).isZero();
        assertThat(response.getHighMatchJobsCount()).isZero();
        assertThat(response.getMinMatchThreshold()).isEqualTo(60);
        assertThat(response.getTopMatches()).isEmpty();
        assertThat(response.getWeightsUsed().getTechnicalSkills()).isEqualTo(40.0);
        assertThat(response.getWeightsUsed().getExperience()).isEqualTo(20.0);
        assertThat(response.getWeightsUsed().getJobTitle()).isEqualTo(15.0);
        assertThat(response.getWeightsUsed().getLocation()).isEqualTo(10.0);
        assertThat(response.getWeightsUsed().getSponsorship()).isEqualTo(10.0);
        assertThat(response.getWeightsUsed().getSalary()).isEqualTo(5.0);
    }

    @Test
    void ensureActiveMatchesUpToDate_unconfiguredUser_skipsBatchRecalculation() {
        when(userProfileRepository.findByUserId(11L)).thenReturn(Optional.empty());
        when(userJobPreferenceRepository.findByUserId(11L)).thenReturn(Optional.empty());
        when(userSkillRepository.countByUserId(11L)).thenReturn(0L);
        when(userWorkExperienceRepository.countByUserId(11L)).thenReturn(0L);

        int recalculated = service.ensureActiveMatchesUpToDate(11L);

        assertThat(recalculated).isZero();
        verifyNoInteractions(jobRepository, jobMatchScoreRepository);
    }

    @Test
    void ensureActiveMatchesUpToDate_freshScores_skipsRecalculation() {
        Instant dataUpdatedAt = Instant.parse("2026-11-09T10:00:00Z");
        Instant calculatedAt = Instant.parse("2026-11-09T10:00:05Z");

        UserProfile profile = UserProfile.builder()
                .user(user)
                .currentDesignation("Java Engineer")
                .yearsOfExperience(new BigDecimal("5.0"))
                .build();
        profile.setUpdatedAt(dataUpdatedAt);

        JobRepository.ActiveJobVersionView jobVersion = mock(JobRepository.ActiveJobVersionView.class);
        when(jobVersion.getId()).thenReturn(101L);
        when(jobVersion.getChangedAt()).thenReturn(dataUpdatedAt);

        when(userProfileRepository.findByUserId(11L)).thenReturn(Optional.of(profile));
        when(userJobPreferenceRepository.findByUserId(11L)).thenReturn(Optional.empty());
        when(userSkillRepository.countByUserId(11L)).thenReturn(1L);
        when(userWorkExperienceRepository.countByUserId(11L)).thenReturn(0L);
        when(jobRepository.findActiveNonExpiredVersions(any(Instant.class), any())).thenReturn(List.of(jobVersion));
        when(jobMatchScoreRepository.countByUserIdAndJobIdIn(11L, List.of(101L))).thenReturn(1L);
        when(jobMatchScoreRepository.findMinCalculatedAtByUserIdAndJobIdIn(11L, List.of(101L)))
                .thenReturn(Optional.of(calculatedAt));
        when(userSkillRepository.findLatestTimestampByUserId(11L)).thenReturn(Optional.of(dataUpdatedAt));
        when(userWorkExperienceRepository.findLatestTimestampByUserId(11L)).thenReturn(Optional.empty());

        int recalculated = service.ensureActiveMatchesUpToDate(11L);

        assertThat(recalculated).isZero();
        verify(jobRepository, never()).findActiveNonExpiredWithCompany(any(), any());
        verify(jobMatchScoreRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void ensureActiveMatchesUpToDate_staleScores_recalculatesActiveJobs() {
        Instant staleCalculatedAt = Instant.parse("2026-11-09T09:00:00Z");
        Instant profileUpdatedAt = Instant.parse("2026-11-09T10:00:00Z");

        UserProfile profile = UserProfile.builder()
                .user(user)
                .currentDesignation("Senior Java Engineer")
                .yearsOfExperience(new BigDecimal("6.0"))
                .build();
        profile.setUpdatedAt(profileUpdatedAt);

        JobRepository.ActiveJobVersionView jobVersion = mock(JobRepository.ActiveJobVersionView.class);
        when(jobVersion.getId()).thenReturn(101L);
        when(jobVersion.getChangedAt()).thenReturn(staleCalculatedAt);

        Job activeJob = Job.builder()
                .id(101L)
                .canonicalHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .companyName("Adyen")
                .title("Senior Java Engineer")
                .country("Netherlands")
                .skills(List.of("Java"))
                .description("Backend role.")
                .postedAt(staleCalculatedAt)
                .active(true)
                .build();

        when(userProfileRepository.findByUserId(11L)).thenReturn(Optional.of(profile));
        when(userJobPreferenceRepository.findByUserId(11L)).thenReturn(Optional.empty());
        when(userSkillRepository.countByUserId(11L)).thenReturn(0L);
        when(userWorkExperienceRepository.countByUserId(11L)).thenReturn(0L);
        when(jobRepository.findActiveNonExpiredVersions(any(Instant.class), any())).thenReturn(List.of(jobVersion));
        when(jobMatchScoreRepository.countByUserIdAndJobIdIn(11L, List.of(101L))).thenReturn(1L);
        when(jobMatchScoreRepository.findMinCalculatedAtByUserIdAndJobIdIn(11L, List.of(101L)))
                .thenReturn(Optional.of(staleCalculatedAt));
        when(userSkillRepository.findLatestTimestampByUserId(11L)).thenReturn(Optional.empty());
        when(userWorkExperienceRepository.findLatestTimestampByUserId(11L)).thenReturn(Optional.empty());

        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(jobRepository.findActiveNonExpiredWithCompany(any(Instant.class), any())).thenReturn(List.of(activeJob));
        when(userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(11L)).thenReturn(List.of());
        when(userWorkExperienceRepository.findAllByUserIdOrderByStartDateDesc(11L)).thenReturn(List.of());
        when(jobMatchScoreRepository.findAllByUserIdAndJobIdIn(11L, List.of(101L))).thenReturn(List.of());
        when(jobMatchScoreRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        int recalculated = service.ensureActiveMatchesUpToDate(11L);

        assertThat(recalculated).isEqualTo(1);
        verify(jobMatchScoreRepository).saveAllAndFlush(any());
    }
}

