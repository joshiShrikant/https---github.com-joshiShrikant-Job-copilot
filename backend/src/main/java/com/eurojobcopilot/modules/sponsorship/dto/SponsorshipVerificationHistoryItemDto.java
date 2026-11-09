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
@Schema(description = "Persisted audit snapshot of a visa sponsorship verification query")
public class SponsorshipVerificationHistoryItemDto {

    @Schema(description = "Verification history entry ID", example = "108")
    private Long id;

    @Schema(description = "Linked normalized company ID when known", example = "42")
    private Long companyId;

    @Schema(description = "Linked job posting ID when verified against a specific vacancy", example = "1042")
    private Long jobId;

    @Schema(description = "Verified company name", example = "Adyen N.V.")
    private String companyName;

    @Schema(description = "Normalized canonical company name key", example = "adyen")
    private String normalizedCompanyName;

    @Schema(description = "Target European country", example = "Netherlands")
    private String country;

    @Schema(description = "ISO-2 country code", example = "NL")
    private String countryIsoCode;

    @Schema(description = "Resolved 6-state visa sponsorship status", example = "VERIFIED_SPONSOR")
    private VisaSponsorshipStatus status;

    @Schema(description = "Human-readable status label", example = "Verified sponsor")
    private String statusLabel;

    @Schema(description = "Vacancy-level sponsorship status independent of employer accreditation", example = "NOT_MENTIONED")
    private VisaSponsorshipStatus vacancyStatus;

    @Schema(description = "True only when specific vacancy evidence explicitly mentions visa sponsorship", example = "false")
    private boolean vacancySponsorshipConfirmed;

    @Schema(description = "Always false unless explicitly confirmed at vacancy level", example = "false")
    private boolean vacancySponsorshipAssumed;

    @Schema(description = "Confidence level in verification verdict", example = "VERIFIED")
    private SponsorConfidenceLevel confidenceLevel;

    @Schema(description = "Registry source identifier", example = "NL_IND_PUBLIC_REGISTER")
    private String registrySource;

    @Schema(description = "Official register identifier such as IND KvK number", example = "KVK-34259528")
    private String registryIdentifier;

    @Schema(description = "Official registered entity name when matched", example = "Adyen N.V.")
    private String officialRegisteredName;

    @Schema(description = "Summary of collected evidence")
    private String evidenceSummary;

    @Schema(description = "Concrete evidence signals detected during verification")
    private List<String> evidenceSignals;

    @Schema(description = "Official register or authoritative source URL")
    private String sourceUrl;

    @Schema(description = "Explicit warning against assuming recognized sponsorship covers all vacancies")
    private String vacancyCaveat;

    @Schema(description = "UTC verification timestamp")
    private Instant verifiedAt;
}

