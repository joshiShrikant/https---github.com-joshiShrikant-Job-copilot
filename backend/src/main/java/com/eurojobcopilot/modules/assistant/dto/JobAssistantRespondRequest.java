package com.eurojobcopilot.modules.assistant.dto;

import com.eurojobcopilot.modules.assistant.entity.AssistantActionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload invoking the contextual AI Application Assistant for a specific job")
public class JobAssistantRespondRequest {

    @NotNull(message = "Assistant actionType is required")
    @Schema(description = "Contextual assistant action", example = "EXPLAIN_MATCH_SCORE")
    private AssistantActionType actionType;

    @Size(max = 1500, message = "Prompt must not exceed 1500 characters")
    @Schema(description = "Optional custom question or refinement prompt (required when actionType is CUSTOM_QUESTION)")
    private String userPrompt;

    @Size(max = 1000, message = "Additional context must not exceed 1000 characters")
    @Schema(description = "Optional candidate notes, target salary expectations, or tone instructions")
    private String additionalContext;
}

