package com.eurojobcopilot.modules.job.dto;

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
@Schema(description = "Paginated job search results aligned with frontend PageResponse<JobSummary>")
public class PagedJobsResponse {

    @Schema(description = "Matching jobs for the requested page")
    private List<JobSummaryDto> content;

    @Schema(description = "Zero-based page index", example = "0")
    private int pageNumber;

    @Schema(description = "Requested page size", example = "20")
    private int pageSize;

    @Schema(description = "Total number of matching jobs across all pages", example = "128")
    private long totalElements;

    @Schema(description = "Total number of available pages", example = "7")
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
}

