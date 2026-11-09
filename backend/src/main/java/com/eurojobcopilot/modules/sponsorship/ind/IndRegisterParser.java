package com.eurojobcopilot.modules.sponsorship.ind;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java parser for Netherlands IND Public Register of Recognised Sponsors responses
 * (HTML tables or CSV feeds), with strict anti-bot/CAPTCHA detection without bypassing controls.
 */
@Component
public class IndRegisterParser {

    private static final Pattern ROW_PATTERN = Pattern.compile(
            "<tr\\b[^>]*>(.*?)</tr>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern CELL_PATTERN = Pattern.compile(
            "<td\\b[^>]*>(.*?)</td>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern ANY_HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?[0-9A-Fa-f]+);");
    private static final Pattern KVK_DIGITS_ONLY = Pattern.compile("\\b(\\d{8})\\b");

    public record ParsedIndEntry(String officialName, String kvkNumber) {
    }

    public record ParseResult(
            List<ParsedIndEntry> entries,
            boolean blockedOrCaptcha,
            String blockReason,
            String format
    ) {
    }

    public ParseResult parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return new ParseResult(List.of(), false, null, "EMPTY");
        }

        String lower = payload.toLowerCase(Locale.ROOT);
        if (isAntiBotOrCaptchaPage(lower)) {
            return new ParseResult(
                    List.of(),
                    true,
                    "IND source responded with anti-bot or CAPTCHA challenge; access not permitted automatically.",
                    "BLOCKED"
            );
        }

        if (lower.contains("<table") || lower.contains("<tr")) {
            List<ParsedIndEntry> htmlEntries = parseHtmlRows(payload);
            return new ParseResult(htmlEntries, false, null, "HTML_TABLE");
        }

        List<ParsedIndEntry> csvEntries = parseDelimitedLines(payload);
        return new ParseResult(csvEntries, false, null, "DELIMITED_TEXT");
    }

    private boolean isAntiBotOrCaptchaPage(String lowerPayload) {
        return lowerPayload.contains("cf-chl-")
                || lowerPayload.contains("challenge-platform")
                || lowerPayload.contains("g-recaptcha")
                || lowerPayload.contains("hcaptcha")
                || lowerPayload.contains("cf-turnstile")
                || lowerPayload.contains("verify you are human")
                || lowerPayload.contains("enable javascript and cookies to continue");
    }

    private List<ParsedIndEntry> parseHtmlRows(String html) {
        Map<String, ParsedIndEntry> deduplicated = new LinkedHashMap<>();
        Matcher rowMatcher = ROW_PATTERN.matcher(html);
        while (rowMatcher.find()) {
            String rowContent = rowMatcher.group(1);
            if (rowContent.toLowerCase(Locale.ROOT).contains("<th")) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = CELL_PATTERN.matcher(rowContent);
            while (cellMatcher.find()) {
                cells.add(cleanHtmlCell(cellMatcher.group(1)));
            }
            if (cells.isEmpty()) {
                continue;
            }
            String orgName = cells.getFirst();
            if (isHeaderOrBlank(orgName)) {
                continue;
            }
            String kvkRaw = cells.size() > 1 ? extractKvkNumber(cells.get(1)) : null;
            deduplicated.putIfAbsent(orgName.toLowerCase(Locale.ROOT), new ParsedIndEntry(orgName, kvkRaw));
        }
        return List.copyOf(deduplicated.values());
    }

    private List<ParsedIndEntry> parseDelimitedLines(String rawText) {
        Map<String, ParsedIndEntry> deduplicated = new LinkedHashMap<>();
        String[] lines = rawText.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            char delimiter = detectDelimiter(trimmed);
            List<String> columns = splitLineRespectingQuotes(trimmed, delimiter);
            if (columns.isEmpty()) {
                continue;
            }
            String orgName = cleanPlainText(columns.getFirst());
            if (isHeaderOrBlank(orgName)) {
                continue;
            }
            String kvkRaw = columns.size() > 1 ? extractKvkNumber(columns.get(1)) : null;
            deduplicated.putIfAbsent(orgName.toLowerCase(Locale.ROOT), new ParsedIndEntry(orgName, kvkRaw));
        }
        return List.copyOf(deduplicated.values());
    }

    private char detectDelimiter(String line) {
        if (line.contains(";")) {
            return ';';
        }
        if (line.contains("\t")) {
            return '\t';
        }
        return ',';
    }

    private List<String> splitLineRespectingQuotes(String line, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == delimiter && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        result.add(current.toString());
        return result;
    }

    private boolean isHeaderOrBlank(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        return "organisation".equals(lower)
                || "organization".equals(lower)
                || "company".equals(lower)
                || "sponsor".equals(lower)
                || "name".equals(lower);
    }

    private String extractKvkNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = cleanPlainText(raw);
        Matcher digitsMatcher = KVK_DIGITS_ONLY.matcher(cleaned);
        if (digitsMatcher.find()) {
            return "KVK-" + digitsMatcher.group(1);
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    private String cleanHtmlCell(String rawHtml) {
        String stripped = ANY_HTML_TAG.matcher(rawHtml).replaceAll(" ");
        return cleanPlainText(decodeEntities(stripped));
    }

    private String cleanPlainText(String raw) {
        if (raw == null) {
            return "";
        }
        String unquoted = raw.trim();
        if (unquoted.length() >= 2 && unquoted.startsWith("\"") && unquoted.endsWith("\"")) {
            unquoted = unquoted.substring(1, unquoted.length() - 1).trim();
        }
        return MULTI_WHITESPACE.matcher(unquoted).replaceAll(" ").trim();
    }

    private String decodeEntities(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String named = input
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ");

        Matcher matcher = NUMERIC_ENTITY.matcher(named);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            try {
                int codePoint = token.startsWith("x") || token.startsWith("X")
                        ? Integer.parseInt(token.substring(1), 16)
                        : Integer.parseInt(token, 10);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            } catch (IllegalArgumentException ex) {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}

