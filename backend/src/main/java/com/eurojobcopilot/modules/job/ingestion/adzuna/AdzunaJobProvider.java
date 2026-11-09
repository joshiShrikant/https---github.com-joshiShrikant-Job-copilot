package com.eurojobcopilot.modules.job.ingestion.adzuna;

import com.eurojobcopilot.modules.job.ingestion.IngestionQuery;
import com.eurojobcopilot.modules.job.ingestion.JobIngestionProperties;
import com.eurojobcopilot.modules.job.ingestion.JobProvider;
import com.eurojobcopilot.modules.job.ingestion.JobProviderException;
import com.eurojobcopilot.modules.job.ingestion.RawJobPosting;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Multi-country European job provider connector integrating the Adzuna REST API v1.
 */
@Component
public class AdzunaJobProvider implements JobProvider {

    public static final String PROVIDER_NAME = "ADZUNA";

    private static final Map<String, CountryMetadata> CODE_TO_METADATA = Map.ofEntries(
            Map.entry("de", new CountryMetadata("Germany", "EUR")),
            Map.entry("nl", new CountryMetadata("Netherlands", "EUR")),
            Map.entry("gb", new CountryMetadata("United Kingdom", "GBP")),
            Map.entry("uk", new CountryMetadata("United Kingdom", "GBP")),
            Map.entry("fr", new CountryMetadata("France", "EUR")),
            Map.entry("ch", new CountryMetadata("Switzerland", "CHF")),
            Map.entry("at", new CountryMetadata("Austria", "EUR")),
            Map.entry("be", new CountryMetadata("Belgium", "EUR")),
            Map.entry("es", new CountryMetadata("Spain", "EUR")),
            Map.entry("pl", new CountryMetadata("Poland", "PLN")),
            Map.entry("it", new CountryMetadata("Italy", "EUR"))
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
            Map.entry("france", "fr"),
            Map.entry("fr", "fr"),
            Map.entry("switzerland", "ch"),
            Map.entry("schweiz", "ch"),
            Map.entry("ch", "ch"),
            Map.entry("austria", "at"),
            Map.entry("at", "at"),
            Map.entry("belgium", "be"),
            Map.entry("be", "be"),
            Map.entry("spain", "es"),
            Map.entry("es", "es"),
            Map.entry("poland", "pl"),
            Map.entry("pl", "pl"),
            Map.entry("italy", "it"),
            Map.entry("it", "it")
    );

    private final JobIngestionProperties properties;
    private final RestClient restClient;

