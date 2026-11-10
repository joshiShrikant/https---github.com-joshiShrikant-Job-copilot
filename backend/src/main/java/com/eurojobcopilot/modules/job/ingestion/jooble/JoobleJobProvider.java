package com.eurojobcopilot.modules.job.ingestion.jooble;

import com.eurojobcopilot.modules.job.ingestion.IngestionQuery;
import com.eurojobcopilot.modules.job.ingestion.JobIngestionProperties;
import com.eurojobcopilot.modules.job.ingestion.JobProvider;
import com.eurojobcopilot.modules.job.ingestion.JobProviderException;
import com.eurojobcopilot.modules.job.ingestion.RawJobPosting;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * External job provider connector integrating the Jooble Job Search POST REST API.
 */
@Component
public class JoobleJobProvider implements JobProvider {

    public static final String PROVIDER_NAME = "JOOBLE";

    private static final int MAX_KEYWORDS_IN_QUERY = 8;
    private static final BigDecimal MAX_SANE_SALARY = new BigDecimal("10000000");

    private static final Pattern US_LOCATION_PATTERN = Pattern.compile("^([^,]+),\\s*([A-Z]{2})(?:\\b.*)?$");
    private static final Pattern SALARY_NUMBER_PATTERN = Pattern.compile("(\\d+(?:[',.]\\d{3})*(?:[.,]\\d{1,2})?)\\s*(k)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern THOUSANDS_GROUP_PATTERN = Pattern.compile("^\\d{1,3}(?:([,.'])\\d{3})+$");

    private static final Set<String> US_STATE_CODES = Set.of(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
            "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
            "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
            "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
            "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
            "DC"
    );

    private static final Map<String, CountryMetadata> CODE_TO_METADATA = Map.ofEntries(
            Map.entry("de", new CountryMetadata("Germany", "EUR")),
            Map.entry("nl", new CountryMetadata("Netherlands", "EUR")),
            Map.entry("gb", new CountryMetadata("United Kingdom", "GBP")),
            Map.entry("uk", new CountryMetadata("United Kingdom", "GBP")),
            Map.entry("ch", new CountryMetadata("Switzerland", "CHF")),
            Map.entry("fr", new CountryMetadata("France", "EUR")),
            Map.entry("es", new CountryMetadata("Spain", "EUR")),
            Map.entry("ie", new CountryMetadata("Ireland", "EUR")),
            Map.entry("se", new CountryMetadata("Sweden", "SEK")),
            Map.entry("at", new CountryMetadata("Austria", "EUR")),
            Map.entry("be", new CountryMetadata("Belgium", "EUR")),
            Map.entry("pl", new CountryMetadata("Poland", "PLN")),
            Map.entry("it", new CountryMetadata("Italy", "EUR")),
            Map.entry("us", new CountryMetadata("United States", "USD"))
    );

    private static final Map<String, String> COUNTRY_NAME_TO_CODE = Map.ofEntries(
            Map.entry("germany", "de"),
            Map.entry("deutschland", "de"),
            Map.entry("de", "de"),
            Map.entry("netherlands", "nl"),
            Map.entry("the netherlands", "nl"),
            Map.entry("nederland", "nl"),
            Map.entry("nl", "nl"),
            Map.entry("united kingdom", "gb"),
            Map.entry("uk", "gb"),
            Map.entry("gb", "gb"),
            Map.entry("great britain", "gb"),
            Map.entry("england", "gb"),
            Map.entry("switzerland", "ch"),
            Map.entry("schweiz", "ch"),
            Map.entry("suisse", "ch"),
            Map.entry("ch", "ch"),
            Map.entry("france", "fr"),
            Map.entry("fr", "fr"),
            Map.entry("spain", "es"),
            Map.entry("es", "es"),
            Map.entry("ireland", "ie"),
            Map.entry("ie", "ie"),
            Map.entry("sweden", "se"),
            Map.entry("sverige", "se"),
            Map.entry("se", "se"),
            Map.entry("austria", "at"),
            Map.entry("at", "at"),
            Map.entry("belgium", "be"),
            Map.entry("be", "be"),
            Map.entry("poland", "pl"),
            Map.entry("pl", "pl"),
            Map.entry("italy", "it"),
            Map.entry("it", "it"),
            Map.entry("united states", "us"),
            Map.entry("united states of america", "us"),
            Map.entry("usa", "us"),
            Map.entry("us", "us")
    );

    private static final Map<String, String> CITY_HINT_TO_COUNTRY_CODE = Map.ofEntries(
            Map.entry("berlin", "de"),
            Map.entry("munich", "de"),
            Map.entry("munchen", "de"),
            Map.entry("hamburg", "de"),
            Map.entry("frankfurt", "de"),
            Map.entry("cologne", "de"),
            Map.entry("stuttgart", "de"),
            Map.entry("amsterdam", "nl"),
            Map.entry("rotterdam", "nl"),
            Map.entry("utrecht", "nl"),
            Map.entry("eindhoven", "nl"),
            Map.entry("london", "gb"),
            Map.entry("manchester", "gb"),
            Map.entry("edinburgh", "gb"),
            Map.entry("bern", "ch"),
            Map.entry("zurich", "ch"),
            Map.entry("geneva", "ch"),
            Map.entry("basel", "ch"),
            Map.entry("lausanne", "ch"),
            Map.entry("zug", "ch"),
            Map.entry("paris", "fr"),
            Map.entry("lyon", "fr"),
            Map.entry("madrid", "es"),
            Map.entry("barcelona", "es"),
            Map.entry("dublin", "ie"),
            Map.entry("stockholm", "se"),
            Map.entry("vienna", "at"),
            Map.entry("brussels", "be")
    );

    private final JobIngestionProperties properties;
    private final RestClient restClient;

    public JoobleJobProvider(
            @Qualifier("jobIngestionRestClientBuilder") RestClient.Builder restClientBuilder,
            JobIngestionProperties properties
    ) {
        this.properties = properties;
        String baseUrl = normalizeBaseUrl(
                properties.getJooble() != null ? properties.getJooble().getBaseUrl() : null
        );
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isEnabled() {
        JobIngestionProperties.JoobleProperties jooble = properties.getJooble();
        return properties.isEnabled()
                && jooble != null
                && jooble.isEnabled()
                && hasText(jooble.getBaseUrl())
                && hasText(jooble.getApiKey());
    }

    @Override
    public boolean supportsCountry(String country) {
        if (!hasText(country)) {
            return false;
        }
        String mapped = COUNTRY_NAME_TO_CODE.get(country.trim().toLowerCase(Locale.ROOT));
        if (mapped == null) {
            return false;
        }
        List<String> configured = properties.getJooble() != null
                ? properties.getJooble().getCountryCodes()
                : List.of();
        return configured != null && configured.stream().anyMatch(code -> normalizeCountryCode(code).equals(mapped));
    }

    @Override
    public List<RawJobPosting> fetchJobs(IngestionQuery query) {
        JobIngestionProperties.JoobleProperties jooble = properties.getJooble();
        if (jooble == null || !hasText(jooble.getApiKey())) {
            return List.of();
        }

        List<SearchTarget> targets = resolveSearchTargets(query, jooble);
        if (targets.isEmpty()) {
            return List.of();
        }

        String keywordsQuery = buildKeywordsQuery(query, jooble);
        int requestedPages = query != null ? query.maxPages() : properties.getDefaultMaxPages();
        int configuredPages = jooble.getMaxPagesPerLocation();
        int maxPages = Math.max(1, Math.min(requestedPages, Math.max(1, configuredPages)));

        int perPage = Math.min(100, Math.max(1, jooble.getResultsPerPage()));
        if (query != null && query.pageSize() > 0) {
            perPage = Math.min(perPage, query.pageSize());
        }

        int maxResults = properties.getMaxResultsPerProvider() > 0
                ? properties.getMaxResultsPerProvider()
                : Integer.MAX_VALUE;
        boolean remoteOnly = query != null && query.remoteOnly();
        Instant sinceWatermark = query != null ? query.since() : null;

        List<RawJobPosting> collected = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (SearchTarget target : targets) {
            for (int page = 1; page <= maxPages; page++) {
                JoobleApiResponse response = fetchPage(jooble.getApiKey().trim(), keywordsQuery, target.queryLocation(), page, perPage);
                if (response == null || response.jobs() == null || response.jobs().isEmpty()) {
                    break;
                }

                for (JoobleApiResponse.JoobleJobItem item : response.jobs()) {
                    if (item == null) {
                        continue;
                    }
                    String sourceJobId = extractId(item.id());
                    if (sourceJobId != null && !seenIds.add(sourceJobId)) {
                        continue;
                    }

                    Instant postedAt = parseUpdatedTimestamp(item.updated());
                    if (sinceWatermark != null && postedAt != null && postedAt.isBefore(sinceWatermark)) {
                        continue;
                    }

                    RawJobPosting raw = mapToRawPosting(item, sourceJobId, target.countryMetadata(), postedAt);
                    if (remoteOnly && !"REMOTE".equalsIgnoreCase(raw.workArrangementHint())) {
                        continue;
                    }

                    collected.add(raw);
                    if (collected.size() >= maxResults) {
                        return collected;
                    }
                }

                long fetchedUpToPage = (long) (page - 1) * perPage + response.jobs().size();
                if (response.jobs().size() < perPage
                        || (response.totalCount() != null && fetchedUpToPage >= response.totalCount())) {
                    break;
                }
            }
        }

        return collected;
    }

    private JoobleApiResponse fetchPage(
            String apiKey,
            String keywords,
            String location,
            int page,
            int perPage
    ) {
        JoobleSearchRequest payload = new JoobleSearchRequest(keywords, location, page, perPage);
        try {
            return restClient.post()
                    .uri("/{apiKey}", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JoobleApiResponse.class);
        } catch (RestClientException ex) {
            throw new JobProviderException(
                    PROVIDER_NAME,
                    "Jooble API fetch failed for location=" + location + " page=" + page + ": "
                            + sanitizeErrorMessage(ex.getMessage(), apiKey),
                    ex
            );
        }
    }

    private String buildKeywordsQuery(IngestionQuery query, JobIngestionProperties.JoobleProperties jooble) {
        if (query != null && query.keywords() != null && !query.keywords().isEmpty()) {
            Set<String> terms = new LinkedHashSet<>();
            for (String rawKeyword : query.keywords()) {
                if (!hasText(rawKeyword)) {
                    continue;
                }
                String trimmed = rawKeyword.trim();
                if (trimmed.contains(" ") && !(trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
                    terms.add("\"" + trimmed + "\"");
                } else {
                    terms.add(trimmed);
                }
                if (terms.size() >= MAX_KEYWORDS_IN_QUERY) {
                    break;
                }
            }
            if (!terms.isEmpty()) {
                return String.join(" OR ", terms);
            }
        }
        return hasText(jooble.getDefaultKeywords()) ? jooble.getDefaultKeywords().trim() : "it";
    }

    private List<SearchTarget> resolveSearchTargets(
            IngestionQuery query,
            JobIngestionProperties.JoobleProperties jooble
    ) {
        Set<String> configuredCodes = new LinkedHashSet<>();
        if (jooble.getCountryCodes() != null) {
            for (String code : jooble.getCountryCodes()) {
                String normalized = normalizeCountryCode(code);
                if (CODE_TO_METADATA.containsKey(normalized)) {
                    configuredCodes.add(normalized);
                }
            }
        }

        Set<String> requestedCodes = new LinkedHashSet<>();
        boolean hasRequestedCountries = query != null && !query.countries().isEmpty();
        if (hasRequestedCountries) {
            for (String country : query.countries()) {
                if (!hasText(country)) {
                    continue;
                }
                String mapped = COUNTRY_NAME_TO_CODE.get(country.trim().toLowerCase(Locale.ROOT));
                if (mapped != null && (configuredCodes.isEmpty() || configuredCodes.contains(mapped))) {
                    requestedCodes.add(mapped);
                }
            }
        }

        List<String> configuredLocations = jooble.getLocations() == null
                ? List.of()
                : jooble.getLocations().stream().filter(this::hasText).toList();
        if (!configuredLocations.isEmpty()) {
            List<SearchTarget> targets = new ArrayList<>();
            String fallbackCode = !configuredCodes.isEmpty() ? configuredCodes.iterator().next() : "ch";

            for (String rawLocation : configuredLocations) {
                String location = rawLocation.trim();
                String inferredCode = inferCountryCodeFromLocation(location, fallbackCode);
                if (hasRequestedCountries && !requestedCodes.contains(inferredCode)) {
                    continue;
                }
                CountryMetadata meta = CODE_TO_METADATA.getOrDefault(
                        inferredCode,
                        new CountryMetadata(location, "EUR")
                );
                targets.add(new SearchTarget(location, meta));
            }
            return targets;
        }

        Set<String> effectiveCodes = hasRequestedCountries ? requestedCodes : configuredCodes;
        if (effectiveCodes.isEmpty()) {
            return List.of();
        }

        List<SearchTarget> targets = new ArrayList<>(effectiveCodes.size());
        for (String code : effectiveCodes) {
            CountryMetadata meta = CODE_TO_METADATA.get(code);
            if (meta != null) {
                targets.add(new SearchTarget(meta.countryName(), meta));
            }
        }
        return targets;
    }

    private RawJobPosting mapToRawPosting(
            JoobleApiResponse.JoobleJobItem item,
            String sourceJobId,
            CountryMetadata fallbackCountry,
            Instant postedAt
    ) {
        String displayLocation = trimToNull(item.location());
        ResolvedGeo resolvedGeo = resolveItemGeography(displayLocation, fallbackCountry);

        String titleAndSnippet = (item.title() != null ? item.title() : "")
                + " "
                + (displayLocation != null ? displayLocation : "")
                + " "
                + (item.snippet() != null ? item.snippet() : "");
        String lowerText = titleAndSnippet.toLowerCase(Locale.ROOT);
        String arrangementHint = null;
        if (lowerText.contains("hybrid")) {
            arrangementHint = "HYBRID";
        } else if (lowerText.contains("remote") || lowerText.contains("home office")) {
            arrangementHint = "REMOTE";
        }

        ParsedSalary parsedSalary = parseSalary(item.salary(), resolvedGeo.currency());

        return RawJobPosting.builder()
                .sourceProvider(PROVIDER_NAME)
                .sourceJobId(sourceJobId)
                .title(trimToNull(item.title()))
                .companyName(trimToNull(item.company()))
                .location(displayLocation)
                .city(resolvedGeo.city())
                .country(resolvedGeo.country())
                .workArrangementHint(arrangementHint)
                .employmentTypeHint(trimToNull(item.type()))
                .salaryMin(parsedSalary.min())
                .salaryMax(parsedSalary.max())
                .salaryCurrency(parsedSalary.currency())
                .salaryPeriodHint(parsedSalary.periodHint())
                .rawDescription(trimToNull(item.snippet()))
                .originalUrl(trimToNull(item.link()))
                .postedAt(postedAt)
                .build();
    }

    private ResolvedGeo resolveItemGeography(String rawLocation, CountryMetadata fallbackCountry) {
        if (!hasText(rawLocation)) {
            return new ResolvedGeo(null, fallbackCountry.countryName(), fallbackCountry.currency());
        }

        String trimmed = rawLocation.trim();
        Matcher usMatcher = US_LOCATION_PATTERN.matcher(trimmed);
        if (usMatcher.matches()) {
            String regionCode = usMatcher.group(2).toUpperCase(Locale.ROOT);
            if (US_STATE_CODES.contains(regionCode) && !COUNTRY_NAME_TO_CODE.containsKey(regionCode.toLowerCase(Locale.ROOT))) {
                return new ResolvedGeo(trimToNull(usMatcher.group(1)), "United States", "USD");
            }
        }

        String city = null;
        String country = null;
        String currency = fallbackCountry.currency();

        String[] segments = trimmed.split("[,;|/]");
        for (String segment : segments) {
            String token = segment.trim();
            if (token.isEmpty()) {
                continue;
            }
            String lower = token.toLowerCase(Locale.ROOT);
            String mappedCode = COUNTRY_NAME_TO_CODE.get(lower);
            if (mappedCode != null) {
                CountryMetadata meta = CODE_TO_METADATA.get(mappedCode);
                if (meta != null && country == null) {
                    country = meta.countryName();
                    currency = meta.currency();
                }
            } else if (!"remote".equals(lower) && !"europe".equals(lower) && city == null) {
                city = token;
            }
        }

        if (country == null && city != null) {
            String cityCode = CITY_HINT_TO_COUNTRY_CODE.get(city.toLowerCase(Locale.ROOT));
            if (cityCode != null && CODE_TO_METADATA.containsKey(cityCode)) {
                CountryMetadata meta = CODE_TO_METADATA.get(cityCode);
                country = meta.countryName();
                currency = meta.currency();
            }
        }

        if (country == null) {
            country = fallbackCountry.countryName();
        }

        return new ResolvedGeo(city, country, currency);
    }

    private String inferCountryCodeFromLocation(String location, String fallbackCode) {
        String[] segments = location.split("[,;|/]");
        for (String segment : segments) {
            String token = segment.trim().toLowerCase(Locale.ROOT);
            if (COUNTRY_NAME_TO_CODE.containsKey(token)) {
                return COUNTRY_NAME_TO_CODE.get(token);
            }
            if (CITY_HINT_TO_COUNTRY_CODE.containsKey(token)) {
                return CITY_HINT_TO_COUNTRY_CODE.get(token);
            }
        }
        return fallbackCode;
    }

    private ParsedSalary parseSalary(String rawSalary, String fallbackCurrency) {
        if (!hasText(rawSalary)) {
            return new ParsedSalary(null, null, null, null);
        }

        String text = rawSalary.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        String currency = detectCurrency(text, lower, fallbackCurrency);
        String periodHint = "YEARLY";
        if (lower.contains("hour") || lower.contains("/hr") || lower.contains("/h") || lower.contains("per h")) {
            periodHint = "HOURLY";
        } else if (lower.contains("day") || lower.contains("daily")) {
            periodHint = "DAILY";
        } else if (lower.contains("month") || lower.contains("/mo")) {
            periodHint = "MONTHLY";
        }

        List<BigDecimal> amounts = new ArrayList<>(2);
        Matcher matcher = SALARY_NUMBER_PATTERN.matcher(text);
        while (matcher.find() && amounts.size() < 2) {
            BigDecimal parsed = parseNumericToken(matcher.group(1), matcher.group(2) != null);
            if (parsed != null && parsed.compareTo(BigDecimal.ZERO) > 0 && parsed.compareTo(MAX_SANE_SALARY) <= 0) {
                amounts.add(parsed);
            }
        }

        if (amounts.isEmpty()) {
            return new ParsedSalary(null, null, null, null);
        }

        BigDecimal min = amounts.get(0);
        BigDecimal max = amounts.size() > 1 ? amounts.get(1) : null;
        if (max != null && min.compareTo(max) > 0) {
            BigDecimal temp = min;
            min = max;
            max = temp;
        }
        return new ParsedSalary(min, max, currency, periodHint);
    }

    private BigDecimal parseNumericToken(String rawToken, boolean hasKSuffix) {
        if (!hasText(rawToken)) {
            return null;
        }
        String normalized = rawToken.trim().replace("'", "");
        if (THOUSANDS_GROUP_PATTERN.matcher(normalized).matches()) {
            normalized = normalized.replace(",", "").replace(".", "");
        } else {
            int lastComma = normalized.lastIndexOf(',');
            int lastDot = normalized.lastIndexOf('.');
            if (lastComma > -1 && lastDot > -1) {
                if (lastComma > lastDot) {
                    normalized = normalized.replace(".", "").replace(",", ".");
                } else {
                    normalized = normalized.replace(",", "");
                }
            } else if (lastComma > -1) {
                int fractionalDigits = normalized.length() - lastComma - 1;
                normalized = fractionalDigits == 3
                        ? normalized.replace(",", "")
                        : normalized.replace(",", ".");
            } else if (lastDot > -1) {
                int fractionalDigits = normalized.length() - lastDot - 1;
                if (fractionalDigits == 3) {
                    normalized = normalized.replace(".", "");
                }
            }
        }

        try {
            BigDecimal value = new BigDecimal(normalized);
            return hasKSuffix ? value.multiply(new BigDecimal("1000")) : value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String detectCurrency(String rawText, String lowerText, String fallbackCurrency) {
        if (rawText.contains("CHF") || lowerText.contains("chf")) {
            return "CHF";
        }
        if (rawText.contains("€") || lowerText.contains("eur")) {
            return "EUR";
        }
        if (rawText.contains("£") || lowerText.contains("gbp")) {
            return "GBP";
        }
        if (rawText.contains("$") || lowerText.contains("usd")) {
            return "USD";
        }
        if (lowerText.contains("pln")) {
            return "PLN";
        }
        if (lowerText.contains("sek")) {
            return "SEK";
        }
        return hasText(fallbackCurrency) ? fallbackCurrency : "EUR";
    }

    private Instant parseUpdatedTimestamp(String updated) {
        if (!hasText(updated)) {
            return null;
        }
        String trimmed = updated.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private String extractId(JsonNode idNode) {
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        String value = idNode.asText();
        return hasText(value) ? value.trim() : null;
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (!hasText(rawBaseUrl)) {
            return "https://jooble.org/api";
        }
        String trimmed = rawBaseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "https://jooble.org/api" : trimmed;
    }

    private String normalizeCountryCode(String code) {
        if (!hasText(code)) {
            return "";
        }
        String lower = code.trim().toLowerCase(Locale.ROOT);
        return "uk".equals(lower) ? "gb" : lower;
    }

    private String sanitizeErrorMessage(String message, String apiKey) {
        if (message == null) {
            return "Unknown upstream error";
        }
        return hasText(apiKey) ? message.replace(apiKey, "[REDACTED]") : message;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CountryMetadata(String countryName, String currency) {
    }

    private record SearchTarget(String queryLocation, CountryMetadata countryMetadata) {
    }

    private record ResolvedGeo(String city, String country, String currency) {
    }

    private record ParsedSalary(BigDecimal min, BigDecimal max, String currency, String periodHint) {
    }
}
