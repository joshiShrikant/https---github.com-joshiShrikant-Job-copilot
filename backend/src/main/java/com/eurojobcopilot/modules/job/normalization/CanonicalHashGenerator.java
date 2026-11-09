package com.eurojobcopilot.modules.job.normalization;

import com.eurojobcopilot.core.enums.WorkArrangement;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Produces deterministic 64-character SHA-256 canonical hashes and clean comparison keys
 * for cross-provider job and company deduplication.
 */
@Component
public class CanonicalHashGenerator {

    private static final Pattern GENDER_MARKER_PATTERN = Pattern.compile(
            "\\s*[\\(\\[]\\s*(?:m/w/d|w/m/d|m/f/d|f/m/d|f/m/x|m/w/x|d/f/m|all\\s+genders|gn)\\s*[\\)\\]]",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LEGAL_SUFFIX_PATTERN = Pattern.compile(
            "(?:,\\s*|\\s+)(?:b\\.v\\.|bv|gmbh|ag|se|ab|oy|a/s|aps|s\\.a\\.r\\.l\\.|sarl|sas|s\\.r\\.l\\.|srl|ltd\\.?|limited|inc\\.?|llc|plc|n\\.v\\.|nv|co\\.?\\s*kg)\\.?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^\\p{Alnum}]+");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}+");

    public String computeCanonicalHash(
            String companyName,
            String title,
            String country,
            String city,
            WorkArrangement workArrangement
    ) {
        String companyKey = normalizeCompanyKey(companyName);
        String titleKey = normalizeTitleKey(title);
        String countryKey = normalizeGenericKey(country);
        String cityKey = normalizeGenericKey(city);

        if (cityKey.isEmpty() && workArrangement == WorkArrangement.REMOTE) {
            cityKey = "remote";
        }

        String canonicalPayload = companyKey + "|" + titleKey + "|" + countryKey + "|" + cityKey;
        return sha256Hex(canonicalPayload);
    }

    public String normalizeCompanyKey(String rawCompanyName) {
        if (rawCompanyName == null) {
            return "";
        }
        String stripped = foldDiacritics(rawCompanyName.trim());
        String previous;
        do {
            previous = stripped;
            stripped = LEGAL_SUFFIX_PATTERN.matcher(stripped).replaceAll("").trim();
        } while (!stripped.equals(previous) && !stripped.isEmpty());

        return MULTI_SPACE_PATTERN.matcher(NON_ALPHANUMERIC_PATTERN.matcher(stripped).replaceAll(" "))
                .replaceAll(" ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    public String normalizeTitleKey(String rawTitle) {
        if (rawTitle == null) {
            return "";
        }
        String withoutGender = GENDER_MARKER_PATTERN.matcher(foldDiacritics(rawTitle)).replaceAll(" ");
        return MULTI_SPACE_PATTERN.matcher(NON_ALPHANUMERIC_PATTERN.matcher(withoutGender).replaceAll(" "))
                .replaceAll(" ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    public String normalizeGenericKey(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String folded = foldDiacritics(rawValue);
        return MULTI_SPACE_PATTERN.matcher(NON_ALPHANUMERIC_PATTERN.matcher(folded).replaceAll(" "))
                .replaceAll(" ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    public String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable in JVM", ex);
        }
    }

    private String foldDiacritics(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD);
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
    }
}

