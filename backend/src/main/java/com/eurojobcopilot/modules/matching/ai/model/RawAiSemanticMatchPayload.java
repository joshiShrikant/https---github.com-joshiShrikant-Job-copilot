package com.eurojobcopilot.modules.matching.ai.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Unvalidated deserialization target for structured AI semantic matching JSON responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawAiSemanticMatchPayload {

    @JsonAlias({"overallScore", "semanticScore", "score"})
    private BigDecimal aiScore;

    @JsonAlias({"strongMatches", "strong_matching_areas", "strengths"})
    private List<String> strongMatchingAreas;

    @JsonAlias({"missing_skills", "skillGaps"})
    private List<String> missingSkills;

    @JsonAlias({"experience_gaps"})
    private List<String> experienceGaps;

    @JsonAlias({"potential_concerns", "concerns", "redFlags"})
    private List<String> potentialConcerns;

    @JsonAlias({"improvement_suggestions", "suggestions", "recommendations"})
    private List<String> improvementSuggestions;

    @JsonAlias({"summary", "reasoning", "aiRationale"})
    private String rationale;
}

