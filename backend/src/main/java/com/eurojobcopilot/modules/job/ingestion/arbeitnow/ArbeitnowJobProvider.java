package com.eurojobcopilot.modules.job.ingestion.arbeitnow;

import com.eurojobcopilot.modules.job.ingestion.IngestionQuery;
import com.eurojobcopilot.modules.job.ingestion.JobIngestionProperties;
import com.eurojobcopilot.modules.job.ingestion.JobProvider;
import com.eurojobcopilot.modules.job.ingestion.JobProviderException;
import com.eurojobcopilot.modules.job.ingestion.RawJobPosting;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * External job provider connector integrating the Arbeitnow public European Job Board API.
 */
@Component
public class ArbeitnowJobProvider implements JobProvider {

    public static final String PROVIDER_NAME = "ARBEITNOW";
    private static final long EPOCH_MILLIS_THRESHOLD = 10_000_000_000L;

    private final JobIngestionProperties properties;
    private final RestClient restClient;

    public ArbeitnowJobProvider(
            @Qualifier("jobIngestionRestClientBuilder") RestClient.Builder restClientBuilder,
            JobIngestionProperties properties
    ) {
        this.properties = properties;
        String baseUrl = properties.getArbeitnow() != null && hasText(properties.getArbeitnow().getBaseUrl())
                ? properties.getArbeitnow().getBaseUrl().trim()
                : "https://www.arbeitnow.com/api/job-board-api";
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled()
                && properties.getArbeitnow() != null
                && properties.getArbeitnow().isEnabled()
                && hasText(properties.getArbeitnow().getBaseUrl());
    }

    @Override
    public List<RawJobPosting> fetchJobs(IngestionQuery query) {
        int requestedPages = query != null ? query.maxPages() : properties.getDefaultMaxPages();
        int configuredPages = properties.getArbeitnow() != null ? properties.getArbeitnow().getMaxPages() : 3;
        int maxPages = Math.max(1, Math.min(requestedPages, Math.max(1, configuredPages)));
        int maxResults = properties.getMaxResultsPerProvider() > 0
                ? properties.getMaxResultsPerProvider()
                : Integer.MAX_VALUE;
        boolean remoteOnly = query != null && query.remoteOnly();
        Instant sinceWatermark = query != null ? query.since() : null;

        List<RawJobPosting> collected = new ArrayList<>();

        for (int page = 1; page <= maxPages; page++) {
            ArbeitnowApiResponse response = fetchPage(page);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                break;
            }

            boolean reachedOldEntries = false;
            for (ArbeitnowApiResponse.ArbeitnowJobItem item : response.data()) {
                Instant postedAt = parseCreatedAt(item.createdAt());
                if (sinceWatermark != null && postedAt != null && postedAt.isBefore(sinceWatermark)) {
                    reachedOldEntries = true;
                    continue;
                }
                if (remoteOnly && !Boolean.TRUE.equals(item.remote())) {
                    continue;
                }

                collected.add(mapToRawPosting(item, postedAt));
                if (collected.size() >= maxResults) {
                    return collected;
                }
            }

            boolean hasNext = response.links() != null && hasText(response.links().next());
            if (!hasNext || reachedOldEntries) {
                break;
            }
        }

        return collected;
    }

    private ArbeitnowApiResponse fetchPage(int page) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.queryParam("page", page).build())
                    .retrieve()
                    .body(ArbeitnowApiResponse.class);
        } catch (RestClientException ex) {
            throw new JobProviderException(
                    PROVIDER_NAME,
                    "Arbeitnow API fetch failed on page " + page + ": " + ex.getMessage(),
                    ex
            );
        }
    }

    private RawJobPosting mapToRawPosting(ArbeitnowApiResponse.ArbeitnowJobItem item, Instant postedAt) {
        List<String> tags = item.tags() == null ? List.of() : item.tags();
        List<String> jobTypes = item.jobTypes() == null ? List.of() : item.jobTypes();

        String employmentHint = String.join(", ", jobTypes);
        String arrangementHint = inferArrangementHint(item.remote(), tags, jobTypes);
        Boolean visaHint = inferVisaHint(item.visaSponsorship(), tags);

        return RawJobPosting.builder()
                .sourceProvider(PROVIDER_NAME)
                .sourceJobId(item.slug())
                .title(item.title())
                .companyName(item.companyName())
                .location(item.location())
                .remoteFlag(item.remote())
                .workArrangementHint(arrangementHint)
                .employmentTypeHint(employmentHint)
                .rawDescription(item.description())
                .tags(tags)
                .originalUrl(item.url())
                .visaSponsoredHint(visaHint)
                .postedAt(postedAt)
                .build();
    }

    private String inferArrangementHint(Boolean remoteFlag, List<String> tags, List<String> jobTypes) {
        for (String token : concat(tags, jobTypes)) {
            if (token != null) {
                String lower = token.toLowerCase(Locale.ROOT);
                if (lower.contains("hybrid")) {
                    return "HYBRID";
                }
                if (lower.contains("remote")) {
                    return "REMOTE";
                }
            }
        }
        return Boolean.TRUE.equals(remoteFlag) ? "REMOTE" : null;
    }

    private Boolean inferVisaHint(Boolean explicitFlag, List<String> tags) {
        if (Boolean.TRUE.equals(explicitFlag)) {
            return Boolean.TRUE;
        }
        for (String tag : tags) {
            if (tag != null) {
                String lower = tag.toLowerCase(Locale.ROOT);
                if (lower.contains("visa") || lower.contains("sponsorship") || lower.contains("blue card")) {
                    return Boolean.TRUE;
                }
            }
        }
        return null;
    }

    private Instant parseCreatedAt(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            long value = node.asLong();
            return value > EPOCH_MILLIS_THRESHOLD
                    ? Instant.ofEpochMilli(value)
                    : Instant.ofEpochSecond(value);
        }
        if (node.isTextual()) {
            String raw = node.asText().trim();
            if (raw.isEmpty()) {
                return null;
            }
            try {
                return Instant.parse(raw);
            } catch (DateTimeParseException ignored) {
                try {
                    long numeric = Long.parseLong(raw);
                    return numeric > EPOCH_MILLIS_THRESHOLD
                            ? Instant.ofEpochMilli(numeric)
                            : Instant.ofEpochSecond(numeric);
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    private List<String> concat(List<String> first, List<String> second) {
        List<String> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return combined;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

