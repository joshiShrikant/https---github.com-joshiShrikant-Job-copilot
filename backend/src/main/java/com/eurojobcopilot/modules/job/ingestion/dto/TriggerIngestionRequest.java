package com.eurojobcopilot.modules.job.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Optional parameters overriding a manually triggered job ingestion run")
public record TriggerIngestionRequest(
        @Schema(description = "Optional single provider name (e.g. ARBEITNOW, ADZUNA, or JOOBLE); omit to poll all enabled providers", example = "ADZUNA")
        String provider,

        @Schema(description = "Optional target European countries filter", example = "[\"Germany\", \"Netherlands\"]")
        @Size(max = 20, message = "At most 20 target countries may be specified")
        List<@NotBlank(message = "Country name must not be blank") String> countries,

        @Schema(description = "Optional keyword filter list", example = "[\"Java\", \"Spring Boot\"]")
        @Size(max = 20, message = "At most 20 keywords may be specified")
        List<@NotBlank(message = "Keyword must not be blank") String> keywords,

        @Schema(description = "Maximum pages to poll per provider/country", example = "2", minimum = "1", maximum = "10")
        @Min(value = 1, message = "maxPages must be at least 1")
        @Max(value = 10, message = "maxPages must not exceed 10")
        Integer maxPages,

        @Schema(description = "Restrict discovery strictly to remote roles", example = "false")
        Boolean remoteOnly,

        @Schema(description = "Lookback window in days from now", example = "7", minimum = "1", maximum = "90")
        @Min(value = 1, message = "sinceDays must be at least 1")
        @Max(value = 90, message = "sinceDays must not exceed 90")
        Integer sinceDays
) {
}

