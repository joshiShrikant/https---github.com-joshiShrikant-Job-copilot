package com.eurojobcopilot.modules.job.ingestion;

import lombok.Builder;

import java.time.Instant;

/**
 * In-memory snapshot of active/recent ingestion execution state.
 */
@Builder
public record IngestionRuntimeSnapshot(
        boolean running,
        String activeTarget,
        Instant lastRunStartedAt,
        Instant lastRunCompletedAt,
        IngestionExecutionSummary lastSummary
) {
}

