package com.eurojobcopilot.modules.matching.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable output produced by {@link DeterministicJobMatchingEngine} containing weighted composite
 * scores, all six 0..100 component scores, skill overlap lists, experience compatibility verdict,
 * and explainable recommendation reasons.
 */
public record DeterministicMatchResult(
        BigDecimal deterministicScore,
        BigDecimal finalScore,
        Integer matchPercentage,
        BigDecimal skillScore,
        BigDecimal experienceScore,
        BigDecimal titleScore,
        BigDecimal locationScore,
        BigDecimal sponsorshipScore,
        BigDecimal salaryScore,
        List<String> matchingSkills,
        List<String> missingSkills,
        String experienceCompatibility,
        List<String> recommendationReasons
) {
}

