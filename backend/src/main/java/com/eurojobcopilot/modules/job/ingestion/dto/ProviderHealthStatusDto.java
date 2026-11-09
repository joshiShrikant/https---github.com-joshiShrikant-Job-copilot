package com.eurojobcopilot.modules.job.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Configured state and latest fetch telemetry for a registered job provider")
public record ProviderHealthStatusDto(
        @Schema(description = "Provider identifier", example = "ADZUNA")
        String providerName,

        @Schema(description = "Whether the connector is enabled and properly configured", example = "true")
        boolean enabled,

        @Schema(description = "Most recent recorded fetch log for this provider, if any")
        JobFetchLogDto lastRun
) {
}

