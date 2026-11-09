package com.eurojobcopilot.modules.job.dto;

import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.entity.VisaSponsorshipStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@Schema(description = "Combined job-level and company-registry visa sponsorship intelligence breakdown")
public class SponsorshipBreakdownDto {

    @Schema(description = "Effective sponsorship classification on the job listing", example = "VERIFIED_SPONSOR")
    private JobSponsorStatus sponsorStatus;

    @Schema(description = "Granular 6-state sponsorship status", example = "VERIFIED_SPONSOR")
    private VisaSponsorshipStatus detailedStatus;

    @Schema(description = "Display label for detailedStatus", example = "Verified sponsor")
    private String detailedStatusLabel;

    @Schema(description = "Vacancy-level sponsorship signal evaluated independently from employer accreditation", example = "NOT_MENTIONED")
    private VisaSponsorshipStatus vacancyStatus;

    @Schema(description = "Display label for vacancyStatus", example = "Not mentioned")
    private String vacancyStatusLabel;

    @Schema(description = "True only when the specific vacancy text/AI analysis explicitly mentions sponsorship", example = "false")
    private boolean vacancySponsorshipConfirmed;

    @Schema(description = "Always false unless explicitly confirmed at vacancy level", example = "false")
    private boolean vacancySponsorshipAssumed;

    @JsonProperty("isVerifiedSponsor")
    @Schema(name = "isVerifiedSponsor", description = "Whether verified sponsor evidence exists and is not contradicted by the vacancy", example = "true")
    private boolean verifiedSponsor;

    @Schema(description = "Sponsorship confidence classification", example = "VERIFIED")
    private SponsorConfidenceLevel confidenceLevel;

    @Schema(description = "Human-readable sponsorship verdict explaining evidence and confidence")
    private String summary;

    @Schema(description = "Explicit warning reminding candidates not to assume recognized sponsor accreditation covers every vacancy")
    private String vacancyCaveat;

    @Schema(description = "Country-specific statutory visa pathway and register summary")
    private String countryGuideSummary;

    @Schema(description = "Authoritative official register or government guidance URL for the job's country")
    private String officialRegisterUrl;

    @Schema(description = "Verification or latest registry check UTC timestamp")
    private Instant verifiedAt;

    @Schema(description = "Concrete sponsorship signals detected from registries and posting copy")
    private List<String> evidenceSignals;

    @Schema(description = "Linked government sponsor registry records for this employer")
    private List<SponsorshipRegistryRecordDto> registryRecords;
}

