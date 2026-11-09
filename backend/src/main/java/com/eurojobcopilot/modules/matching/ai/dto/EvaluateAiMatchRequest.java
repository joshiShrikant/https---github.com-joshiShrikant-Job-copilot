package com.eurojobcopilot.modules.matching.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional control payload when triggering AI semantic job match evaluation")
public class EvaluateAiMatchRequest {

    @Builder.Default
    @Schema(
            description = "When false, returns the existing persisted AI semantic comparison if available instead of re-invoking the AI provider",
            example = "true",
            defaultValue = "true"
    )
    private Boolean forceRefresh = Boolean.TRUE;
}

