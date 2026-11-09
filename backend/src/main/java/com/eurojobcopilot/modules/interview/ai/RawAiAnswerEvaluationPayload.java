package com.eurojobcopilot.modules.interview.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawAiAnswerEvaluationPayload(
        BigDecimal score,
        String aiFeedback,
        List<String> strongPoints,
        List<String> missingElements,
        String sessionOverallFeedback,
        List<String> improvementRecommendations
) {
}

