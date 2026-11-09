package com.eurojobcopilot.modules.matching.ai.engine;

import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.matching.ai.model.RawAiSemanticMatchPayload;
import com.eurojobcopilot.modules.matching.ai.model.ValidatedSemanticMatchResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Enforces structural, score-bound, length, and substance validation on AI semantic matching
 * responses before persisting or blending with deterministic compatibility scores.
 */
@Component
public class SemanticMatchValidator {

    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = new BigDecimal("100.00");
    private static final int MAX_RAW_LIST_ITEMS = 40;
    private static final int MAX_LIST_ITEMS = 20;
    private static final int MAX_LIST_ITEM_LENGTH = 400;
    private static final int MAX_RATIONALE_LENGTH = 4000;
    private static final int MIN_RATIONALE_LENGTH = 10;

    private static final Pattern INLINE_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EXCESS_NEWLINES = Pattern.compile("\\n{3,}");

    public ValidatedSemanticMatchResult validate(RawAiSemanticMatchPayload raw) {
        if (raw == null) {
            throw new AiResponseValidationException("AI returned a null semantic match payload.");
        }

        BigDecimal aiScore = validateScore(raw.getAiScore());
        List<String> strongMatches = sanitizeStringList("strongMatchingAreas", raw.getStrongMatchingAreas());
        List<String> missingSkills = sanitizeStringList("missingSkills", raw.getMissingSkills());
        List<String> experienceGaps = sanitizeStringList("experienceGaps", raw.getExperienceGaps());
        List<String> potentialConcerns = sanitizeStringList("potentialConcerns", raw.getPotentialConcerns());
        List<String> improvementSuggestions = sanitizeStringList("improvementSuggestions", raw.getImprovementSuggestions());
        String rationale = cleanProse(raw.getRationale(), MAX_RATIONALE_LENGTH);

        if (rationale == null || rationale.length() < MIN_RATIONALE_LENGTH) {
            throw new AiResponseValidationException(
                    "AI semantic matching failed validation: rationale must be a substantive explanation."
            );
        }

        int totalInsights = strongMatches.size()
                + missingSkills.size()
                + experienceGaps.size()
                + potentialConcerns.size()
                + improvementSuggestions.size();
        if (totalInsights == 0) {
            throw new AiResponseValidationException(
                    "AI semantic matching failed validation: response contained no strong matches, gaps, concerns, or improvement suggestions."
            );
        }

        return new ValidatedSemanticMatchResult(
                aiScore,
                strongMatches,
                missingSkills,
                experienceGaps,
                potentialConcerns,
                improvementSuggestions,
                rationale
        );
    }

    private BigDecimal validateScore(BigDecimal rawScore) {
        if (rawScore == null) {
            throw new AiResponseValidationException("AI semantic matching failed validation: aiScore is required.");
        }
        if (rawScore.compareTo(MIN_SCORE) < 0 || rawScore.compareTo(MAX_SCORE) > 0) {
            throw new AiResponseValidationException(
                    "AI semantic matching failed validation: aiScore must be between 0 and 100 (got " + rawScore + ")."
            );
        }
        return rawScore.setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> sanitizeStringList(String fieldName, List<String> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            return List.of();
        }
        if (rawItems.size() > MAX_RAW_LIST_ITEMS) {
            throw new AiResponseValidationException(
                    "AI semantic matching failed validation: field '" + fieldName
                            + "' exceeded maximum item count (" + rawItems.size() + ")."
            );
        }

        Map<String, String> unique = new LinkedHashMap<>();
        for (String item : rawItems) {
            String cleaned = cleanSingleLine(item, MAX_LIST_ITEM_LENGTH);
            if (cleaned != null) {
                unique.putIfAbsent(cleaned.toLowerCase(Locale.ROOT), cleaned);
                if (unique.size() >= MAX_LIST_ITEMS) {
                    break;
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private String cleanSingleLine(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String collapsed = INLINE_WHITESPACE.matcher(raw).replaceAll(" ").trim();
        return collapsed.isEmpty() ? null : truncate(collapsed, maxLength);
    }

    private String cleanProse(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.replace("\r\n", "\n").trim();
        normalized = EXCESS_NEWLINES.matcher(normalized).replaceAll("\n\n");
        return normalized.isEmpty() ? null : truncate(normalized, maxLength);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}