    public AdzunaJobProvider(
            @Qualifier("jobIngestionRestClientBuilder") RestClient.Builder restClientBuilder,
            JobIngestionProperties properties
    ) {
        this.properties = properties;
        String baseUrl = properties.getAdzuna() != null && hasText(properties.getAdzuna().getBaseUrl())
                ? properties.getAdzuna().getBaseUrl().trim()
                : "https://api.adzuna.com/v1/api/jobs";
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isEnabled() {
        JobIngestionProperties.AdzunaProperties adzuna = properties.getAdzuna();
        return properties.isEnabled()
                && adzuna != null
                && adzuna.isEnabled()
                && hasText(adzuna.getBaseUrl())
                && hasText(adzuna.getAppId())
                && hasText(adzuna.getAppKey());
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
        List<String> configured = properties.getAdzuna() != null
                ? properties.getAdzuna().getCountryCodes()
                : List.of();
        return configured.stream().anyMatch(code -> normalizeCountryCode(code).equals(mapped));
    }

    @Override
    public List<RawJobPosting> fetchJobs(IngestionQuery query) {
        JobIngestionProperties.AdzunaProperties adzuna = properties.getAdzuna();
        List<String> targetCodes = resolveTargetCountryCodes(query, adzuna);
        if (targetCodes.isEmpty()) {
            return List.of();
        }

        int requestedPages = query != null ? query.maxPages() : properties.getDefaultMaxPages();
        int configuredPages = adzuna != null ? adzuna.getMaxPagesPerCountry() : 2;
        int maxPages = Math.max(1, Math.min(requestedPages, Math.max(1, configuredPages)));
        int perPage = adzuna != null ? Math.min(50, Math.max(1, adzuna.getResultsPerPage())) : 50;
        if (query != null && query.pageSize() > 0) {
            perPage = Math.min(perPage, query.pageSize());
        }
        int maxResults = properties.getMaxResultsPerProvider() > 0
                ? properties.getMaxResultsPerProvider()
                : Integer.MAX_VALUE;
        boolean remoteOnly = query != null && query.remoteOnly();
        Instant sinceWatermark = query != null ? query.since() : null;

        List<RawJobPosting> collected = new ArrayList<>();

        for (String countryCode : targetCodes) {
            for (int page = 1; page <= maxPages; page++) {
                AdzunaApiResponse response = fetchCountryPage(countryCode, page, perPage, query);
                if (response == null || response.results() == null || response.results().isEmpty()) {
                    break;
                }

                boolean reachedOldEntries = false;
                for (AdzunaApiResponse.AdzunaJobItem item : response.results()) {
                    Instant postedAt = parseIsoCreated(item.created());
                    if (sinceWatermark != null && postedAt != null && postedAt.isBefore(sinceWatermark)) {
                        reachedOldEntries = true;
                        continue;
                    }

                    RawJobPosting raw = mapToRawPosting(item, countryCode, postedAt);
                    if (remoteOnly && !"REMOTE".equalsIgnoreCase(raw.workArrangementHint())) {
                        continue;
                    }

                    collected.add(raw);
                    if (collected.size() >= maxResults) {
                        return collected;
                    }
                }

                if (reachedOldEntries || response.results().size() < perPage) {
                    break;
                }
            }
        }

        return collected;
    }

    private AdzunaApiResponse fetchCountryPage(
            String countryCode,
            int page,
            int perPage,
            IngestionQuery query
    ) {
        JobIngestionProperties.AdzunaProperties adzuna = properties.getAdzuna();
        try {
            return restClient.get()
                    .uri(uriBuilder -> buildSearchUri(uriBuilder, countryCode, page, perPage, query, adzuna))
                    .retrieve()
                    .body(AdzunaApiResponse.class);
        } catch (RestClientException ex) {
            throw new JobProviderException(
                    PROVIDER_NAME,
                    "Adzuna API fetch failed for country=" + countryCode + " page=" + page + ": " + ex.getMessage(),
                    ex
            );
        }
    }

    private URI buildSearchUri(
            UriBuilder uriBuilder,
            String countryCode,
            int page,
            int perPage,
            IngestionQuery query,
            JobIngestionProperties.AdzunaProperties adzuna
    ) {
        UriBuilder builder = uriBuilder
                .path("/{country}/search/{page}")
                .queryParam("app_id", adzuna.getAppId())
                .queryParam("app_key", adzuna.getAppKey())
                .queryParam("results_per_page", perPage)
                .queryParam("sort_by", "date");

        if (query != null && !query.keywords().isEmpty()) {
            builder.queryParam("what_or", String.join(" ", query.keywords()));
        }

        if (query != null && query.since() != null) {
            long hours = Math.max(1L, Duration.between(query.since(), Instant.now()).toHours());
            long days = Math.max(1L, (long) Math.ceil(hours / 24.0));
            builder.queryParam("max_days_old", days);
        }

        return builder.build(countryCode, page);
    }

    private List<String> resolveTargetCountryCodes(
            IngestionQuery query,
            JobIngestionProperties.AdzunaProperties adzuna
    ) {
        Set<String> configuredCodes = new LinkedHashSet<>();
        if (adzuna != null && adzuna.getCountryCodes() != null) {
            for (String code : adzuna.getCountryCodes()) {
                String normalized = normalizeCountryCode(code);
                if (CODE_TO_METADATA.containsKey(normalized)) {
                    configuredCodes.add(normalized);
                }
            }
        }

        if (query == null || query.countries().isEmpty()) {
            return List.copyOf(configuredCodes);
        }

        Set<String> requestedCodes = new LinkedHashSet<>();
        for (String country : query.countries()) {
            if (hasText(country)) {
                String mapped = COUNTRY_NAME_TO_CODE.get(country.trim().toLowerCase(Locale.ROOT));
                if (mapped != null && (configuredCodes.isEmpty() || configuredCodes.contains(mapped))) {
                    requestedCodes.add(mapped);
                }
            }
        }
        return List.copyOf(requestedCodes);
    }

    private RawJobPosting mapToRawPosting(
            AdzunaApiResponse.AdzunaJobItem item,
            String countryCode,
            Instant postedAt
    ) {
        CountryMetadata countryMeta = CODE_TO_METADATA.getOrDefault(
                countryCode,
                new CountryMetadata(countryCode.toUpperCase(Locale.ROOT), "EUR")
        );

        String companyName = item.company() != null ? item.company().displayName() : null;
        String displayLocation = item.location() != null ? item.location().displayName() : null;
        String city = extractCity(item.location());

        List<String> hints = new ArrayList<>();
        if (hasText(item.contractType())) {
            hints.add(item.contractType());
        }
        if (hasText(item.contractTime())) {
            hints.add(item.contractTime());
        }
        String employmentHint = String.join(", ", hints);

        String titleAndDesc = (item.title() != null ? item.title() : "")
                + " "
                + (item.description() != null ? item.description() : "");
        String titleLower = titleAndDesc.toLowerCase(Locale.ROOT);
        String arrangementHint = null;
        if (titleLower.contains("hybrid")) {
            arrangementHint = "HYBRID";
        } else if (titleLower.contains("remote")) {
            arrangementHint = "REMOTE";
        }

        return RawJobPosting.builder()
                .sourceProvider(PROVIDER_NAME)
                .sourceJobId(item.id())
                .title(item.title())
                .companyName(companyName)
                .location(displayLocation)
                .city(city)
                .country(countryMeta.countryName())
                .workArrangementHint(arrangementHint)
                .employmentTypeHint(employmentHint)
                .salaryMin(item.salaryMin())
                .salaryMax(item.salaryMax())
                .salaryCurrency(countryMeta.currency())
                .salaryPeriodHint("YEARLY")
                .rawDescription(item.description())
                .originalUrl(item.redirectUrl())
                .postedAt(postedAt)
                .build();
    }

    private String extractCity(AdzunaApiResponse.AdzunaLocation location) {
        if (location == null) {
            return null;
        }
        List<String> area = location.area();
        if (area != null && area.size() >= 2) {
            String leaf = area.getLast();
            if (hasText(leaf)) {
                return leaf.trim();
            }
        }
        if (hasText(location.displayName())) {
            String firstPart = location.displayName().split(",")[0].trim();
            if (!COUNTRY_NAME_TO_CODE.containsKey(firstPart.toLowerCase(Locale.ROOT))) {
                return firstPart;
            }
        }
        return null;
    }

    private Instant parseIsoCreated(String created) {
        if (!hasText(created)) {
            return null;
        }
        try {
            return Instant.parse(created.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String normalizeCountryCode(String code) {
        if (!hasText(code)) {
            return "";
        }
        String lower = code.trim().toLowerCase(Locale.ROOT);
        return "uk".equals(lower) ? "gb" : lower;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CountryMetadata(String countryName, String currency) {
    }
}

