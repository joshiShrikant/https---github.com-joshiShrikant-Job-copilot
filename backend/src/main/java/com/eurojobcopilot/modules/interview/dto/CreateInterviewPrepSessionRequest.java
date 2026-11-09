package com.eurojobcopilot.modules.interview.dto;

import com.eurojobcopilot.modules.interview.entity.QuestionCategory;
import com.eurojobcopilot.modules.interview.entity.QuestionDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to generate a role-tailored AI interview preparation session")
public class CreateInterviewPrepSessionRequest {

    @NotNull(message = "jobId is required")
    @Positive(message = "jobId must be a positive integer")
    @Schema(description = "Target job identifier", example = "1042")
    private Long jobId;

    @Size(max = 200, message = "Session title must not exceed 200 characters")
    @Schema(description = "Optional custom session title")
    private String sessionTitle;

    @Schema(description = "Requested question categories (defaults to stack-appropriate categories when omitted)")
    private List<QuestionCategory> categories;

    @Schema(description = "Target question difficulty level (inferred from role seniority when omitted)", example = "SENIOR")
    private QuestionDifficulty difficulty;

    @Builder.Default
    @Min(value = 3, message = "questionCount must be at least 3")
    @Max(value = 12, message = "questionCount must not exceed 12")
    @Schema(description = "Number of practice questions to generate (3..12)", example = "6")
    private Integer questionCount = 6;

    @Size(max = 1000, message = "focusNotes must not exceed 1000 characters")
    @Schema(description = "Optional candidate notes or specific topics to emphasize (e.g. Angular Signals + Spring Boot security)")
    private String focusNotes;
}

