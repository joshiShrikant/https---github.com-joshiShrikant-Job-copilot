package com.eurojobcopilot.modules.matching.ai.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Strictly validated semantic match result safe to persist onto {@code job_match_scores}.
 */
public record ValidatedSemanticMatchResult(
        BigDecimal aiScore,
        List<String> strongMatchingAreas,
        List<String> missingSkills,
        List<String> experienceGaps,
        List<String> potentialConcerns,
        List<String> improvementSuggestions,
        String rationale
) {
}

