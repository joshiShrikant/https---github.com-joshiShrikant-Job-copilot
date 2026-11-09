package com.eurojobcopilot.modules.job.ingestion.arbeitnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Jackson response payload mapping for the Arbeitnow Job Board JSON API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArbeitnowApiResponse(
        List<ArbeitnowJobItem> data,
        ArbeitnowLinks links,
        ArbeitnowMeta meta
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArbeitnowJobItem(
            String slug,
            @JsonProperty("company_name") String companyName,
            String title,
            String description,
            Boolean remote,
            String url,
            List<String> tags,
            @JsonProperty("job_types") List<String> jobTypes,
            String location,
            @JsonProperty("created_at") JsonNode createdAt,
            @JsonProperty("visa_sponsorship") Boolean visaSponsorship
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArbeitnowLinks(
            String first,
            String last,
            String prev,
            String next
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArbeitnowMeta(
            @JsonProperty("current_page") Integer currentPage,
            @JsonProperty("last_page") Integer lastPage,
            @JsonProperty("per_page") Integer perPage,
            Integer total
    ) {
    }
}

