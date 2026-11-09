package com.eurojobcopilot.modules.autopilot.dto;

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
@Schema(description = "Paginated ranked autopilot job recommendations with preference threshold summary")
public class PagedRecommendedJobsResponse {

    private List<RecommendedJobItemDto> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;

    @JsonProperty("isFirst")
    @Schema(name = "isFirst")
    private boolean first;

    @JsonProperty("isLast")
    @Schema(name = "isLast")
    private boolean last;

    private boolean hasNext;
    private boolean hasPrevious;
    private int minMatchScoreUsed;
    private long highMatchCount;
    private long verifiedSponsorCount;
    private boolean autopilotPaused;
}

