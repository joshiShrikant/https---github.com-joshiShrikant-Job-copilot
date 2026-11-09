package com.eurojobcopilot.modules.job.ingestion;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Parameter object governing provider fetch queries during an ingestion run.
 */
@Builder
public record IngestionQuery(
        List<String> countries,
        List<String> keywords,
        int maxPages,
        int pageSize,
        boolean remoteOnly,
        Instant since
) {
    public IngestionQuery {
        countries = countries == null ? List.of() : List.copyOf(countries);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        maxPages = maxPages > 0 ? maxPages : 1;
        pageSize = pageSize > 0 ? pageSize : 50;
    }

    public static IngestionQuery defaults(List<String> targetCountries, int maxPages) {
        return IngestionQuery.builder()
                .countries(targetCountries)
                .keywords(List.of("Java", "Spring", "Angular", "Backend", "Full Stack", "Software Engineer"))
                .maxPages(maxPages)
                .pageSize(50)
                .remoteOnly(false)
                .build();
    }
}

