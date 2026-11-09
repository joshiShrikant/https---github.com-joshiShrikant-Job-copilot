package com.eurojobcopilot.modules.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Candidate practice answer submission for AI grading and coaching feedback")
public class SubmitInterviewAnswerRequest {

    @NotNull(message = "questionId is required")
    @Positive(message = "questionId must be a positive integer")
    @Schema(description = "Identifier of the interview question being answered", example = "101")
    private Long questionId;

    @NotBlank(message = "userAnswer must not be blank")
    @Size(max = 8000, message = "userAnswer must not exceed 8000 characters")
    @Schema(description = "Candidate's practice answer to be graded by the AI interviewer")
    private String userAnswer;
}

