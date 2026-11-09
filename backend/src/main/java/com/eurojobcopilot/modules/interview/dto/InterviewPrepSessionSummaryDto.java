package com.eurojobcopilot.modules.interview.dto;

import com.eurojobcopilot.modules.interview.entity.QuestionDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary of a generated interview preparation session")
public class InterviewPrepSessionSummaryDto {

    private Long id;
    private Long jobId;
    private String jobTitle;
    private String companyName;
    private String country;
    private String sessionTitle;
    private List<String> targetCategories;
    private QuestionDifficulty targetDifficulty;
    private int totalQuestions;
    private int answeredQuestions;
    private BigDecimal score;
    private Instant createdAt;
    private Instant updatedAt;
}

