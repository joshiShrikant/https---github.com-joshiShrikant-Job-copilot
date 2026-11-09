package com.eurojobcopilot.modules.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update payload saving the candidate's edited version of an AI Application Assistant response")
public class UpdateJobAssistantMessageRequest {

    @NotBlank(message = "Edited response content must not be blank")
    @Size(max = 20000, message = "Edited response content must not exceed 20000 characters")
    @Schema(description = "Candidate-edited draft or assistant output persisted with the job")
    private String editableContent;
}

