package com.eurojobcopilot.modules.assistant.ai;

import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.assistant.entity.AssistantActionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobAssistantValidatorTest {

    private final JobAssistantValidator validator = new JobAssistantValidator();

    @Test
    void validateAndNormalize_acceptsGroundedPayloadAndMergesHighlightsWarnings() {
        RawAiAssistantPayload raw = new RawAiAssistantPayload(
                "Match Breakdown for Adyen Senior Java Role",
                "Your 6 years of Java 21 and Spring Boot directly match the core backend requirement.",
                List.of("Java 21 & Spring Boot aligned", "Java 21 & Spring Boot aligned", "NL IND Verified Sponsor"),
                List.of("UserProfile"),
                List.of("Vacancy-level sponsorship must still be confirmed in screening")
        );

        ValidatedAssistantPayload result = validator.validateAndNormalize(raw, AssistantActionType.EXPLAIN_MATCH_SCORE);
        assertThat(result.assistantTitle()).isEqualTo("Match Breakdown for Adyen Senior Java Role");
        assertThat(result.highlights()).containsExactly(
                "Java 21 & Spring Boot aligned",
                "NL IND Verified Sponsor",
                "Note: Vacancy-level sponsorship must still be confirmed in screening"
        );
    }

    @Test
    void validateAndNormalize_rejectsEmptyOrTrivialContent() {
        assertThatThrownBy(() -> validator.validateAndNormalize(
                new RawAiAssistantPayload("Title", "Too short", List.of(), List.of(), List.of()),
                AssistantActionType.ANALYZE_JOB
        )).isInstanceOf(AiResponseValidationException.class);
    }
}

