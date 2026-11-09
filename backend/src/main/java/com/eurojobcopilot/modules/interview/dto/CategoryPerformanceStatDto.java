package com.eurojobcopilot.modules.interview.dto;

import com.eurojobcopilot.modules.interview.entity.QuestionCategory;
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
@Schema(description = "Interview practice performance breakdown for a single technical/behavioral category")
public class CategoryPerformanceStatDto {

    private QuestionCategory category;
    private int totalQuestions;
    private int answeredQuestions;
    private BigDecimal averageScore;
}

