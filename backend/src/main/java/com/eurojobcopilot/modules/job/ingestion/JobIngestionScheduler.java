package com.eurojobcopilot.modules.job.ingestion;

import com.eurojobcopilot.modules.job.entity.FetchStatus;
import com.eurojobcopilot.modules.job.repository.JobFetchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Periodic cron engine orchestrating multi-provider job discovery and stale listing cleanup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobIngestionScheduler {

    private static final List<String> DEFAULT_DISCOVERY_KEYWORDS = List.of(
            "Java", "Spring", "Angular", "Backend", "Full Stack", "Software Engineer"
    );

    private final JobIngestionService jobIngestionService;
    private final JobMaintenanceService jobMaintenanceService;
    private final JobIngestionProperties properties;
    private final JobFetchLogRepository jobFetchLogRepository;

    @Scheduled(
            cron = "${app.ingestion.scheduler.cron:0 0 */6 * * *}",
            zone = "${app.ingestion.scheduler.zone:UTC}"
    )
    public void runScheduledDiscovery() {
        if (!isSchedulerActive()) {
            return;
        }

        Instant sinceWatermark = resolveIncrementalSince();
        IngestionQuery query = IngestionQuery.builder()
                .countries(properties.getTargetCountries())
                .keywords(DEFAULT_DISCOVERY_KEYWORDS)
                .maxPages(properties.getDefaultMaxPages())
                .pageSize(50)
                .remoteOnly(false)
                .since(sinceWatermark)
                .build();

        try {
            Optional<IngestionExecutionSummary> summaryOpt = jobIngestionService.tryScheduledIngestAll(query);
            if (summaryOpt.isEmpty()) {
                log.warn("Skipping scheduled job discovery run because another ingestion execution is already active");
                return;
            }

            IngestionExecutionSummary summary = summaryOpt.get();
            log.info(
                    "Scheduled job discovery completed: providers={} fetched={} inserted={} updated={} skipped={} durationMs={}",
                    summary.providersExecuted(),
                    summary.totalFetched(),
                    summary.totalInserted(),
                    summary.totalUpdated(),
                    summary.totalSkippedDuplicates(),
                    summary.totalDurationMs()
            );
        } catch (RuntimeException ex) {
            log.error("Scheduled job discovery encountered an unexpected error: {}", ex.getMessage(), ex);
        }

        cleanupStaleSafely();
    }

    @Scheduled(
            cron = "${app.ingestion.scheduler.expiration-cron:0 30 2 * * *}",
            zone = "${app.ingestion.scheduler.zone:UTC}"
    )
    public void runScheduledStaleJobCleanup() {
        if (!isSchedulerActive()) {
            return;
        }
        cleanupStaleSafely();
    }

    private boolean isSchedulerActive() {
        return properties.isEnabled()
                && properties.getScheduler() != null
                && properties.getScheduler().isEnabled();
    }

    private Instant resolveIncrementalSince() {
        int lookbackHours = properties.getScheduler().getLookbackHours();
        if (lookbackHours <= 0) {
            return null;
        }
        if (!jobFetchLogRepository.existsByStatus(FetchStatus.SUCCESS)) {
            return null;
        }
        return Instant.now().minus(lookbackHours, ChronoUnit.HOURS);
    }

    private void cleanupStaleSafely() {
        try {
            int staleDays = properties.getScheduler().getStaleExpirationDays();
            jobMaintenanceService.deactivateExpiredOrStaleJobs(staleDays);
        } catch (RuntimeException ex) {
            log.error("Scheduled stale job cleanup failed: {}", ex.getMessage(), ex);
        }
    }
}

