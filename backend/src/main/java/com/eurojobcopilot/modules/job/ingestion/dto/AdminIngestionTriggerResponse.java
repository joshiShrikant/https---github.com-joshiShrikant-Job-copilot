package com.eurojobcopilot.modules.job.ingestion.dto;

import com.eurojobcopilot.modules.job.ingestion.ProviderIngestionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
@Schema(description = "Summary report produced by a manual job ingestion execution")
public record AdminIngestionTriggerResponse(
        @Schema(description = "Execution scope (ALL or a specific provider identifier)", example = "ALL")
        String scope,

        @Schema(description = "UTC timestamp when execution began")
        Instant runStartedAt,

        @Schema(description = "UTC timestamp when execution completed")
        Instant runCompletedAt,

        @Schema(description = "Total execution duration in milliseconds", example = "420")
        long totalDurationMs,

        @Schema(description = "Number of enabled providers executed", example = "2")
        int providersExecuted,

        @Schema(description = "Total raw postings returned by upstream providers", example = "120")
        int totalFetched,

        @Schema(description = "Total new canonical jobs inserted into database", example = "34")
        int totalInserted,

        @Schema(description = "Total existing jobs updated or cross-provider enriched", example = "11")
        int totalUpdated,

        @Schema(description = "Total duplicate postings skipped", example = "75")
        int totalSkippedDuplicates,

        @Schema(description = "Total raw postings rejected by normalization", example = "0")
        int totalFailedNormalization,

        @Schema(description = "Per-provider telemetry details")
        List<ProviderIngestionResult> providerResults
) {
}

