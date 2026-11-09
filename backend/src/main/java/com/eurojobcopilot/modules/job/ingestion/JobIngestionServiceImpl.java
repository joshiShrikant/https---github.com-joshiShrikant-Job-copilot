package com.eurojobcopilot.modules.job.ingestion;

import com.eurojobcopilot.core.exception.ResourceConflictException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.modules.job.entity.FetchStatus;
import com.eurojobcopilot.modules.job.entity.JobFetchLog;
import com.eurojobcopilot.modules.job.normalization.DeduplicationOutcome;
import com.eurojobcopilot.modules.job.normalization.DeduplicationService;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.normalization.NormalizedJobData;
import com.eurojobcopilot.modules.job.repository.JobFetchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fault-isolated orchestration service invoking registered {@link JobProvider} connectors,
 * running {@link JobNormalizer} and {@link DeduplicationService}, guarding concurrent runs,
 * and recording {@link JobFetchLog} telemetry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobIngestionServiceImpl implements JobIngestionService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final List<JobProvider> jobProviders;
    private final JobNormalizer jobNormalizer;
    private final DeduplicationService deduplicationService;
    private final JobFetchLogRepository jobFetchLogRepository;
    private final JobIngestionProperties properties;

    private final AtomicBoolean executionLock = new AtomicBoolean(false);
    private volatile String activeTarget;
    private volatile Instant lastRunStartedAt;
    private volatile Instant lastRunCompletedAt;
    private volatile IngestionExecutionSummary lastSummary;

    @Override
    public IngestionExecutionSummary ingestAll(IngestionQuery query) {
        if (!executionLock.compareAndSet(false, true)) {
            throw new ResourceConflictException("A job ingestion run is already in progress");
        }
        return runAllUnderLock(query);
    }

    @Override
    public Optional<IngestionExecutionSummary> tryScheduledIngestAll(IngestionQuery query) {
        if (!executionLock.compareAndSet(false, true)) {
            return Optional.empty();
        }
        return Optional.of(runAllUnderLock(query));
    }

    @Override
    public ProviderIngestionResult ingestFromProvider(String providerName, IngestionQuery query) {
        JobProvider provider = jobProviders.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Unknown job provider: " + providerName));

        if (!executionLock.compareAndSet(false, true)) {
            throw new ResourceConflictException("A job ingestion run is already in progress");
        }

        Instant startedAt = Instant.now();
        this.activeTarget = provider.getProviderName();
        this.lastRunStartedAt = startedAt;
        try {
            ProviderIngestionResult result = executeProvider(provider, resolveQuery(query));
            Instant completedAt = Instant.now();
            this.lastSummary = IngestionExecutionSummary.builder()
                    .runStartedAt(startedAt)
                    .runCompletedAt(completedAt)
                    .totalDurationMs(Duration.between(startedAt, completedAt).toMillis())
                    .providersExecuted(1)
                    .totalFetched(result.jobsFetched())
                    .totalInserted(result.jobsInserted())
                    .totalUpdated(result.jobsUpdated())
                    .totalSkippedDuplicates(result.jobsSkippedDuplicates())
                    .totalFailedNormalization(result.jobsFailedNormalization())
                    .providerResults(List.of(result))
                    .build();
            return result;
        } finally {
            this.lastRunCompletedAt = Instant.now();
            this.activeTarget = null;
            executionLock.set(false);
        }
    }

    @Override
    public boolean isRunning() {
        return executionLock.get();
    }

    @Override
    public IngestionRuntimeSnapshot getRuntimeSnapshot() {
        return IngestionRuntimeSnapshot.builder()
                .running(executionLock.get())
                .activeTarget(activeTarget)
                .lastRunStartedAt(lastRunStartedAt)
                .lastRunCompletedAt(lastRunCompletedAt)
                .lastSummary(lastSummary)
                .build();
    }

    @Override
    public List<ProviderRegistration> getRegisteredProviders() {
        return jobProviders.stream()
                .map(provider -> new ProviderRegistration(provider.getProviderName(), provider.isEnabled()))
                .toList();
    }

    private IngestionExecutionSummary runAllUnderLock(IngestionQuery query) {
        Instant startedAt = Instant.now();
        this.activeTarget = "ALL";
        this.lastRunStartedAt = startedAt;
        try {
            IngestionQuery effectiveQuery = resolveQuery(query);

            List<ProviderIngestionResult> results = new ArrayList<>();
            int totalFetched = 0;
            int totalInserted = 0;
            int totalUpdated = 0;
            int totalSkipped = 0;
            int totalFailedNorm = 0;

            for (JobProvider provider : jobProviders) {
                if (!provider.isEnabled()) {
                    log.debug("Skipping disabled job provider={}", provider.getProviderName());
                    continue;
                }
                ProviderIngestionResult result = executeProvider(provider, effectiveQuery);
                results.add(result);
                totalFetched += result.jobsFetched();
                totalInserted += result.jobsInserted();
                totalUpdated += result.jobsUpdated();
                totalSkipped += result.jobsSkippedDuplicates();
                totalFailedNorm += result.jobsFailedNormalization();
            }

            Instant completedAt = Instant.now();
            IngestionExecutionSummary summary = IngestionExecutionSummary.builder()
                    .runStartedAt(startedAt)
                    .runCompletedAt(completedAt)
                    .totalDurationMs(Duration.between(startedAt, completedAt).toMillis())
                    .providersExecuted(results.size())
                    .totalFetched(totalFetched)
                    .totalInserted(totalInserted)
                    .totalUpdated(totalUpdated)
                    .totalSkippedDuplicates(totalSkipped)
                    .totalFailedNormalization(totalFailedNorm)
                    .providerResults(results)
                    .build();
            this.lastSummary = summary;
            return summary;
        } finally {
            this.lastRunCompletedAt = Instant.now();
            this.activeTarget = null;
            executionLock.set(false);
        }
    }

    private ProviderIngestionResult executeProvider(JobProvider provider, IngestionQuery query) {
        Instant fetchedAt = Instant.now();
        long startNanos = System.nanoTime();
        String providerName = provider.getProviderName();

        int fetchedCount = 0;
        int insertedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        int failedNormCount = 0;
        FetchStatus status = FetchStatus.SUCCESS;
        String errorMessage = null;

        try {
            List<RawJobPosting> rawPostings = provider.fetchJobs(query);
            if (rawPostings != null) {
                fetchedCount = rawPostings.size();
                int limit = properties.getMaxResultsPerProvider() > 0
                        ? Math.min(rawPostings.size(), properties.getMaxResultsPerProvider())
                        : rawPostings.size();

                for (int i = 0; i < limit; i++) {
                    RawJobPosting raw = rawPostings.get(i);
                    try {
                        Optional<NormalizedJobData> normalizedOpt = jobNormalizer.normalize(raw);
                        if (normalizedOpt.isEmpty()) {
                            failedNormCount++;
                            continue;
                        }

                        DeduplicationOutcome outcome = deduplicationService.upsertNormalizedJob(normalizedOpt.get());
                        switch (outcome) {
                            case INSERTED -> insertedCount++;
                            case UPDATED -> updatedCount++;
                            case SKIPPED_DUPLICATE -> skippedCount++;
                        }
                    } catch (DataIntegrityViolationException ex) {
                        log.warn("Canonical hash race detected for provider={}: {}", providerName, ex.getMessage());
                        skippedCount++;
                    } catch (RuntimeException ex) {
                        log.warn("Failed to process individual job item for provider={}: {}", providerName, ex.getMessage());
                        failedNormCount++;
                    }
                }
            }

            if (failedNormCount > 0) {
                status = (insertedCount + updatedCount + skippedCount) > 0
                        ? FetchStatus.PARTIAL_SUCCESS
                        : FetchStatus.FAILED;
                errorMessage = failedNormCount + " raw job record(s) failed validation or normalization.";
            }
        } catch (RuntimeException ex) {
            status = FetchStatus.FAILED;
            errorMessage = truncateError(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            log.error("Job provider polling failed for provider={}: {}", providerName, errorMessage, ex);
        }

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;

        JobFetchLog logEntry = JobFetchLog.builder()
                .providerName(providerName)
                .status(status)
                .jobsFetched(fetchedCount)
                .jobsInserted(insertedCount)
                .jobsUpdated(updatedCount)
                .jobsSkippedDuplicates(skippedCount)
                .errorMessage(errorMessage)
                .executionDurationMs(durationMs)
                .fetchedAt(fetchedAt)
                .build();
        jobFetchLogRepository.save(logEntry);

        return ProviderIngestionResult.builder()
                .providerName(providerName)
                .status(status)
                .jobsFetched(fetchedCount)
                .jobsInserted(insertedCount)
                .jobsUpdated(updatedCount)
                .jobsSkippedDuplicates(skippedCount)
                .jobsFailedNormalization(failedNormCount)
                .executionDurationMs(durationMs)
                .errorMessage(errorMessage)
                .fetchedAt(fetchedAt)
                .build();
    }

    private IngestionQuery resolveQuery(IngestionQuery query) {
        if (query != null) {
            return query;
        }
        return IngestionQuery.defaults(properties.getTargetCountries(), properties.getDefaultMaxPages());
    }

    private String truncateError(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
