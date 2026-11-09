package com.eurojobcopilot.modules.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Job board / discovery connector coverage and refresh telemetry")
public class SourceCoverageDto {

    @Schema(example = "ARBEITNOW")
    private String sourceProvider;

    private long activeJobsCount;

    @Schema(description = "Percentage share of collected active jobs from this provider", example = "62.5")
    private BigDecimal sharePercentage;

    private Instant lastFetchAt;

    @Schema(example = "SUCCESS")
    private String lastFetchStatus;

    private long totalRunsLogged;
}

