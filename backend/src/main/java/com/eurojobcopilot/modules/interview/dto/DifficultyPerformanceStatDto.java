package com.eurojobcopilot.modules.interview.dto;

import com.eurojobcopilot.modules.interview.entity.QuestionDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Interview practice performance breakdown by difficulty tier")
public class DifficultyPerformanceStatDto {

    private QuestionDifficulty difficulty;
    private int totalQuestions;
    private int answeredQuestions;
    private BigDecimal averageScore;
}

