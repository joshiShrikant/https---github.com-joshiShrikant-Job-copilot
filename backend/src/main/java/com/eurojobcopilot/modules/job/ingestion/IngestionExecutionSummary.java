package com.eurojobcopilot.modules.job.ingestion;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate batch report returned after orchestrating one or more job provider runs.
 */
@Builder
public record IngestionExecutionSummary(
        Instant runStartedAt,
        Instant runCompletedAt,
        long totalDurationMs,
        int providersExecuted,
        int totalFetched,
        int totalInserted,
        int totalUpdated,
        int totalSkippedDuplicates,
        int totalFailedNormalization,
        List<ProviderIngestionResult> providerResults
) {
    public IngestionExecutionSummary {
        providerResults = providerResults == null ? List.of() : List.copyOf(providerResults);
    }
}

