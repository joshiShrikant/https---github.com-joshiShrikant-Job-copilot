package com.eurojobcopilot.modules.job.ingestion;

import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.modules.job.entity.FetchStatus;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobFetchLog;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.normalization.DeduplicationService;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.repository.JobFetchLogRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class JobIngestionPipelineIntegrationTest {

    @Autowired
    private JobNormalizer jobNormalizer;

    @Autowired
    private DeduplicationService deduplicationService;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobFetchLogRepository jobFetchLogRepository;

    @Autowired
    private JobIngestionProperties properties;

    @Autowired
    private JobIngestionService springJobIngestionService;

    @Autowired
    @Qualifier("jobIngestionRestClientBuilder")
    private RestClient.Builder restClientBuilder;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        jobFetchLogRepository.deleteAll();
        jobMatchScoreRepository.deleteAllInBatch();
        jobRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void shouldExposeConfiguredIngestionBeansCleanly() {
        assertThat(springJobIngestionService).isNotNull();
        assertThat(restClientBuilder).isNotNull();
        assertThat(properties.getDefaultMaxPages()).isEqualTo(3);

        IngestionExecutionSummary emptySummary = springJobIngestionService.ingestAll(null);
        assertThat(emptySummary.providersExecuted()).isZero();
        assertThat(emptySummary.totalInserted()).isZero();
    }

    @Test
    void shouldInsertUpgradeVerifiedSponsorDeduplicateCrossProviderAndSkipDuplicates() {
        companyRepository.save(Company.builder()
                .name("Adyen N.V.")
                .normalizedName("adyen")
                .headquartersCountry("Netherlands")
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .build());

        JobProvider arbeitnowMock = stubProvider("ARBEITNOW", true, List.of(
                RawJobPosting.builder()
                        .sourceProvider("ARBEITNOW")
                        .sourceJobId("arb-101")
                        .title("Senior Java Engineer (m/f/d)")
                        .companyName("Adyen B.V.")
                        .city("Amsterdam")
                        .country("NL")
                        .workArrangementHint("hybrid")
                        .rawDescription("<p>Join our core Java & Spring Boot payments team.</p>")
                        .originalUrl("https://arbeitnow.com/jobs/arb-101")
                        .postedAt(Instant.parse("2026-11-08T09:00:00Z"))
                        .build()
        ));

        JobIngestionServiceImpl orchestrator = new JobIngestionServiceImpl(
                List.of(arbeitnowMock),
                jobNormalizer,
                deduplicationService,
                jobFetchLogRepository,
                properties
        );

        ProviderIngestionResult firstRun = orchestrator.ingestFromProvider("ARBEITNOW", null);
        assertThat(firstRun.status()).isEqualTo(FetchStatus.SUCCESS);
        assertThat(firstRun.jobsInserted()).isEqualTo(1);
        assertThat(firstRun.jobsUpdated()).isZero();
        assertThat(firstRun.jobsSkippedDuplicates()).isZero();

        List<Job> persistedJobs = jobRepository.findAll();
        assertThat(persistedJobs).hasSize(1);
        Job savedJob = persistedJobs.getFirst();
        assertThat(savedJob.getSponsorStatus()).isEqualTo(JobSponsorStatus.VERIFIED_SPONSOR);
        assertThat(savedJob.getSalaryMin()).isNull();

        // Cross-provider duplicate from ADZUNA enriches missing salary & longer JD without inserting duplicate row
        JobProvider adzunaEnrichingDuplicate = stubProvider("ADZUNA", true, List.of(
                RawJobPosting.builder()
                        .sourceProvider("ADZUNA")
                        .sourceJobId("adz-777")
                        .title("Senior Java Engineer")
                        .companyName("Adyen")
                        .location("Amsterdam, Netherlands")
                        .salaryMin(new BigDecimal("90000"))
                        .salaryMax(new BigDecimal("115000"))
                        .salaryCurrency("EUR")
                        .rawDescription("<p>Join our core Java & Spring Boot payments team in Amsterdam building ultra-reliable low-latency acquiring systems across Europe.</p>")
                        .tags(List.of("Kafka", "PostgreSQL"))
                        .originalUrl("https://adzuna.nl/jobs/adz-777")
                        .applicationUrl("https://careers.adyen.com/apply/java")
                        .postedAt(Instant.parse("2026-11-08T11:00:00Z"))
                        .build()
        ));

        JobIngestionServiceImpl secondOrchestrator = new JobIngestionServiceImpl(
                List.of(adzunaEnrichingDuplicate),
                jobNormalizer,
                deduplicationService,
                jobFetchLogRepository,
                properties
        );

        ProviderIngestionResult enrichRun = secondOrchestrator.ingestFromProvider("ADZUNA", null);
        assertThat(enrichRun.status()).isEqualTo(FetchStatus.SUCCESS);
        assertThat(enrichRun.jobsInserted()).isZero();
        assertThat(enrichRun.jobsUpdated()).isEqualTo(1);
        assertThat(jobRepository.count()).isEqualTo(1);

        Job enrichedJob = jobRepository.findAll().getFirst();
        assertThat(enrichedJob.getSalaryMin()).isEqualByComparingTo("90000");
        assertThat(enrichedJob.getSalaryMax()).isEqualByComparingTo("115000");
        assertThat(enrichedJob.getApplicationUrl()).isEqualTo("https://careers.adyen.com/apply/java");
        assertThat(enrichedJob.getSkills()).contains("Java", "Spring Boot", "Kafka", "PostgreSQL");

        // Re-running identical cross-provider payload yields SKIPPED_DUPLICATE with zero new writes
        ProviderIngestionResult duplicateSkipRun = secondOrchestrator.ingestFromProvider("ADZUNA", null);
        assertThat(duplicateSkipRun.jobsInserted()).isZero();
        assertThat(duplicateSkipRun.jobsUpdated()).isZero();
        assertThat(duplicateSkipRun.jobsSkippedDuplicates()).isEqualTo(1);
        assertThat(jobRepository.count()).isEqualTo(1);

        // Re-polling original provider with revised title/salary updates existing (sourceProvider, sourceJobId) row cleanly
        JobProvider updatedArbeitnow = stubProvider("ARBEITNOW", true, List.of(
                RawJobPosting.builder()
                        .sourceProvider("ARBEITNOW")
                        .sourceJobId("arb-101")
                        .title("Staff Java Engineer")
                        .companyName("Adyen B.V.")
                        .city("Amsterdam")
                        .country("NL")
                        .workArrangementHint("hybrid")
                        .salaryMin(new BigDecimal("100000"))
                        .salaryMax(new BigDecimal("125000"))
                        .salaryCurrency("EUR")
                        .rawDescription("<p>Lead core Java & Spring Boot distributed payments architecture.</p>")
                        .originalUrl("https://arbeitnow.com/jobs/arb-101")
                        .postedAt(Instant.parse("2026-11-08T12:00:00Z"))
                        .build()
        ));
        JobIngestionServiceImpl thirdOrchestrator = new JobIngestionServiceImpl(
                List.of(updatedArbeitnow),
                jobNormalizer,
                deduplicationService,
                jobFetchLogRepository,
                properties
        );
        ProviderIngestionResult sameProviderUpdate = thirdOrchestrator.ingestFromProvider("ARBEITNOW", null);
        assertThat(sameProviderUpdate.jobsUpdated()).isEqualTo(1);
        assertThat(jobRepository.count()).isEqualTo(1);
        assertThat(jobRepository.findAll().getFirst().getTitle()).isEqualTo("Staff Java Engineer");
    }

    @Test
    void shouldIsolateFailingProviderSkipDisabledProviderAndRecordPartialSuccess() {
        JobProvider failingProvider = new JobProvider() {
            @Override
            public String getProviderName() {
                return "BROKEN_PROVIDER";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public List<RawJobPosting> fetchJobs(IngestionQuery query) {
                throw new IllegalStateException("HTTP 503 upstream timeout");
            }
        };

        JobProvider disabledProvider = stubProvider("DISABLED_PROVIDER", false, List.of());

        JobProvider partialProvider = stubProvider("ADZUNA", true, List.of(
                RawJobPosting.builder()
                        .sourceProvider("ADZUNA")
                        .sourceJobId("bad-item")
                        .title("")
                        .companyName("Missing Title Co")
                        .country("Germany")
                        .originalUrl("https://example.com/bad")
                        .build(),
                RawJobPosting.builder()
                        .sourceProvider("ADZUNA")
                        .sourceJobId("good-1")
                        .title("Angular Lead Engineer")
                        .companyName("Personio SE")
                        .city("Munich")
                        .country("DE")
                        .rawDescription("Build enterprise Angular apps with TypeScript.")
                        .originalUrl("https://adzuna.de/jobs/good-1")
                        .build()
        ));

        JobIngestionServiceImpl orchestrator = new JobIngestionServiceImpl(
                List.of(failingProvider, disabledProvider, partialProvider),
                jobNormalizer,
                deduplicationService,
                jobFetchLogRepository,
                properties
        );

        IngestionExecutionSummary summary = orchestrator.ingestAll(null);

        assertThat(summary.providersExecuted()).isEqualTo(2);
        assertThat(summary.totalInserted()).isEqualTo(1);
        assertThat(summary.totalFailedNormalization()).isEqualTo(1);
        assertThat(jobRepository.count()).isEqualTo(1);

        List<JobFetchLog> logs = jobFetchLogRepository.findAll();
        assertThat(logs).hasSize(2);
        assertThat(logs)
                .anySatisfy(log -> {
                    assertThat(log.getProviderName()).isEqualTo("BROKEN_PROVIDER");
                    assertThat(log.getStatus()).isEqualTo(FetchStatus.FAILED);
                    assertThat(log.getErrorMessage()).contains("HTTP 503 upstream timeout");
                })
                .anySatisfy(log -> {
                    assertThat(log.getProviderName()).isEqualTo("ADZUNA");
                    assertThat(log.getStatus()).isEqualTo(FetchStatus.PARTIAL_SUCCESS);
                    assertThat(log.getJobsInserted()).isEqualTo(1);
                });

        assertThatThrownBy(() -> orchestrator.ingestFromProvider("UNKNOWN", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private JobProvider stubProvider(String name, boolean enabled, List<RawJobPosting> items) {
        return new JobProvider() {
            @Override
            public String getProviderName() {
                return name;
            }

            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public List<RawJobPosting> fetchJobs(IngestionQuery query) {
                return items;
            }
        };
    }
}
