package com.eurojobcopilot.modules.job.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Paginated slice of historical job ingestion fetch logs")
public record PagedFetchLogsResponse(
        @Schema(description = "Fetch log entries ordered newest first")
        List<JobFetchLogDto> items,

        @Schema(description = "Zero-based page index", example = "0")
        int page,

        @Schema(description = "Page size", example = "20")
        int size,

        @Schema(description = "Total matching log records", example = "42")
        long totalElements,

        @Schema(description = "Total available pages", example = "3")
        int totalPages,

        @Schema(description = "Whether this is the last page", example = "false")
        boolean last
) {
}

