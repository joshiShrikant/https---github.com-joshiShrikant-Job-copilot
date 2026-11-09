package com.eurojobcopilot.modules.analytics.service;

import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic classifier and annual-compensation normalizer over ingested jobs.
 */
@Component
public class MarketJobClassifier {

    public enum EnglishLanguageStatus {
        CONFIRMED,
        LOCAL_LANGUAGE_REQUIRED,
        UNSPECIFIED
    }

    public record JobClassification(
            boolean angular,
            boolean frontend,
            boolean java,
            boolean fullStack,
            EnglishLanguageStatus englishStatus
    ) {
    }

    private static final Pattern JAVA_WORD_PATTERN =
            Pattern.compile("\\bjava\\b(?!script)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVA_ECOSYSTEM_PATTERN =
            Pattern.compile("\\b(spring\\s*boot|spring\\s*framework|jakarta\\s*ee|quarkus|hibernate|micronaut|jvm)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANGULAR_PATTERN =
            Pattern.compile("\\b(angular|ngrx|angular\\s+signals)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FRONTEND_PATTERN =
            Pattern.compile("\\b(frontend|front-end|front\\s+end|angular|react|reactjs|vue|vuejs|svelte|next\\.js|nextjs|ui\\s+engineer|ui\\s+developer)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FULLSTACK_EXPLICIT_PATTERN =
            Pattern.compile("\\b(full[\\s-]?stack)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BACKEND_SIGNAL_PATTERN =
            Pattern.compile("\\b(backend|back-end|back\\s+end|spring\\s*boot|microservices|node\\.js|nodejs|python|django|fastapi|golang|\\bgo\\b|dotnet|\\.net|c#)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern LOCAL_LANG_GATE_PATTERN = Pattern.compile(
            "(fluent\\s+(in\\s+)?(german|dutch|french|swedish|danish|polish|spanish|portuguese|italian)"
                    + "|(german|dutch|french|swedish|danish|polish)\\s+(required|mandatory|c1|c2|native)"
                    + "|deutschkenntnisse|fliessend\\s+deutsch|fließend\\s+deutsch|verhandlungssicher\\s+deutsch"
                    + "|nederlands\\s+vereist|spreek(t)?\\s+vloeiend\\s+nederlands"
                    + "|maitrise\\s+du\\s+francais|maîtrise\\s+du\\s+français|français\\s+courant)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ENGLISH_CONFIRMED_PATTERN = Pattern.compile(
            "(working\\s+language\\s*[:\\-]?\\s*english|english[\\s-]+speaking|fluent\\s+english|proficient\\s+english"
                    + "|english\\s+(required|only|first|c1|b2|fluency)|team\\s+language\\s+(is\\s+)?english|\\benglish\\b)",
            Pattern.CASE_INSENSITIVE
    );

    public JobClassification classify(Job job, JobAiAnalysis aiAnalysis) {
        String combined = buildCombinedCorpus(job, aiAnalysis);

        boolean angular = ANGULAR_PATTERN.matcher(combined).find();
        boolean frontend = angular || FRONTEND_PATTERN.matcher(combined).find();
        boolean java = JAVA_WORD_PATTERN.matcher(combined).find()
                || JAVA_ECOSYSTEM_PATTERN.matcher(combined).find();
        boolean backendSignal = java || BACKEND_SIGNAL_PATTERN.matcher(combined).find();
        boolean fullStack = FULLSTACK_EXPLICIT_PATTERN.matcher(combined).find()
                || (frontend && backendSignal);

        EnglishLanguageStatus englishStatus = determineEnglishStatus(combined, aiAnalysis);

        return new JobClassification(angular, frontend, java, fullStack, englishStatus);
    }

    public EnglishLanguageStatus determineEnglishStatus(Job job, JobAiAnalysis aiAnalysis) {
        return determineEnglishStatus(buildCombinedCorpus(job, aiAnalysis), aiAnalysis);
    }

    public BigDecimal normalizeAnnualSalaryMidpoint(Job job) {
        if (job == null) {
            return null;
        }
        BigDecimal rawMid = computeMidpoint(job.getSalaryMin(), job.getSalaryMax());
        if (rawMid == null || rawMid.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        SalaryPeriod period = job.getSalaryPeriod() != null ? job.getSalaryPeriod() : SalaryPeriod.YEARLY;
        BigDecimal multiplier = switch (period) {
            case YEARLY -> BigDecimal.ONE;
            case MONTHLY -> BigDecimal.valueOf(12);
            case DAILY -> BigDecimal.valueOf(220);
            case HOURLY -> BigDecimal.valueOf(1800);
        };
        return rawMid.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private EnglishLanguageStatus determineEnglishStatus(String combined, JobAiAnalysis aiAnalysis) {
        if (aiAnalysis != null && aiAnalysis.getLanguageRequirements() != null
                && !aiAnalysis.getLanguageRequirements().isEmpty()) {
            boolean sawEnglish = false;
            boolean sawMandatoryLocal = false;
            for (String lang : aiAnalysis.getLanguageRequirements()) {
                if (lang == null || lang.isBlank()) {
                    continue;
                }
                String lower = lang.trim().toLowerCase(Locale.ROOT);
                boolean optionalHint = lower.contains("nice to have")
                        || lower.contains("bonus")
                        || lower.contains("optional")
                        || lower.contains("plus")
                        || lower.contains("preferred only");
                if (lower.contains("english") || lower.equals("en")) {
                    sawEnglish = true;
                } else if (!optionalHint && mentionsNonEnglishLanguage(lower)) {
                    sawMandatoryLocal = true;
                }
            }
            if (sawMandatoryLocal) {
                return EnglishLanguageStatus.LOCAL_LANGUAGE_REQUIRED;
            }
            if (sawEnglish) {
                return EnglishLanguageStatus.CONFIRMED;
            }
        }

        if (LOCAL_LANG_GATE_PATTERN.matcher(combined).find()) {
            return EnglishLanguageStatus.LOCAL_LANGUAGE_REQUIRED;
        }
        if (ENGLISH_CONFIRMED_PATTERN.matcher(combined).find()) {
            return EnglishLanguageStatus.CONFIRMED;
        }
        return EnglishLanguageStatus.UNSPECIFIED;
    }

    private boolean mentionsNonEnglishLanguage(String lower) {
        return lower.contains("german") || lower.contains("deutsch")
                || lower.contains("dutch") || lower.contains("nederlands")
                || lower.contains("french") || lower.contains("français") || lower.contains("francais")
                || lower.contains("swedish") || lower.contains("svenska")
                || lower.contains("danish") || lower.contains("dansk")
                || lower.contains("polish") || lower.contains("polski")
                || lower.contains("spanish") || lower.contains("español") || lower.contains("espanol")
                || lower.contains("portuguese") || lower.contains("português")
                || lower.contains("italian") || lower.contains("italiano")
                || lower.contains("finnish") || lower.contains("suomi");
    }

    private String buildCombinedCorpus(Job job, JobAiAnalysis aiAnalysis) {
        StringBuilder sb = new StringBuilder();
        if (job != null) {
            appendPart(sb, job.getTitle());
            appendPart(sb, job.getNormalizedTitle());
            if (job.getSkills() != null && !job.getSkills().isEmpty()) {
                appendPart(sb, String.join(" ", job.getSkills()));
            }
            appendPart(sb, job.getDescriptionSnippet());
            if (job.getDescription() != null) {
                String desc = job.getDescription();
                appendPart(sb, desc.length() > 3500 ? desc.substring(0, 3500) : desc);
            }
        }
        if (aiAnalysis != null) {
            appendPart(sb, aiAnalysis.getExtractedTitle());
            if (aiAnalysis.getRequiredSkills() != null && !aiAnalysis.getRequiredSkills().isEmpty()) {
                appendPart(sb, String.join(" ", aiAnalysis.getRequiredSkills()));
            }
            if (aiAnalysis.getNiceToHaveSkills() != null && !aiAnalysis.getNiceToHaveSkills().isEmpty()) {
                appendPart(sb, String.join(" ", aiAnalysis.getNiceToHaveSkills()));
            }
        }
        return sb.toString();
    }

    private void appendPart(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            sb.append(part).append(' ');
        }
    }

    private BigDecimal computeMidpoint(BigDecimal min, BigDecimal max) {
        if (min != null && min.compareTo(BigDecimal.ZERO) > 0
                && max != null && max.compareTo(BigDecimal.ZERO) > 0) {
            return min.add(max).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        }
        if (max != null && max.compareTo(BigDecimal.ZERO) > 0) {
            return max;
        }
        if (min != null && min.compareTo(BigDecimal.ZERO) > 0) {
            return min;
        }
        return null;
    }
}
