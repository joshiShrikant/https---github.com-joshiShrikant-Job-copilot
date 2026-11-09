package com.eurojobcopilot.modules.sponsorship.dto;

import com.eurojobcopilot.modules.sponsorship.entity.RegistrySyncStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Status telemetry of an official government sponsor register synchronization")
public class RegistrySyncStatusDto {

    @Schema(description = "Official registry source code", example = "NL_IND_PUBLIC_REGISTER")
    private String registrySource;

    @Schema(description = "Country", example = "Netherlands")
    private String country;

    @Schema(description = "Latest sync execution outcome")
    private RegistrySyncStatus status;

    @Schema(description = "Authoritative register URL configured for sync")
    private String sourceUrl;

    @Schema(description = "Active sponsor entries currently stored for this register", example = "125")
    private long activeEntriesCount;

    @Schema(description = "Entries parsed in latest run", example = "125")
    private int entriesParsed;

    @Schema(description = "Entries inserted or updated in latest run", example = "12")
    private int entriesUpserted;

    @Schema(description = "Companies automatically linked/verified in latest run", example = "5")
    private int companiesLinked;

    @Schema(description = "Error or block notice if latest run did not succeed")
    private String errorMessage;

    @Schema(description = "UTC timestamp of latest sync attempt")
    private Instant syncedAt;
}

