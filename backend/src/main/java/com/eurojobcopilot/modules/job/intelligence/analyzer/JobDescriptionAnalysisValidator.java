package com.eurojobcopilot.modules.job.intelligence.analyzer;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Enforces strict structural, range, and domain invariant checks on AI JD extractions before persistence.
 */
@Component
@RequiredArgsConstructor
public class JobDescriptionAnalysisValidator {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_COMPANY_LENGTH = 255;
    private static final int MAX_COUNTRY_LENGTH = 100;
    private static final int MAX_LOCATION_LENGTH = 255;
    private static final int MAX_EXPERIENCE_SUMMARY_LENGTH = 255;
    private static final int MAX_LIST_ITEMS = 40;
    private static final int MAX_RAW_LIST_ITEMS = 60;
    private static final int MAX_LIST_ITEM_LENGTH = 350;
    private static final int MAX_PROSE_LENGTH = 4000;
    private static final BigDecimal MAX_EXPERIENCE_YEARS = new BigDecimal("40.0");

    private static final Pattern INLINE_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EXCESS_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern ISO_CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

    private final JobNormalizer jobNormalizer;

    public ValidatedJobDescriptionAnalysis validate(RawAiJdExtractionPayload raw) {
        if (raw == null) {
            throw new AiResponseValidationException("AI returned a null JD analysis payload.");
        }

        String extractedTitle = cleanSingleLine(raw.getJobTitle(), MAX_TITLE_LENGTH);
        String extractedCompany = cleanSingleLine(raw.getCompany(), MAX_COMPANY_LENGTH);
        String extractedCountry = validateCountry(raw.getCountry());
        String extractedLocation = cleanSingleLine(raw.getLocation(), MAX_LOCATION_LENGTH);

        BigDecimal experienceYears = validateExperienceYears(raw.getRequiredExperienceYears());
        ExperienceLevel experienceLevel = parseExperienceLevel(raw.getRequiredExperienceLevel());
        String experienceSummary = cleanSingleLine(raw.getRequiredExperienceSummary(), MAX_EXPERIENCE_SUMMARY_LENGTH);

        List<String> requiredSkills = sanitizeStringList("requiredSkills", raw.getRequiredSkills());
        List<String> preferredSkills = sanitizeStringList("preferredSkills", raw.getPreferredSkills());
        List<String> responsibilities = sanitizeStringList("responsibilities", raw.getResponsibilities());
        List<String> benefits = sanitizeStringList("benefits", raw.getBenefits());
        List<String> languageRequirements = sanitizeStringList("languageRequirements", raw.getLanguageRequirements());

        BigDecimal salaryMin = validateSalaryBound("salaryMin", raw.getSalaryMin());
        BigDecimal salaryMax = validateSalaryBound("salaryMax", raw.getSalaryMax());
        if (salaryMin != null && salaryMax != null && salaryMin.compareTo(salaryMax) > 0) {
            throw new AiResponseValidationException(
                    "AI JD analysis failed validation: salaryMin (" + salaryMin + ") exceeds salaryMax (" + salaryMax + ")."
            );
        }

        String salaryCurrency = validateCurrency(raw.getSalaryCurrency());
        SalaryPeriod salaryPeriod = parseSalaryPeriod(raw.getSalaryPeriod());
        WorkArrangement workArrangement = parseWorkArrangement(raw.getWorkArrangement());
        EmploymentType employmentType = parseEmploymentType(raw.getEmploymentType());

        String visaAnalysis = cleanProse(raw.getVisaSponsorshipAnalysis(), MAX_PROSE_LENGTH);
        String relocationAnalysis = cleanProse(raw.getRelocationAnalysis(), MAX_PROSE_LENGTH);
        String summary = cleanProse(raw.getSummary(), MAX_PROSE_LENGTH);

        if (requiredSkills.isEmpty() && responsibilities.isEmpty() && summary == null) {
            throw new AiResponseValidationException(
                    "AI JD analysis failed validation: extraction lacked required skills, responsibilities, and summary."
            );
        }

        return new ValidatedJobDescriptionAnalysis(
                extractedTitle,
                extractedCompany,
                extractedCountry,
                extractedLocation,
                experienceYears,
                experienceLevel,
                experienceSummary,
                requiredSkills,
                preferredSkills,
                responsibilities,
                benefits,
                languageRequirements,
                salaryMin,
                salaryMax,
                salaryCurrency,
                salaryPeriod,
                workArrangement,
                employmentType,
                raw.getSponsorshipOffered(),
                visaAnalysis,
                raw.getRelocationAssistanceOffered(),
                relocationAnalysis,
                summary
        );
    }

    private String validateCountry(String rawCountry) {
        if (rawCountry == null || rawCountry.isBlank()) {
            return null;
        }
        String normalized = jobNormalizer.normalizeCountry(rawCountry);
        if (normalized.isBlank()) {
            return null;
        }
        return truncate(normalized, MAX_COUNTRY_LENGTH);
    }

    private BigDecimal validateExperienceYears(BigDecimal years) {
        if (years == null) {
            return null;
        }
        if (years.compareTo(BigDecimal.ZERO) < 0 || years.compareTo(MAX_EXPERIENCE_YEARS) > 0) {
            throw new AiResponseValidationException(
                    "AI JD analysis failed validation: requiredExperienceYears must be between 0 and 40 (got " + years + ")."
            );
        }
        return years.setScale(1, RoundingMode.HALF_UP);
    }

