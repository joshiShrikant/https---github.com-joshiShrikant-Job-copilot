package com.eurojobcopilot.modules.job.ingestion.adzuna;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Jackson response payload mapping for the Adzuna Jobs Search REST API v1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdzunaApiResponse(
        Long count,
        BigDecimal mean,
        List<AdzunaJobItem> results
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdzunaJobItem(
            String id,
            String title,
            String description,
            String created,
            @JsonProperty("redirect_url") String redirectUrl,
            @JsonProperty("salary_min") BigDecimal salaryMin,
            @JsonProperty("salary_max") BigDecimal salaryMax,
            @JsonProperty("salary_is_predicted") String salaryIsPredicted,
            @JsonProperty("contract_type") String contractType,
            @JsonProperty("contract_time") String contractTime,
            AdzunaCompany company,
            AdzunaLocation location,
            AdzunaCategory category
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdzunaCompany(
            @JsonProperty("display_name") String displayName
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdzunaLocation(
            @JsonProperty("display_name") String displayName,
            List<String> area
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdzunaCategory(
            String label,
            String tag
    ) {
    }
}

