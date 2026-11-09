package com.eurojobcopilot.modules.job.ingestion;

import com.eurojobcopilot.modules.job.entity.FetchStatus;
import lombok.Builder;

import java.time.Instant;

/**
 * Telemetry report captured for a single provider polling execution.
 */
@Builder
public record ProviderIngestionResult(
        String providerName,
        FetchStatus status,
        int jobsFetched,
        int jobsInserted,
        int jobsUpdated,
        int jobsSkippedDuplicates,
        int jobsFailedNormalization,
        long executionDurationMs,
        String errorMessage,
        Instant fetchedAt
) {
}

