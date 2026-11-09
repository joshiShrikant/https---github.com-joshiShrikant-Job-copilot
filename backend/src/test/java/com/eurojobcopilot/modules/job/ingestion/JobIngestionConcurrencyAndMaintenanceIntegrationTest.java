package com.eurojobcopilot.modules.job.ingestion;

import com.eurojobcopilot.core.exception.ResourceConflictException;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.normalization.DeduplicationService;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.repository.JobFetchLogRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class JobIngestionConcurrencyAndMaintenanceIntegrationTest {

    @Autowired
    private JobNormalizer jobNormalizer;

    @Autowired
    private DeduplicationService deduplicationService;

    @Autowired
    private JobFetchLogRepository jobFetchLogRepository;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobIngestionProperties properties;

    @Autowired
    private JobMaintenanceService jobMaintenanceService;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        jobFetchLogRepository.deleteAll();
        jobMatchScoreRepository.deleteAllInBatch();
        jobRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void shouldRejectOverlappingManualRunsAndSkipOverlappingScheduledRunsWhileLocked() throws Exception {
        CountDownLatch enteredFetch = new CountDownLatch(1);
        CountDownLatch releaseFetch = new CountDownLatch(1);

        JobProvider blockingProvider = new JobProvider() {
            @Override
            public String getProviderName() {
                return "SLOW_BOARD";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public List<RawJobPosting> fetchJobs(IngestionQuery query) {
                enteredFetch.countDown();
                try {
                    if (!releaseFetch.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for releaseLatch");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return List.of(RawJobPosting.builder()
                        .sourceProvider("SLOW_BOARD")
                        .sourceJobId("slow-1")
                        .title("Backend Java Developer")
                        .companyName("Test GmbH")
                        .country("Germany")
                        .city("Berlin")
                        .rawDescription("Java 21 microservices role.")
                        .originalUrl("https://example.com/slow-1")
                        .postedAt(Instant.now())
                        .build());
            }
        };

        JobIngestionServiceImpl orchestrator = new JobIngestionServiceImpl(
                List.of(blockingProvider),
                jobNormalizer,
                deduplicationService,
                jobFetchLogRepository,
                properties
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<IngestionExecutionSummary> activeFuture = executor.submit(() -> orchestrator.ingestAll(null));
            assertThat(enteredFetch.await(3, TimeUnit.SECONDS)).isTrue();

            assertThat(orchestrator.isRunning()).isTrue();
            assertThat(orchestrator.getRuntimeSnapshot().activeTarget()).isEqualTo("ALL");
            assertThat(orchestrator.tryScheduledIngestAll(null)).isEmpty();
            assertThatThrownBy(() -> orchestrator.ingestAll(null))
                    .isInstanceOf(ResourceConflictException.class)
                    .hasMessageContaining("already in progress");
            assertThatThrownBy(() -> orchestrator.ingestFromProvider("SLOW_BOARD", null))
                    .isInstanceOf(ResourceConflictException.class);

            releaseFetch.countDown();
            IngestionExecutionSummary summary = activeFuture.get(5, TimeUnit.SECONDS);

            assertThat(summary.totalInserted()).isEqualTo(1);
            assertThat(orchestrator.isRunning()).isFalse();
            assertThat(orchestrator.getRuntimeSnapshot().activeTarget()).isNull();
            assertThat(orchestrator.getRuntimeSnapshot().lastRunCompletedAt()).isNotNull();
            assertThat(orchestrator.tryScheduledIngestAll(null)).isPresent();
        } finally {
            releaseFetch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldDeactivateExpiredAndStaleJobsWhileKeepingFreshAndUnexpiredActive() {
        Instant now = Instant.now();

        Job freshUndated = jobRepository.save(Job.builder()
                .canonicalHash("hash-fresh-undated-1")
                .companyName("Fresh Corp")
                .title("Senior Java Engineer")
                .normalizedTitle("senior java engineer")
                .country("Germany")
                .description("Fresh job")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://example.com/1")
                .postedAt(now.minus(5, ChronoUnit.DAYS))
                .active(true)
                .build());

        Job futureExpiry = jobRepository.save(Job.builder()
                .canonicalHash("hash-future-exp-2")
                .companyName("Future Corp")
                .title("Angular Engineer")
                .normalizedTitle("angular engineer")
                .country("Netherlands")
                .description("Valid explicit expiry")
                .sourceProvider("ADZUNA")
                .originalUrl("https://example.com/2")
                .postedAt(now.minus(60, ChronoUnit.DAYS))
                .expiresAt(now.plus(5, ChronoUnit.DAYS))
                .active(true)
                .build());

        Job explicitExpired = jobRepository.save(Job.builder()
                .canonicalHash("hash-expired-3")
                .companyName("Expired Corp")
                .title("Expired Role")
                .normalizedTitle("expired role")
                .country("Germany")
                .description("Explicitly expired")
                .sourceProvider("ADZUNA")
                .originalUrl("https://example.com/3")
                .postedAt(now.minus(3, ChronoUnit.DAYS))
                .expiresAt(now.minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());

        Job staleUndated = jobRepository.save(Job.builder()
                .canonicalHash("hash-stale-undated-4")
                .companyName("Stale Corp")
                .title("Old Role")
                .normalizedTitle("old role")
                .country("France")
                .description("Stale 60 days old")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://example.com/4")
                .postedAt(now.minus(60, ChronoUnit.DAYS))
                .active(true)
                .build());

        int deactivated = jobMaintenanceService.deactivateExpiredOrStaleJobs(45);
        assertThat(deactivated).isEqualTo(2);

        assertThat(jobRepository.findById(freshUndated.getId()).orElseThrow().isActive()).isTrue();
        assertThat(jobRepository.findById(futureExpiry.getId()).orElseThrow().isActive()).isTrue();
        assertThat(jobRepository.findById(explicitExpired.getId()).orElseThrow().isActive()).isFalse();
        assertThat(jobRepository.findById(staleUndated.getId()).orElseThrow().isActive()).isFalse();
        assertThat(jobRepository.countByActiveTrue()).isEqualTo(2);
    }
}

