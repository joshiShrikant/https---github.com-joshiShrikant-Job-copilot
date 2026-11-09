package com.eurojobcopilot.modules.job.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Builder
@Schema(description = "Outcome of an expired and stale job deactivation sweep")
public record StaleJobCleanupResponse(
        @Schema(description = "Number of expired or stale jobs deactivated", example = "14")
        int deactivatedCount,

        @Schema(description = "Stale threshold window applied in days", example = "45")
        int staleExpirationDays,

        @Schema(description = "UTC timestamp when cleanup completed")
        Instant executedAt
) {
}

