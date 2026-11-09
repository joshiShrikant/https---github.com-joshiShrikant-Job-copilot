package com.eurojobcopilot.modules.recruiter.dto;

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
@Schema(description = "Paginated recruiter contacts directory")
public class PagedRecruitersResponse {

    @Schema(description = "Recruiter contact cards on the requested page")
    private List<RecruiterResponse> content;

    @Schema(description = "Zero-based page index", example = "0")
    private int pageNumber;

    @Schema(description = "Page size", example = "20")
    private int pageSize;

    @Schema(description = "Total matching recruiters", example = "8")
    private long totalElements;

    @Schema(description = "Total pages", example = "1")
    private int totalPages;

    @JsonProperty("isFirst")
    @Schema(name = "isFirst", description = "Whether this is the first page")
    private boolean first;

    @JsonProperty("isLast")
    @Schema(name = "isLast", description = "Whether this is the last page")
    private boolean last;

    @JsonProperty("hasNext")
    @Schema(name = "hasNext", description = "Whether another page exists")
    private boolean hasNext;

    @JsonProperty("hasPrevious")
    @Schema(name = "hasPrevious", description = "Whether a previous page exists")
    private boolean hasPrevious;
}

