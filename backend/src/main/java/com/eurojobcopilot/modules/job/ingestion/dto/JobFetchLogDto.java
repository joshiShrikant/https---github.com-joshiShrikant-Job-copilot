package com.eurojobcopilot.modules.job.ingestion.dto;

import com.eurojobcopilot.modules.job.entity.FetchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Builder
@Schema(description = "Audit telemetry record for a single provider ingestion run")
public record JobFetchLogDto(
        @Schema(description = "Log database identifier", example = "101")
        Long id,

        @Schema(description = "Canonical provider name", example = "ADZUNA")
        String providerName,

        @Schema(description = "Execution status outcome", example = "SUCCESS")
        FetchStatus status,

        @Schema(description = "Raw postings fetched from upstream API", example = "50")
        int jobsFetched,

        @Schema(description = "New jobs inserted", example = "18")
        int jobsInserted,

        @Schema(description = "Existing jobs updated or enriched", example = "4")
        int jobsUpdated,

        @Schema(description = "Duplicate postings skipped", example = "28")
        int jobsSkippedDuplicates,

        @Schema(description = "Provider polling duration in milliseconds", example = "312")
        long executionDurationMs,

        @Schema(description = "Truncated upstream error or partial failure details when applicable")
        String errorMessage,

        @Schema(description = "UTC timestamp when the fetch started")
        Instant fetchedAt
) {
}

