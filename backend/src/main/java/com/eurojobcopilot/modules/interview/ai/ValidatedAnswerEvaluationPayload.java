package com.eurojobcopilot.modules.interview.ai;

import java.math.BigDecimal;
import java.util.List;

public record ValidatedAnswerEvaluationPayload(
        BigDecimal score,
        String aiFeedback,
        List<String> strongPoints,
        List<String> missingElements,
        String sessionOverallFeedback,
        List<String> improvementRecommendations
) {
}

