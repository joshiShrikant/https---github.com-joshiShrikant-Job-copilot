package com.eurojobcopilot.modules.matching.ai.engine;

import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.matching.ai.model.RawAiSemanticMatchPayload;
import com.eurojobcopilot.modules.matching.ai.model.ValidatedSemanticMatchResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticMatchValidatorTest {

    private final SemanticMatchValidator validator = new SemanticMatchValidator();

    @Test
    void validate_sanitizesDeduplicatesAndRoundsValidPayload() {
        RawAiSemanticMatchPayload raw = RawAiSemanticMatchPayload.builder()
                .aiScore(new BigDecimal("86.345"))
                .strongMatchingAreas(List.of("  Java 21   & Spring Boot payments backend ", "java 21 & spring boot payments backend", "   "))
                .missingSkills(List.of("Kafka", "Kubernetes"))
                .experienceGaps(List.of("Limited multi-region failover ownership"))
                .potentialConcerns(List.of("Requires visa sponsorship in NL"))
                .improvementSuggestions(List.of("Highlight TPS metrics on master resume"))
                .rationale("Candidate aligns strongly on JVM core services.\r\n\r\n\r\nKafka and sponsorship risk remain.")
                .build();

        ValidatedSemanticMatchResult result = validator.validate(raw);

        assertThat(result.aiScore()).isEqualByComparingTo("86.35");
        assertThat(result.strongMatchingAreas()).containsExactly("Java 21 & Spring Boot payments backend");
        assertThat(result.missingSkills()).containsExactly("Kafka", "Kubernetes");
        assertThat(result.experienceGaps()).containsExactly("Limited multi-region failover ownership");
        assertThat(result.potentialConcerns()).containsExactly("Requires visa sponsorship in NL");
        assertThat(result.improvementSuggestions()).containsExactly("Highlight TPS metrics on master resume");
        assertThat(result.rationale()).doesNotContain("\r\n");
    }

    @Test
    void validate_rejectsOutOfRangeScoreSubstancelessRationaleOrEmptyFeedbackLists() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(AiResponseValidationException.class);

        RawAiSemanticMatchPayload negativeScore = RawAiSemanticMatchPayload.builder()
                .aiScore(new BigDecimal("-1"))
                .strongMatchingAreas(List.of("Java"))
                .rationale("Valid rationale text over ten chars.")
                .build();
        assertThatThrownBy(() -> validator.validate(negativeScore))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("between 0 and 100");

        RawAiSemanticMatchPayload excessiveScore = RawAiSemanticMatchPayload.builder()
                .aiScore(new BigDecimal("101.5"))
                .strongMatchingAreas(List.of("Java"))
                .rationale("Valid rationale text over ten chars.")
                .build();
        assertThatThrownBy(() -> validator.validate(excessiveScore))
                .isInstanceOf(AiResponseValidationException.class);

        RawAiSemanticMatchPayload shortRationale = RawAiSemanticMatchPayload.builder()
                .aiScore(new BigDecimal("75"))
                .strongMatchingAreas(List.of("Java"))
                .rationale("Short")
                .build();
        assertThatThrownBy(() -> validator.validate(shortRationale))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("rationale");

        RawAiSemanticMatchPayload emptyLists = RawAiSemanticMatchPayload.builder()
                .aiScore(new BigDecimal("75"))
                .rationale("Candidate profile has some alignment with the target engineering position.")
                .build();
        assertThatThrownBy(() -> validator.validate(emptyLists))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("no strong matches, gaps, concerns, or improvement suggestions");

        List<String> oversized = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            oversized.add("Skill-" + i);
        }
        RawAiSemanticMatchPayload tooManyItems = RawAiSemanticMatchPayload.builder()
                .aiScore(new BigDecimal("80"))
                .missingSkills(oversized)
                .rationale("Detailed rationale evaluating the role gaps cleanly.")
                .build();
        assertThatThrownBy(() -> validator.validate(tooManyItems))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("exceeded maximum item count");
    }
}

