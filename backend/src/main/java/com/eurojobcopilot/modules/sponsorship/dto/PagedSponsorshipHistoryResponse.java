package com.eurojobcopilot.modules.sponsorship.dto;

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
@Schema(description = "Paginated verification history results for the authenticated user")
public class PagedSponsorshipHistoryResponse {

    @Schema(description = "Verification history entries for the current page")
    private List<SponsorshipVerificationHistoryItemDto> content;

    @Schema(description = "Zero-based current page index", example = "0")
    private int pageNumber;

    @Schema(description = "Requested page size", example = "20")
    private int pageSize;

    @Schema(description = "Total verification history entries matching filters", example = "12")
    private long totalElements;

    @Schema(description = "Total page count", example = "1")
    private int totalPages;

    @JsonProperty("isFirst")
    @Schema(name = "isFirst", description = "Whether this is the first page", example = "true")
    private boolean first;

    @JsonProperty("isLast")
    @Schema(name = "isLast", description = "Whether this is the last page", example = "true")
    private boolean last;

    @Schema(description = "Whether a next page exists", example = "false")
    private boolean hasNext;

    @Schema(description = "Whether a previous page exists", example = "false")
    private boolean hasPrevious;
}

