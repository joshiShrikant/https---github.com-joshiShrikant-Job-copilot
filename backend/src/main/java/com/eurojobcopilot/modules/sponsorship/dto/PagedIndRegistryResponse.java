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
@Schema(description = "Paginated search response for synced Netherlands IND recognized sponsors")
public class PagedIndRegistryResponse {

    @Schema(description = "Matching active IND recognized sponsor records")
    private List<OfficialSponsorRegistryEntryDto> content;

    @Schema(description = "Latest Netherlands IND register sync status")
    private RegistrySyncStatusDto lastSyncStatus;

    @Schema(description = "Zero-based page index", example = "0")
    private int pageNumber;

    @Schema(description = "Page size", example = "20")
    private int pageSize;

    @Schema(description = "Total matching active records", example = "25")
    private long totalElements;

    @Schema(description = "Total pages", example = "2")
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

