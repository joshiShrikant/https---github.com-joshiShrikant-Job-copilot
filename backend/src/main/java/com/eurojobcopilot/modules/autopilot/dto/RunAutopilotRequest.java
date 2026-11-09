package com.eurojobcopilot.modules.autopilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Options for manually triggering an autopilot job discovery and matching scan")
public class RunAutopilotRequest {

    @Builder.Default
    @Schema(description = "Attempt external job connector refresh if cooldown permits and ingestion is enabled", example = "false")
    private boolean refreshExternalJobs = false;

    @Builder.Default
    @Schema(description = "Optionally invoke AI semantic matching on top high-match jobs when AI is enabled", example = "false")
    private boolean analyzeTopMatchesWithAi = false;
}