    private ExperienceLevel parseExperienceLevel(String rawLevel) {
        if (rawLevel == null || rawLevel.isBlank()) {
            return null;
        }
        String normalized = normalizeEnumToken(rawLevel);
        return switch (normalized) {
            case "ENTRY", "ENTRY_LEVEL", "GRADUATE", "TRAINEE" -> ExperienceLevel.ENTRY;
            case "JUNIOR", "JR" -> ExperienceLevel.JUNIOR;
            case "MID", "MID_LEVEL", "INTERMEDIATE", "REGULAR" -> ExperienceLevel.MID;
            case "SENIOR", "SR" -> ExperienceLevel.SENIOR;
            case "LEAD", "TECH_LEAD", "TEAM_LEAD" -> ExperienceLevel.LEAD;
            case "PRINCIPAL", "STAFF", "DISTINGUISHED", "ARCHITECT" -> ExperienceLevel.PRINCIPAL;
            default -> throw new AiResponseValidationException(
                    "AI JD analysis failed validation: unsupported requiredExperienceLevel '" + rawLevel.trim() + "'."
            );
        };
    }

    private BigDecimal validateSalaryBound(String fieldName, BigDecimal bound) {
        if (bound == null) {
            return null;
        }
        if (bound.compareTo(BigDecimal.ZERO) < 0) {
            throw new AiResponseValidationException(
                    "AI JD analysis failed validation: " + fieldName + " must not be negative."
            );
        }
        return bound.setScale(2, RoundingMode.HALF_UP);
    }

    private String validateCurrency(String rawCurrency) {
        if (rawCurrency == null || rawCurrency.isBlank()) {
            return null;
        }
        String token = rawCurrency.trim().toUpperCase(Locale.ROOT);
        if ("€".equals(token)) {
            return "EUR";
        }
        if ("£".equals(token)) {
            return "GBP";
        }
        if (!ISO_CURRENCY_PATTERN.matcher(token).matches()) {
            throw new AiResponseValidationException(
                    "AI JD analysis failed validation: salaryCurrency must be a valid 3-letter ISO code (got '"
                            + rawCurrency.trim() + "')."
            );
        }
        return token;
    }

    private SalaryPeriod parseSalaryPeriod(String rawPeriod) {
        if (rawPeriod == null || rawPeriod.isBlank()) {
            return null;
        }
        String normalized = normalizeEnumToken(rawPeriod);
        return switch (normalized) {
            case "YEARLY", "ANNUAL", "ANNUALLY", "YEAR", "PER_YEAR", "PA" -> SalaryPeriod.YEARLY;
            case "MONTHLY", "MONTH", "PER_MONTH" -> SalaryPeriod.MONTHLY;
            case "DAILY", "DAY", "PER_DAY" -> SalaryPeriod.DAILY;
            case "HOURLY", "HOUR", "PER_HOUR" -> SalaryPeriod.HOURLY;
            default -> throw new AiResponseValidationException(
                    "AI JD analysis failed validation: unsupported salaryPeriod '" + rawPeriod.trim() + "'."
            );
        };
    }

    private WorkArrangement parseWorkArrangement(String rawArrangement) {
        if (rawArrangement == null || rawArrangement.isBlank()) {
            return null;
        }
        String normalized = normalizeEnumToken(rawArrangement);
        return switch (normalized) {
            case "REMOTE", "FULLY_REMOTE", "REMOTE_FIRST", "HOME_OFFICE" -> WorkArrangement.REMOTE;
            case "HYBRID" -> WorkArrangement.HYBRID;
            case "ONSITE", "ON_SITE", "IN_OFFICE", "OFFICE" -> WorkArrangement.ONSITE;
            case "ANY", "FLEXIBLE" -> WorkArrangement.ANY;
            default -> throw new AiResponseValidationException(
                    "AI JD analysis failed validation: unsupported workArrangement '" + rawArrangement.trim() + "'."
            );
        };
    }

    private EmploymentType parseEmploymentType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        String normalized = normalizeEnumToken(rawType);
        return switch (normalized) {
            case "FULL_TIME", "FULLTIME", "PERMANENT", "REGULAR" -> EmploymentType.FULL_TIME;
            case "PART_TIME", "PARTTIME" -> EmploymentType.PART_TIME;
            case "CONTRACT", "CONTRACTOR", "FIXED_TERM" -> EmploymentType.CONTRACT;
            case "INTERNSHIP", "INTERN", "WORKING_STUDENT" -> EmploymentType.INTERNSHIP;
            case "FREELANCE", "FREELANCER" -> EmploymentType.FREELANCE;
            default -> throw new AiResponseValidationException(
                    "AI JD analysis failed validation: unsupported employmentType '" + rawType.trim() + "'."
            );
        };
    }

    private List<String> sanitizeStringList(String fieldName, List<String> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            return List.of();
        }
        if (rawItems.size() > MAX_RAW_LIST_ITEMS) {
            throw new AiResponseValidationException(
                    "AI JD analysis failed validation: field '" + fieldName + "' exceeded maximum item count ("
                            + rawItems.size() + ")."
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

    private String normalizeEnumToken(String raw) {
        return raw.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("_+", "_")
                .toUpperCase(Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}

