package com.eurojobcopilot.modules.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cross-session interview preparation performance analytics and targeted improvement advice")
public class InterviewPerformanceHistoryResponse {

    private int totalSessions;
    private int totalQuestions;
    private int totalAnsweredQuestions;
    private BigDecimal averageScore;
    private List<CategoryPerformanceStatDto> categoryStats;
    private List<DifficultyPerformanceStatDto> difficultyStats;
    private List<InterviewSessionScorePointDto> recentScoreTrend;
    private List<String> weakestCategories;
    private List<String> improvementRecommendations;
    private List<InterviewPrepSessionSummaryDto> sessions;
}

