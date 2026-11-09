package com.eurojobcopilot.modules.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated application count for a single Kanban pipeline stage")
public class PipelineStageCountDto {

    @Schema(description = "ApplicationStatus enum name", example = "APPLIED")
    private String status;

    @Schema(description = "Human-readable display label", example = "Applied")
    private String label;

    @Schema(description = "Number of user applications currently in this stage", example = "3")
    private long count;
}

