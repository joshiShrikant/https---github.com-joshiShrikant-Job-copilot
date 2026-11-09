package com.eurojobcopilot.modules.company.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated European employer intelligence directory results with platform totals")
public class PagedCompaniesResponse {

    @Schema(description = "Employer intelligence summaries for the requested page")
    private List<CompanySummaryDto> content;

    @Schema(description = "Zero-based page index", example = "0")
    private int pageNumber;

    @Schema(description = "Requested page size", example = "20")
    private int pageSize;

    @Schema(description = "Total number of matching employers across all pages", example = "42")
    private long totalElements;

    @Schema(description = "Total number of available pages", example = "3")
    private int totalPages;

    @JsonProperty("isFirst")
    @Schema(name = "isFirst", description = "Whether this is the first page", example = "true")
    private boolean first;

    @JsonProperty("isLast")
    @Schema(name = "isLast", description = "Whether this is the last page", example = "false")
    private boolean last;

    @JsonProperty("hasNext")
    @Schema(name = "hasNext", description = "Whether a subsequent page is available", example = "true")
    private boolean hasNext;

    @JsonProperty("hasPrevious")
    @Schema(name = "hasPrevious", description = "Whether a previous page is available", example = "false")
    private boolean hasPrevious;

    @Schema(description = "Total number of verified visa sponsor employers indexed across the directory", example = "18")
    private long verifiedSponsorsTotal;

    @Schema(description = "Total number of target employers saved by the current user", example = "4")
    private long savedCompaniesTotal;
}

