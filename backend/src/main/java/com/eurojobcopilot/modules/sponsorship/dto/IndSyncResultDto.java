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
@Schema(description = "Execution summary of a Netherlands IND recognized sponsor register sync run")
public class IndSyncResultDto {

    @Schema(description = "Official register source code", example = "NL_IND_PUBLIC_REGISTER")
    private String registrySource;

    @Schema(description = "Country", example = "Netherlands")
    private String country;

    @Schema(description = "Sync execution status", example = "SUCCESS")
    private RegistrySyncStatus status;

    @Schema(description = "Source URL queried during sync")
    private String sourceUrl;

    @Schema(description = "Count of entries parsed from IND payload", example = "120")
    private int entriesParsed;

    @Schema(description = "Count of entries inserted or updated", example = "120")
    private int entriesUpserted;

    @Schema(description = "Count of stale active entries deactivated", example = "3")
    private int entriesDeactivated;

    @Schema(description = "Count of existing company records linked to IND sponsorship records", example = "14")
    private int companiesLinked;

    @Schema(description = "Execution duration in milliseconds", example = "640")
    private long executionDurationMs;

    @Schema(description = "Informational message or failure reason")
    private String message;

    @Schema(description = "UTC timestamp when sync finished")
    private Instant syncedAt;
}

