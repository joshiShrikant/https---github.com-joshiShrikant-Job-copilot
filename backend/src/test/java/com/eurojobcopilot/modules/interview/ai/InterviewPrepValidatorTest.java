package com.eurojobcopilot.modules.interview.ai;

import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.interview.ai.RawAiInterviewSessionPayload.RawQuestion;
import com.eurojobcopilot.modules.interview.ai.RawAiInterviewSessionPayload.RawTopic;
import com.eurojobcopilot.modules.interview.entity.QuestionCategory;
import com.eurojobcopilot.modules.interview.entity.QuestionDifficulty;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewPrepValidatorTest {

    private final InterviewPrepValidator validator = new InterviewPrepValidator();

    @Test
    void validateSessionPayload_normalizesCategoriesDifficultiesAndGroundedTopics() {
        InterviewSessionContextSnapshot ctx = new InterviewSessionContextSnapshot(
                1L, 10L, null,
                List.of(QuestionCategory.ANGULAR, QuestionCategory.JAVA),
                QuestionDifficulty.SENIOR, 4, null,
                "Senior Fullstack", "Adyen", "FinTech", "https://adyen.com", true,
                "Netherlands", "Amsterdam", "SENIOR", List.of("Angular", "Java"),
                "Build payment dashboards", null, List.of(), List.of(),
                "Senior Engineer", new BigDecimal("6.0"), List.of("Angular"), List.of(), null
        );

        RawAiInterviewSessionPayload raw = new RawAiInterviewSessionPayload(
                "Adyen Senior Frontend & Java Prep",
                List.of(new RawTopic("Idempotent Payment APIs", "FinTech core workflow", "Job description + FinTech domain")),
                List.of(
                        new RawQuestion("RXJS_NGRX", "SENIOR", "How do you prevent duplicate payment submissions with RxJS switchMap vs exhaustMap?",
                                "Use exhaustMap on submit triggers so concurrent clicks are ignored until completion.", List.of("RxJS", "Angular")),
                        new RawQuestion("JAVA", "STAFF", "Explain virtual threads vs reactive Spring WebFlux for high-throughput gateways.",
                                "Virtual threads simplify imperative blocking I/O while non-blocking pools guard JDBC limits.", List.of("Java 21"))
                ),
                "Focus on payment safety and Signals state transitions.",
                List.of("Review RxJS flattening operators")
        );

        ValidatedInterviewSessionPayload validated = validator.validateSessionPayload(raw, ctx);
        assertThat(validated.sessionTitle()).isEqualTo("Adyen Senior Frontend & Java Prep");
        assertThat(validated.companyPrepTopics()).singleElement()
                .satisfies(t -> assertThat(t.getGroundingBasis()).contains("Job description"));
        assertThat(validated.questions()).hasSize(2);
        assertThat(validated.questions().get(0).category()).isEqualTo(QuestionCategory.RXJS_NGRX);
        assertThat(validated.questions().get(1).difficulty()).isEqualTo(QuestionDifficulty.STAFF);
    }

    @Test
    void validateEvaluationPayload_clampsScoreAndRequiresFeedback() {
        ValidatedAnswerEvaluationPayload clamped = validator.validateEvaluationPayload(new RawAiAnswerEvaluationPayload(
                new BigDecimal("108.4"),
                "Comprehensive explanation covering exhaustMap guarantees.",
                List.of("Exact RxJS operator choice"),
                List.of("Mention idempotency key header"),
                "Strong overall round readiness.",
                List.of("Add idempotency header examples")
        ));
        assertThat(clamped.score()).isEqualByComparingTo("100.00");

        assertThatThrownBy(() -> validator.validateEvaluationPayload(
                new RawAiAnswerEvaluationPayload(null, "short", List.of(), List.of(), null, List.of())
        )).isInstanceOf(AiResponseValidationException.class);
    }
}

