package com.eurojobcopilot.modules.job.intelligence.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional parameters controlling AI job description analysis execution")
public class AnalyzeJobDescriptionRequest {

    @Schema(
            description = "Set to false to return an existing cached analysis if available; defaults to true to run a fresh AI extraction and append a new versioned history entry",
            example = "true"
    )
    private Boolean forceRefresh;
}

