package com.eurojobcopilot.modules.sponsorship.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Country-specific European visa sponsorship rules, official registers, and statutory permit guidance")
public class CountrySponsorshipProfileDto {

    @Schema(description = "Canonical country name", example = "Netherlands")
    private String country;

    @Schema(description = "ISO 3166-1 alpha-2 country code", example = "NL")
    private String isoCode;

    @Schema(description = "Whether the country publishes an official accredited/certified sponsor framework", example = "true")
    private boolean officialRegisterAvailable;

    @Schema(description = "Registry or authoritative source code", example = "NL_IND_PUBLIC_REGISTER")
    private String registrySource;

    @Schema(description = "Official register or portal display name")
    private String registryName;

    @Schema(description = "Primary immigration authority")
    private String primaryAuthority;

    @Schema(description = "Authoritative official government source URL")
    private String officialSourceUrl;

    @Schema(description = "Direct official public register or employer portal URL")
    private String publicRegisterUrl;

    @Schema(description = "Verification strategy used for this country")
    private String verificationMethod;

    @Schema(description = "Primary statutory skilled-worker / visa pathways")
    private List<String> visaPathways;

    @Schema(description = "Regulatory explanation of how employer sponsorship works in this country")
    private String guidanceSummary;

    @Schema(description = "Role-level caveat reminding users that employer accreditation never guarantees every vacancy offers sponsorship")
    private String vacancyRuleWarning;

    @Schema(description = "Count of active synced official registry entries stored locally for this country", example = "42")
    private long syncedEntriesCount;

    @Schema(description = "Latest official registry synchronization timestamp when available")
    private Instant lastSyncedAt;
}

