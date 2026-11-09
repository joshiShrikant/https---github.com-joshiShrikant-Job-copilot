package com.eurojobcopilot.modules.interview.ai;

import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.interview.ai.RawAiInterviewSessionPayload.RawQuestion;
import com.eurojobcopilot.modules.interview.ai.RawAiInterviewSessionPayload.RawTopic;
import com.eurojobcopilot.modules.interview.ai.ValidatedInterviewSessionPayload.ValidatedQuestionItem;
import com.eurojobcopilot.modules.interview.dto.CompanyPrepTopicDto;
import com.eurojobcopilot.modules.interview.entity.QuestionCategory;
import com.eurojobcopilot.modules.interview.entity.QuestionDifficulty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
public class InterviewPrepValidator {

    private static final int MAX_QUESTIONS = 12;
    private static final int MAX_LIST_ITEMS = 6;

    public ValidatedInterviewSessionPayload validateSessionPayload(
            RawAiInterviewSessionPayload raw,
            InterviewSessionContextSnapshot context
    ) {
        if (raw == null || raw.questions() == null || raw.questions().isEmpty()) {
            throw new AiResponseValidationException("AI interview prep generator returned no questions.");
        }

        List<ValidatedQuestionItem> questions = new ArrayList<>();
        QuestionCategory defaultCategory = context.requestedCategories().isEmpty()
                ? QuestionCategory.TECHNICAL
                : context.requestedCategories().getFirst();
        QuestionDifficulty defaultDifficulty = context.targetDifficulty() != null
                ? context.targetDifficulty()
                : QuestionDifficulty.MID;

        for (RawQuestion q : raw.questions()) {
            if (questions.size() >= MAX_QUESTIONS) {
                break;
            }
            if (q == null || q.questionText() == null || q.questionText().trim().length() < 12) {
                continue;
            }
            String questionText = q.questionText().trim();
            String idealAnswer = q.idealAnswer() != null && q.idealAnswer().trim().length() >= 15
                    ? q.idealAnswer().trim()
                    : null;
            if (idealAnswer == null) {
                throw new AiResponseValidationException(
                        "AI generated an interview question without a substantive ideal answer."
                );
            }

            QuestionCategory category = parseCategory(q.category(), defaultCategory);
            QuestionDifficulty difficulty = parseDifficulty(q.difficulty(), defaultDifficulty);
            List<String> tags = sanitizeStringList(q.focusTags(), 5);

            questions.add(new ValidatedQuestionItem(category, difficulty, questionText, idealAnswer, tags));
        }

        if (questions.isEmpty()) {
            throw new AiResponseValidationException("AI interview prep payload contained no valid questions.");
        }

        String title;
        if (context.customSessionTitle() != null && !context.customSessionTitle().isBlank()) {
            title = context.customSessionTitle().trim();
        } else if (raw.sessionTitle() != null && !raw.sessionTitle().isBlank()) {
            title = raw.sessionTitle().trim();
        } else {
            title = context.jobTitle() + " · " + context.companyName() + " Prep";
        }
        if (title.length() > 195) {
            title = title.substring(0, 192) + "...";
        }

        List<CompanyPrepTopicDto> topics = new ArrayList<>();
        if (raw.companyPrepTopics() != null) {
            for (RawTopic t : raw.companyPrepTopics()) {
                if (t == null || t.topic() == null || t.topic().isBlank() || topics.size() >= MAX_LIST_ITEMS) {
                    continue;
                }
                topics.add(CompanyPrepTopicDto.builder()
                        .topic(t.topic().trim())
                        .whyRelevant(t.whyRelevant() != null ? t.whyRelevant().trim() : "Aligned with posted role expectations.")
                        .groundingBasis(t.groundingBasis() != null && !t.groundingBasis().isBlank()
                                ? t.groundingBasis().trim()
                                : "Verified job posting & employer profile")
                        .build());
            }
        }

        String overallAdvice = raw.overallPrepAdvice() != null ? raw.overallPrepAdvice().trim() : null;
        List<String> recommendations = sanitizeStringList(raw.improvementRecommendations(), MAX_LIST_ITEMS);

        return new ValidatedInterviewSessionPayload(title, topics, questions, overallAdvice, recommendations);
    }

    public ValidatedAnswerEvaluationPayload validateEvaluationPayload(RawAiAnswerEvaluationPayload raw) {
        if (raw == null || raw.score() == null || raw.aiFeedback() == null || raw.aiFeedback().trim().length() < 10) {
            throw new AiResponseValidationException("AI answer evaluator returned incomplete feedback or missing score.");
        }

        BigDecimal clamped = raw.score();
        if (clamped.compareTo(BigDecimal.ZERO) < 0) {
            clamped = BigDecimal.ZERO;
        } else if (clamped.compareTo(BigDecimal.valueOf(100)) > 0) {
            clamped = BigDecimal.valueOf(100);
        }
        clamped = clamped.setScale(2, RoundingMode.HALF_UP);

        return new ValidatedAnswerEvaluationPayload(
                clamped,
                raw.aiFeedback().trim(),
                sanitizeStringList(raw.strongPoints(), MAX_LIST_ITEMS),
                sanitizeStringList(raw.missingElements(), MAX_LIST_ITEMS),
                raw.sessionOverallFeedback() != null ? raw.sessionOverallFeedback().trim() : null,
                sanitizeStringList(raw.improvementRecommendations(), MAX_LIST_ITEMS)
        );
    }

    private QuestionCategory parseCategory(String raw, QuestionCategory fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String clean = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("JS".equals(clean)) return QuestionCategory.JAVASCRIPT;
        if ("TS".equals(clean)) return QuestionCategory.TYPESCRIPT;
        if (clean.contains("RXJS") || clean.contains("NGRX")) return QuestionCategory.RXJS_NGRX;
        if (clean.contains("LEAD")) return QuestionCategory.TECHNICAL_LEAD;
        if (clean.contains("SPRING")) return QuestionCategory.SPRING_BOOT;
        try {
            return QuestionCategory.valueOf(clean);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private QuestionDifficulty parseDifficulty(String raw, QuestionDifficulty fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String clean = raw.trim().toUpperCase(Locale.ROOT);
        if ("JUNIOR".equals(clean)) return QuestionDifficulty.ENTRY;
        if ("PRINCIPAL".equals(clean) || "LEAD".equals(clean)) return QuestionDifficulty.STAFF;
        try {
            return QuestionDifficulty.valueOf(clean);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private List<String> sanitizeStringList(List<String> input, int limit) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (input != null) {
            for (String item : input) {
                if (item != null && !item.isBlank() && set.size() < limit) {
                    set.add(item.trim());
                }
            }
        }
        return new ArrayList<>(set);
    }
}

