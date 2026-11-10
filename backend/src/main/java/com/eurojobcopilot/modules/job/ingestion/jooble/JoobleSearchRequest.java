package com.eurojobcopilot.modules.job.ingestion.jooble;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body sent to the Jooble Job Search REST API ({@code POST /{apiKey}}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JoobleSearchRequest(
        String keywords,
        String location,
        Integer page,
        @JsonProperty("ResultOnPage") Integer resultOnPage
) {
}

