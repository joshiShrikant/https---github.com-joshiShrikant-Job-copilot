package com.eurojobcopilot.modules.job.service;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobDetailDto;
import com.eurojobcopilot.modules.job.dto.JobSearchCriteria;
import com.eurojobcopilot.modules.job.dto.JobSortOption;
import com.eurojobcopilot.modules.job.dto.JobSummaryDto;
import com.eurojobcopilot.modules.job.dto.PagedJobsResponse;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.matching.service.JobMatchingService;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipCatalog;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.OfficialSponsorRegistryEntry;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import com.eurojobcopilot.modules.sponsorship.entity.VisaSponsorshipStatus;
import com.eurojobcopilot.modules.sponsorship.ind.SponsorshipClassifier;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanySponsorshipRecordRepository;
import com.eurojobcopilot.modules.sponsorship.repository.OfficialSponsorRegistryEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobQueryServiceImplTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserJobInteractionRepository userJobInteractionRepository;

    @Mock
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Mock
    private JobAiAnalysisRepository jobAiAnalysisRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanySponsorshipRecordRepository companySponsorshipRecordRepository;

    @Mock
    private OfficialSponsorRegistryEntryRepository officialSponsorRegistryEntryRepository;

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private JobMatchingService jobMatchingService;

    private JobQueryServiceImpl service;
    private AuthenticatedUserPrincipal principal;
    private User user;

    @BeforeEach
    void setUp() {
        CanonicalHashGenerator canonicalHashGenerator = new CanonicalHashGenerator();
        JobNormalizer normalizer = new JobNormalizer(canonicalHashGenerator);
        service = new JobQueryServiceImpl(
                jobRepository,
                userJobInteractionRepository,
                jobMatchScoreRepository,
                jobAiAnalysisRepository,
                companyRepository,
                companySponsorshipRecordRepository,
                officialSponsorRegistryEntryRepository,
                jobApplicationRepository,
                normalizer,
                canonicalHashGenerator,
                new CountrySponsorshipCatalog(),
                new SponsorshipClassifier(),
                jobMatchingService
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
    }

    @Test
    void searchJobs_withoutAuthenticatedUser_throwsUnauthorized() {
        JobSearchCriteria criteria = JobSearchCriteria.builder().page(0).size(20).build();

        assertThatThrownBy(() -> service.searchJobs(null, criteria))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Authenticated user");
    }

    @Test
    void searchJobs_withUnsupportedSortParameter_throwsBusinessValidation() {
        JobSearchCriteria criteria = JobSearchCriteria.builder()
                .page(0)
                .size(20)
                .sort("companyName,asc")
                .build();

        assertThatThrownBy(() -> service.searchJobs(principal, criteria))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Unsupported sort parameter");

        verify(jobRepository, never()).searchActiveJobs(any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchJobs_enrichesBatchInteractionsAndMatchScoresWithoutNPlusOne() {
        Job job1 = Job.builder()
                .id(101L)
                .canonicalHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .companyName("Adyen")
                .title("Senior Java Engineer")
                .normalizedTitle("senior java engineer")
                .location("Amsterdam, Netherlands")
                .city("Amsterdam")
                .country("Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .salaryMin(new BigDecimal("85000.00"))
                .salaryMax(new BigDecimal("105000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .description("Long payment engineering description ".repeat(20))
                .descriptionSnippet(null)
                .skills(List.of("Java", "Spring Boot"))
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.example.eu/101")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(Instant.parse("2026-04-01T10:00:00Z"))
                .active(true)
                .build();

        Job job2 = Job.builder()
                .id(102L)
                .canonicalHash("fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210")
                .companyName("Klarna")
                .title("Backend Developer")
                .normalizedTitle("backend developer")
                .country("Germany")
                .workArrangement(WorkArrangement.REMOTE)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.MID)
                .description("Cloud services")
                .descriptionSnippet("Short curated snippet")
                .skills(null)
                .sourceProvider("ADZUNA")
                .originalUrl("https://careers.example.eu/102")
                .sponsorStatus(JobSponsorStatus.UNCONFIRMED)
                .postedAt(Instant.parse("2026-04-01T08:00:00Z"))
                .active(true)
                .build();

        Page<Job> stubPage = new PageImpl<>(List.of(job1, job2), PageRequest.of(0, 2), 5);

        when(jobRepository.searchActiveJobs(
                eq(42L),
                any(JobSearchCriteria.class),
                eq("Germany"),
                eq(PageRequest.of(0, 2)),
                eq(JobSortOption.MATCH_SCORE_DESC),
                any(Instant.class)
        )).thenReturn(stubPage);

        when(userJobInteractionRepository.findAllByUserIdAndJobIdIn(42L, List.of(101L, 102L)))
                .thenReturn(List.of(
                        UserJobInteraction.builder()
                                .user(user)
                                .job(job1)
                                .saved(true)
                                .interested(true)
                                .hidden(false)
                                .build()
                ));

        when(jobMatchScoreRepository.findAllByUserIdAndJobIdIn(42L, List.of(101L, 102L)))
                .thenReturn(List.of(
                        JobMatchScore.builder()
                                .user(user)
                                .job(job1)
                                .finalScore(new BigDecimal("87.60"))
                                .build()
                ));

        JobSearchCriteria criteria = JobSearchCriteria.builder()
                .country("DE")
                .page(0)
                .size(2)
                .sort("matchScore,desc")
                .build();

        PagedJobsResponse response = service.searchJobs(principal, criteria);

        assertThat(response.getTotalElements()).isEqualTo(5L);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.getPageNumber()).isEqualTo(0);
        assertThat(response.getPageSize()).isEqualTo(2);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isFalse();
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.isHasPrevious()).isFalse();
        assertThat(response.getContent()).hasSize(2);

        JobSummaryDto first = response.getContent().get(0);
        assertThat(first.getMatchPercentage()).isEqualTo(88);
        assertThat(first.isSaved()).isTrue();
        assertThat(first.isInterested()).isTrue();
        assertThat(first.isHidden()).isFalse();
        assertThat(first.getSkills()).containsExactly("Java", "Spring Boot");
        assertThat(first.getDescriptionSnippet()).hasSizeLessThanOrEqualTo(500).endsWith("...");

        JobSummaryDto second = response.getContent().get(1);
        assertThat(second.getMatchPercentage()).isNull();
        assertThat(second.isSaved()).isFalse();
        assertThat(second.isInterested()).isFalse();
        assertThat(second.getSkills()).isEmpty();
        assertThat(second.getDescriptionSnippet()).isEqualTo("Short curated snippet");

        verify(userJobInteractionRepository).findAllByUserIdAndJobIdIn(42L, List.of(101L, 102L));
        verify(jobMatchScoreRepository).findAllByUserIdAndJobIdIn(42L, List.of(101L, 102L));
    }

    @Test
    void searchJobs_whenPageEmpty_skipsBatchInteractionAndScoreLookups() {
        when(jobRepository.searchActiveJobs(
                eq(42L),
                any(JobSearchCriteria.class),
                isNull(),
                eq(PageRequest.of(0, 20)),
                eq(JobSortOption.POSTED_AT_DESC),
                any(Instant.class)
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        PagedJobsResponse response = service.searchJobs(principal, null);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isTrue();
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.isHasPrevious()).isFalse();

        verify(userJobInteractionRepository, never()).findAllByUserIdAndJobIdIn(any(), anyList());
        verify(jobMatchScoreRepository, never()).findAllByUserIdAndJobIdIn(any(), anyList());
    }

    @Test
    void getJobDetail_invalidOrMissingJob_throwsAppropriateExceptions() {
        assertThatThrownBy(() -> service.getJobDetail(null, 1L))
                .isInstanceOf(UnauthorizedAccessException.class);

        assertThatThrownBy(() -> service.getJobDetail(principal, 0L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("positive");

        when(jobRepository.findByIdWithCompany(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getJobDetail(principal, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void getJobDetail_resolvesFallbackCompanySponsorshipMatchAndAiAnalysis() {
        Job job = Job.builder()
                .id(501L)
                .canonicalHash("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789")
                .company(null)
                .companyName("Adyen N.V.")
                .title("Staff Java Architect")
                .normalizedTitle("staff java architect")
                .location("Amsterdam, Netherlands")
                .city("Amsterdam")
                .country("Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.LEAD)
                .description("Join our core payments architecture team with visa sponsorship and relocation package.")
                .descriptionSnippet("Core payments architecture team.")
                .skills(List.of("Java", "Spring Boot", "Kafka"))
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.adyen.com/jobs/501")
                .sponsorStatus(JobSponsorStatus.SPONSORSHIP_MENTIONED)
                .postedAt(Instant.parse("2026-04-02T09:00:00Z"))
                .active(true)
                .build();

        Company adyen = Company.builder()
                .id(77L)
                .name("Adyen N.V.")
                .normalizedName("adyen")
                .headquartersCountry("Netherlands")
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .build();

        CompanySponsorshipRecord indRecord = CompanySponsorshipRecord.builder()
                .id(12L)
                .company(adyen)
                .country("Netherlands")
                .registrySource("NL_IND_PUBLIC_REGISTER")
                .registryIdentifier("IND-34259528")
                .officialRegisteredName("Adyen N.V.")
                .status(SponsorshipRecordStatus.RECOGNIZED_SPONSOR)
                .verifiedAt(Instant.parse("2026-04-01T00:00:00Z"))
                .build();

        JobMatchScore score = JobMatchScore.builder()
                .user(user)
                .job(job)
                .deterministicScore(new BigDecimal("85.00"))
                .aiScore(new BigDecimal("92.00"))
                .finalScore(new BigDecimal("88.50"))
                .skillScore(new BigDecimal("90.00"))
                .experienceScore(new BigDecimal("88.00"))
                .titleScore(new BigDecimal("95.00"))
                .locationScore(new BigDecimal("100.00"))
                .sponsorshipScore(new BigDecimal("100.00"))
                .salaryScore(new BigDecimal("75.00"))
                .matchingSkills(List.of("Java", "Spring Boot"))
                .missingSkills(List.of("Kafka"))
                .aiRationale("Strong Java core match; highlight messaging systems.")
                .calculatedAt(Instant.parse("2026-04-02T10:00:00Z"))
                .build();

        JobAiAnalysis aiAnalysis = JobAiAnalysis.builder()
                .job(job)
                .requiredSkills(List.of("Java 21", "Distributed Systems"))
                .niceToHaveSkills(List.of("Kafka"))
                .responsibilities(List.of("Architect resilient payment gateways"))
                .benefits(List.of("Relocation allowance"))
                .languageRequirements(List.of("English"))
                .visaSponsorshipAnalysis("Confirmed IND recognized sponsor")
                .relocationAnalysis("Full EU relocation package included")
                .summary("High-impact Java leadership role in Amsterdam.")
                .aiModelUsed("gemini-2.5-flash")
                .build();

        JobApplication trackedApp = JobApplication.builder()
                .uuid("app-uuid-501")
                .user(user)
                .job(job)
                .status(ApplicationStatus.INTERESTED)
                .build();

        when(jobRepository.findByIdWithCompany(501L)).thenReturn(Optional.of(job));
        when(companyRepository.findByNormalizedName("adyen")).thenReturn(Optional.of(adyen));
        when(companySponsorshipRecordRepository.findAllByCompanyIdOrderByVerifiedAtDesc(77L))
                .thenReturn(List.of(indRecord));
        when(userJobInteractionRepository.findByUserIdAndJobId(42L, 501L))
                .thenReturn(Optional.of(UserJobInteraction.builder()
                        .user(user).job(job).saved(true).interested(true).hasApplied(false).build()));
        when(jobMatchScoreRepository.findByUserIdAndJobId(42L, 501L)).thenReturn(Optional.of(score));
        when(jobAiAnalysisRepository.findByJobId(501L)).thenReturn(Optional.of(aiAnalysis));
        when(jobApplicationRepository.findByUserIdAndJobId(42L, 501L)).thenReturn(Optional.of(trackedApp));

        JobDetailDto detail = service.getJobDetail(principal, 501L);

        assertThat(detail.getId()).isEqualTo(501L);
        assertThat(detail.getCompany()).isNotNull();
        assertThat(detail.getCompany().getName()).isEqualTo("Adyen N.V.");
        assertThat(detail.getCompany().isVerifiedSponsor()).isTrue();
        assertThat(detail.getSponsorshipBreakdown().isVerifiedSponsor()).isTrue();
        assertThat(detail.getSponsorshipBreakdown().getConfidenceLevel()).isEqualTo(SponsorConfidenceLevel.VERIFIED);
        assertThat(detail.getSponsorshipBreakdown().getRegistryRecords()).hasSize(1);
        assertThat(detail.getSponsorshipBreakdown().getEvidenceSignals()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(detail.getMatchPercentage()).isEqualTo(89);
        assertThat(detail.getMatchBreakdown().getMatchingSkills()).containsExactly("Java", "Spring Boot");
        assertThat(detail.getMatchBreakdown().getMissingSkills()).containsExactly("Kafka");
        assertThat(detail.getAiAnalysis().getAiModelUsed()).isEqualTo("gemini-2.5-flash");
        assertThat(detail.getApplication().getUuid()).isEqualTo("app-uuid-501");
        assertThat(detail.isSaved()).isTrue();
        assertThat(detail.isInterested()).isTrue();
        // ApplicationStatus.INTERESTED must not falsely mark hasApplied=true
        assertThat(detail.isHasApplied()).isFalse();
    }

    @Test
    void getJobDetail_enrichesSyncedIndRegistryAndOverridesDetailedStatusWhenVacancyDeclines() {
        Job decliningIndJob = Job.builder()
                .id(602L)
                .canonicalHash("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
                .companyName("Mollie B.V.")
                .title("Support Coordinator")
                .normalizedTitle("support coordinator")
                .country("NL")
                .city("Amsterdam")
                .location("Amsterdam, Netherlands")
                .workArrangement(WorkArrangement.ONSITE)
                .employmentType(EmploymentType.FULL_TIME)
                .description("No visa sponsorship offered for this local operations opening.")
                .skills(List.of("Support"))
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.mollie.com/602")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(Instant.parse("2026-04-03T10:00:00Z"))
                .active(true)
                .build();

        OfficialSponsorRegistryEntry syncedEntry = OfficialSponsorRegistryEntry.builder()
                .id(88L)
                .country("Netherlands")
                .registrySource(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE)
                .registryIdentifier("KVK-50205773")
                .officialName("Mollie B.V.")
                .normalizedName("mollie")
                .status(SponsorshipRecordStatus.RECOGNIZED_SPONSOR)
                .sourceUrl("https://ind.nl/en/public-register-recognised-sponsors")
                .active(true)
                .syncedAt(Instant.parse("2026-04-02T00:00:00Z"))
                .build();

        when(jobRepository.findByIdWithCompany(602L)).thenReturn(Optional.of(decliningIndJob));
        when(companyRepository.findByNormalizedName("mollie")).thenReturn(Optional.empty());
        when(companyRepository.findByNormalizedName("mollie b.v.")).thenReturn(Optional.empty());
        when(officialSponsorRegistryEntryRepository
                .findFirstByCountryIgnoreCaseAndNormalizedNameIgnoreCaseAndActiveTrueOrderBySyncedAtDesc(
                        "Netherlands", "mollie"))
                .thenReturn(Optional.of(syncedEntry));
        when(officialSponsorRegistryEntryRepository.countByCountryIgnoreCaseAndActiveTrue("Netherlands"))
                .thenReturn(1L);
        when(userJobInteractionRepository.findByUserIdAndJobId(42L, 602L)).thenReturn(Optional.empty());
        when(jobMatchScoreRepository.findByUserIdAndJobId(42L, 602L)).thenReturn(Optional.empty());
        when(jobAiAnalysisRepository.findByJobId(602L)).thenReturn(Optional.empty());
        when(jobApplicationRepository.findByUserIdAndJobId(42L, 602L)).thenReturn(Optional.empty());

        JobDetailDto detail = service.getJobDetail(principal, 602L);

        assertThat(detail.getSponsorshipBreakdown()).isNotNull();
        assertThat(detail.getSponsorshipBreakdown().getDetailedStatus()).isEqualTo(VisaSponsorshipStatus.UNAVAILABLE);
        assertThat(detail.getSponsorshipBreakdown().getVacancyStatus()).isEqualTo(VisaSponsorshipStatus.UNAVAILABLE);
        assertThat(detail.getSponsorshipBreakdown().isVacancySponsorshipConfirmed()).isFalse();
        assertThat(detail.getSponsorshipBreakdown().isVacancySponsorshipAssumed()).isFalse();
        assertThat(detail.getSponsorshipBreakdown().isVerifiedSponsor()).isFalse();
        assertThat(detail.getSponsorshipBreakdown().getVacancyCaveat()).containsIgnoringCase("never guarantees");
        assertThat(detail.getSponsorshipBreakdown().getRegistryRecords()).hasSize(1);
        assertThat(detail.getSponsorshipBreakdown().getRegistryRecords().getFirst().getRegistryIdentifier())
                .isEqualTo("KVK-50205773");
    }
}
