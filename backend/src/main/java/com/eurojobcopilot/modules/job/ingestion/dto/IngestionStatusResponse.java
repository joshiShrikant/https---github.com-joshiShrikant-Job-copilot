package com.eurojobcopilot.modules.job.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
@Schema(description = "Current runtime health, scheduler configuration, and provider status of job ingestion")
public record IngestionStatusResponse(
        @Schema(description = "Global ingestion enabled flag", example = "true")
        boolean ingestionEnabled,

        @Schema(description = "Periodic scheduler enabled flag", example = "true")
        boolean schedulerEnabled,

        @Schema(description = "Active discovery cron expression", example = "0 0 */6 * * *")
        String cronExpression,

        @Schema(description = "Cron evaluation timezone", example = "UTC")
        String cronZone,

        @Schema(description = "Stale job expiration window in days", example = "45")
        int staleExpirationDays,

        @Schema(description = "Whether an ingestion batch is currently executing", example = "false")
        boolean running,

        @Schema(description = "Target currently executing (ALL or provider identifier), null when idle")
        String activeTarget,

        @Schema(description = "Timestamp when the most recent run began")
        Instant lastRunStartedAt,

        @Schema(description = "Timestamp when the most recent run completed")
        Instant lastRunCompletedAt,

        @Schema(description = "Count of active jobs currently indexed", example = "342")
        long activeJobsCount,

        @Schema(description = "Total jobs persisted across active and inactive states", example = "410")
        long totalJobsCount,

        @Schema(description = "Health and last run summary for each registered connector")
        List<ProviderHealthStatusDto> providers
) {
}

