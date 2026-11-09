package com.eurojobcopilot.modules.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chronological score point representing one interview prep session")
public class InterviewSessionScorePointDto {

    private Long sessionId;
    private String sessionTitle;
    private String companyName;
    private BigDecimal score;
    private int answeredQuestions;
    private int totalQuestions;
    private Instant createdAt;
}

