package com.eurojobcopilot.modules.sponsorship.dto;

import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
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
@Schema(description = "Synced entry from an official government sponsor register")
public class OfficialSponsorRegistryEntryDto {

    @Schema(description = "Internal entry ID", example = "1")
    private Long id;

    @Schema(description = "Country", example = "Netherlands")
    private String country;

    @Schema(description = "Official register source code", example = "NL_IND_PUBLIC_REGISTER")
    private String registrySource;

    @Schema(description = "Registry identifier such as KvK number", example = "KVK-34259528")
    private String registryIdentifier;

    @Schema(description = "Official registered entity name", example = "Adyen N.V.")
    private String officialName;

    @Schema(description = "Normalized search key", example = "adyen")
    private String normalizedName;

    @Schema(description = "Official sponsor license status", example = "RECOGNIZED_SPONSOR")
    private SponsorshipRecordStatus status;

    @Schema(description = "Official register URL")
    private String sourceUrl;

    @Schema(description = "Whether the entry is currently active in the latest register snapshot", example = "true")
    private boolean active;

    @Schema(description = "UTC timestamp when this entry was synced")
    private Instant syncedAt;
}

