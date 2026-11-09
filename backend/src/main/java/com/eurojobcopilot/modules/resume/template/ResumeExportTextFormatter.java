package com.eurojobcopilot.modules.resume.template;

import com.eurojobcopilot.modules.resume.dto.ResumeExportFormat;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeEducationDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeHeaderDto;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared text normalization, date formatting, safe URL validation, and export filename derivation
 * used across PDF and DOCX tailored resume exporters.
 */
public final class ResumeExportTextFormatter {

    private static final DateTimeFormatter MONTH_YEAR_FMT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern SLUG_NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private ResumeExportTextFormatter() {
    }

    public static String cleanInlineText(String raw) {
        if (raw == null) {
            return "";
        }
        String withoutCtrl = CONTROL_CHARS.matcher(raw.replace('\u00A0', ' ')).replaceAll("");
        return withoutCtrl.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static String cleanMultilineText(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = CONTROL_CHARS.matcher(raw.replace('\u00A0', ' ')).replaceAll("");
        return normalized.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    public static String formatDateRange(LocalDate startDate, LocalDate endDate, boolean current) {
        String start = startDate != null ? startDate.format(MONTH_YEAR_FMT) : "";
        if (current) {
            return start.isEmpty() ? "Present" : start + " – Present";
        }
        String end = endDate != null ? endDate.format(MONTH_YEAR_FMT) : "";
        if (!start.isEmpty() && !end.isEmpty()) {
            return start + " – " + end;
        }
        return !start.isEmpty() ? start : end;
    }

    public static List<String> buildContactSegments(TailoredResumeHeaderDto header) {
        List<String> segments = new ArrayList<>();
        if (header == null) {
            return segments;
        }
        addIfPresent(segments, cleanInlineText(header.getEmail()));
        addIfPresent(segments, cleanInlineText(header.getLocation()));
        BigDecimal years = header.getYearsOfExperience();
        if (years != null) {
            String yrsStr = years.stripTrailingZeros().toPlainString() + "+ yrs experience";
            segments.add(yrsStr);
        }
        addIfPresent(segments, cleanInlineText(header.getWorkAuthorizationSummary()));
        return segments;
    }

    public static List<LabeledUrl> extractSafeHeaderLinks(TailoredResumeHeaderDto header) {
        List<LabeledUrl> links = new ArrayList<>();
        if (header == null) {
            return links;
        }
        if (isSafeHttpUrl(header.getLinkedinUrl())) {
            links.add(new LabeledUrl("LinkedIn", header.getLinkedinUrl().trim()));
        }
        if (isSafeHttpUrl(header.getGithubUrl())) {
            links.add(new LabeledUrl("GitHub", header.getGithubUrl().trim()));
        }
        if (isSafeHttpUrl(header.getPortfolioUrl())) {
            links.add(new LabeledUrl("Portfolio", header.getPortfolioUrl().trim()));
        }
        return links;
    }

    public static boolean isSafeHttpUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return scheme != null
                    && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && host != null
                    && !host.isBlank();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static String formatEducationHeading(TailoredResumeEducationDto edu) {
        String degree = cleanInlineText(edu.getDegree());
        String field = cleanInlineText(edu.getFieldOfStudy());
        String institution = cleanInlineText(edu.getInstitution());
        StringBuilder sb = new StringBuilder();
        if (!degree.isEmpty()) {
            sb.append(degree);
        }
        if (!field.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(" in ");
            }
            sb.append(field);
        }
        if (!institution.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(" — ");
            }
            sb.append(institution);
        }
        return sb.toString();
    }

    public static String formatEducationMeta(TailoredResumeEducationDto edu) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, cleanInlineText(edu.getCountry()));
        String grade = cleanInlineText(edu.getGradeOrGpa());
        if (!grade.isEmpty()) {
            parts.add("Grade: " + grade);
        }
        return String.join(" · ", parts);
    }

    public static String buildDownloadFilename(
            String draftTitle,
            TailoredResumeHeaderDto header,
            ResumeTemplate template,
            ResumeExportFormat format
    ) {
        String nameSlug = toSlug(header != null ? header.getFullName() : null);
        String titleSlug = toSlug(draftTitle);
        String templateSlug = toSlug((template != null ? template : ResumeTemplate.MODERN).name().replace('_', '-'));

        StringBuilder stem = new StringBuilder();
        if (!nameSlug.isEmpty()) {
            stem.append(truncateSlug(nameSlug, 40));
        }
        if (!titleSlug.isEmpty()) {
            if (!stem.isEmpty()) {
                stem.append('-');
            }
            stem.append(truncateSlug(titleSlug, 45));
        }
        if (stem.isEmpty()) {
            stem.append("tailored-resume");
        }
        if (!templateSlug.isEmpty()) {
            stem.append('-').append(templateSlug);
        }
        stem.append("-cv");

        String finalStem = stem.toString();
        if (finalStem.length() > 110) {
            finalStem = finalStem.substring(0, 110);
            while (finalStem.endsWith("-")) {
                finalStem = finalStem.substring(0, finalStem.length() - 1);
            }
        }
        return finalStem + "." + format.extension();
    }

    private static String toSlug(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String lowered = decomposed.toLowerCase(Locale.ROOT)
                .replace("ß", "ss")
                .replace("ø", "o")
                .replace("ł", "l");
        String slug = SLUG_NON_ALNUM.matcher(lowered).replaceAll("-");
        while (slug.startsWith("-")) {
            slug = slug.substring(1);
        }
        while (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug;
    }

    private static String truncateSlug(String slug, int maxLen) {
        if (slug.length() <= maxLen) {
            return slug;
        }
        String sub = slug.substring(0, maxLen);
        while (sub.endsWith("-")) {
            sub = sub.substring(0, sub.length() - 1);
        }
        return sub;
    }

    private static void addIfPresent(List<String> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(value);
        }
    }

    public record LabeledUrl(String label, String url) {
    }
}

