package com.eurojobcopilot.modules.job.ingestion.jooble;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Jackson response payload mapping for the Jooble Job Search REST API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JoobleApiResponse(
        @JsonProperty("totalCount") Long totalCount,
        List<JoobleJobItem> jobs
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JoobleJobItem(
            JsonNode id,
            String title,
            String location,
            String snippet,
            String salary,
            String source,
            String type,
            String link,
            String company,
            String updated
    ) {
    }
}

