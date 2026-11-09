package com.eurojobcopilot.modules.job.ingestion;

import com.eurojobcopilot.modules.job.entity.FetchStatus;
import com.eurojobcopilot.modules.job.repository.JobFetchLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobIngestionSchedulerTest {

    @Mock
    private JobIngestionService jobIngestionService;

    @Mock
    private JobMaintenanceService jobMaintenanceService;

    @Mock
    private JobFetchLogRepository jobFetchLogRepository;

    private JobIngestionProperties properties;
    private JobIngestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new JobIngestionProperties();
        properties.setEnabled(true);
        properties.setDefaultMaxPages(2);
        properties.setTargetCountries(List.of("Germany", "Netherlands"));
        properties.getScheduler().setEnabled(true);
        properties.getScheduler().setStaleExpirationDays(45);
        properties.getScheduler().setLookbackHours(72);

        scheduler = new JobIngestionScheduler(
                jobIngestionService,
                jobMaintenanceService,
                properties,
                jobFetchLogRepository
        );
    }

    @Test
    void shouldSkipAllWorkWhenSchedulerOrGlobalIngestionDisabled() {
        properties.getScheduler().setEnabled(false);
        scheduler.runScheduledDiscovery();
        scheduler.runScheduledStaleJobCleanup();

        properties.getScheduler().setEnabled(true);
        properties.setEnabled(false);
        scheduler.runScheduledDiscovery();
        scheduler.runScheduledStaleJobCleanup();

        verify(jobIngestionService, never()).tryScheduledIngestAll(any());
        verify(jobMaintenanceService, never()).deactivateExpiredOrStaleJobs(45);
    }

    @Test
    void shouldPerformColdStartSeedWithoutSinceAndCleanupStaleJobs() {
        when(jobFetchLogRepository.existsByStatus(FetchStatus.SUCCESS)).thenReturn(false);
        when(jobIngestionService.tryScheduledIngestAll(any())).thenReturn(Optional.of(
                IngestionExecutionSummary.builder()
                        .providersExecuted(2)
                        .totalFetched(10)
                        .totalInserted(8)
                        .totalDurationMs(120)
                        .build()
        ));

        scheduler.runScheduledDiscovery();

        ArgumentCaptor<IngestionQuery> queryCaptor = ArgumentCaptor.forClass(IngestionQuery.class);
        verify(jobIngestionService).tryScheduledIngestAll(queryCaptor.capture());
        IngestionQuery captured = queryCaptor.getValue();

        assertThat(captured.countries()).containsExactly("Germany", "Netherlands");
        assertThat(captured.maxPages()).isEqualTo(2);
        assertThat(captured.since()).isNull();

        verify(jobMaintenanceService).deactivateExpiredOrStaleJobs(45);
    }

    @Test
    void shouldUseIncrementalSinceWatermarkWhenPriorSuccessLogExists() {
        when(jobFetchLogRepository.existsByStatus(FetchStatus.SUCCESS)).thenReturn(true);
        when(jobIngestionService.tryScheduledIngestAll(any())).thenReturn(Optional.of(
                IngestionExecutionSummary.builder().providersExecuted(1).build()
        ));

        Instant before = Instant.now();
        scheduler.runScheduledDiscovery();

        ArgumentCaptor<IngestionQuery> queryCaptor = ArgumentCaptor.forClass(IngestionQuery.class);
        verify(jobIngestionService).tryScheduledIngestAll(queryCaptor.capture());

        Instant since = queryCaptor.getValue().since();
        assertThat(since).isNotNull();
        assertThat(since).isBefore(before);
        verify(jobMaintenanceService).deactivateExpiredOrStaleJobs(45);
    }

    @Test
    void shouldSkipCleanupDuringDiscoveryIfConcurrentExecutionLockIsHeld() {
        when(jobFetchLogRepository.existsByStatus(FetchStatus.SUCCESS)).thenReturn(false);
        when(jobIngestionService.tryScheduledIngestAll(any())).thenReturn(Optional.empty());

        scheduler.runScheduledDiscovery();

        verify(jobMaintenanceService, never()).deactivateExpiredOrStaleJobs(45);
    }
}

