package com.eurojobcopilot.modules.interview.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawAiInterviewSessionPayload(
        String sessionTitle,
        List<RawTopic> companyPrepTopics,
        List<RawQuestion> questions,
        String overallPrepAdvice,
        List<String> improvementRecommendations
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawTopic(
            String topic,
            String whyRelevant,
            String groundingBasis
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawQuestion(
            String category,
            String difficulty,
            String questionText,
            String idealAnswer,
            List<String> focusTags
    ) {
    }
}

