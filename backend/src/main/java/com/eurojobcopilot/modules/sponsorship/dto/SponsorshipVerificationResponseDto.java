package com.eurojobcopilot.modules.sponsorship.dto;

import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.entity.VisaSponsorshipStatus;
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
@Schema(description = "Complete country-aware visa sponsorship verification response with evidence, caveat, and history")
public class SponsorshipVerificationResponseDto {

    @Schema(description = "Recorded history snapshot ID", example = "108")
    private Long verificationId;

    @Schema(description = "Linked company ID when matched in company catalog", example = "42")
    private Long companyId;

    @Schema(description = "Linked job ID when evaluated against a specific vacancy", example = "1042")
    private Long jobId;

    @Schema(description = "Employer name checked", example = "Adyen N.V.")
    private String companyName;

    @Schema(description = "Normalized canonical company lookup key", example = "adyen")
    private String normalizedCompanyName;

    @Schema(description = "Canonical European country name", example = "Netherlands")
    private String country;

    @Schema(description = "ISO-2 country code", example = "NL")
    private String countryIsoCode;

    @Schema(description = "Resolved 6-state sponsorship status", example = "VERIFIED_SPONSOR")
    private VisaSponsorshipStatus status;

    @Schema(description = "Display status label", example = "Verified sponsor")
    private String statusLabel;

    @Schema(description = "Vacancy-specific sponsorship classification separate from company accreditation", example = "NOT_MENTIONED")
    private VisaSponsorshipStatus vacancyStatus;

    @Schema(description = "Display label for vacancy-level sponsorship signal", example = "Not mentioned")
    private String vacancyStatusLabel;

    @Schema(description = "True only when vacancy evidence explicitly confirms visa sponsorship", example = "false")
    private boolean vacancySponsorshipConfirmed;

    @Schema(description = "Always false when recognized sponsor status is used without vacancy confirmation", example = "false")
    private boolean vacancySponsorshipAssumed;

    @Schema(description = "Whether official sponsor accreditation was found at employer/country level", example = "true")
    private boolean companyRecognizedSponsor;

    @Schema(description = "Confidence level of verification verdict", example = "VERIFIED")
    private SponsorConfidenceLevel confidenceLevel;

    @Schema(description = "Matched official registry source", example = "NL_IND_PUBLIC_REGISTER")
    private String registrySource;

    @Schema(description = "Registry license / Chamber identifier when available", example = "KVK-34259528")
    private String registryIdentifier;

    @Schema(description = "Official legal name registered in the authority record", example = "Adyen N.V.")
    private String officialName;

    @Schema(description = "Concise human-readable summary of evidence and verification notes")
    private String evidenceNotes;

    @Schema(description = "Discrete evidence signals inspected across official registers, company records, and vacancy text")
    private List<String> evidenceSignals;

    @Schema(description = "Authoritative register or official government guidance URL")
    private String sourceUrl;

    @Schema(description = "Explicit warning clarifying whether employer sponsorship implies vacancy sponsorship")
    private String vacancyCaveat;

    @Schema(description = "UTC verification date/timestamp")
    private Instant verifiedAt;

    @Schema(description = "Regulatory profile for the queried European country")
    private CountrySponsorshipProfileDto countryProfile;

    @Schema(description = "Recent verification history entries for this employer and country")
    private List<SponsorshipVerificationHistoryItemDto> recentHistory;
}

