package com.eurojobcopilot.modules.company.dto;

import com.eurojobcopilot.modules.company.entity.CompanyPriorityTier;
import com.eurojobcopilot.modules.job.dto.SponsorshipRegistryRecordDto;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
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
@Schema(description = "Detailed employer intelligence dossier including sponsorship registry evidence, active hiring footprint, tech stack, and user bookmark notes")
public class CompanyDetailDto {

    @Schema(description = "Unique company identifier", example = "12")
    private Long id;

    @Schema(description = "Employer display name", example = "Adyen")
    private String name;

    @Schema(description = "Canonical lowercase deduplication name", example = "adyen")
    private String normalizedName;

    @Schema(description = "Employer profile overview")
    private String description;

    @Schema(description = "Sanitized corporate website URL", example = "https://www.adyen.com")
    private String websiteUrl;

    @Schema(description = "Sanitized employer careers page URL", example = "https://careers.adyen.com")
    private String careersUrl;

    @Schema(description = "Sanitized employer logo URL", example = "https://www.adyen.com/logo.png")
    private String logoUrl;

    @Schema(description = "Primary headquarters country", example = "Netherlands")
    private String headquartersCountry;

    @Schema(description = "Primary headquarters city", example = "Amsterdam")
    private String headquartersCity;

    @Schema(description = "Industry sector", example = "FinTech & Payments")
    private String industry;

    @Schema(description = "Organization headcount band", example = "1000-5000")
    private String companySize;

    @JsonProperty("isVerifiedSponsor")
    @Schema(name = "isVerifiedSponsor", description = "Whether the employer has company-level recognized visa sponsor verification", example = "true")
    private boolean verifiedSponsor;

    @Schema(description = "Sponsor confidence classification", example = "VERIFIED")
    private SponsorConfidenceLevel sponsorConfidenceLevel;

    @Schema(description = "Count of currently active, non-expired jobs", example = "7")
    private long activeJobsCount;

    @Schema(description = "Count of active jobs indicating sponsorship availability", example = "5")
    private long sponsorshipJobsCount;

    @Schema(description = "Count of active remote openings", example = "2")
    private long remoteJobsCount;

    @Schema(description = "Consolidated tech stack combining curated employer stack and active role requirements")
    private List<String> techStack;

    @Schema(description = "Distinct countries where this employer has headquarters or active openings")
    private List<String> activeCountries;

    @Schema(description = "Distinct cities where this employer has headquarters or active openings")
    private List<String> activeCities;

    @Schema(description = "Mandatory vacancy-level non-assumption disclaimer clarifying that sponsor status does not guarantee every job offers sponsorship")
    private String vacancyCaveat;

    @Schema(description = "Country-specific immigration and work-permit context for the employer's headquarters country")
    private String countryGuideSummary;

    @Schema(description = "Official government immigration register or guidance URL for the headquarters country")
    private String officialRegisterUrl;

    @Schema(description = "Matched official government sponsor registry records (persisted and live IND register)")
    private List<SponsorshipRegistryRecordDto> registryRecords;

    @JsonProperty("isSaved")
    @Schema(name = "isSaved", description = "Whether the current user bookmarked this employer as a target company", example = "true")
    private boolean saved;

    @Schema(description = "UTC timestamp when bookmarked by the current user")
    private Instant savedAt;

    @Schema(description = "User-assigned target priority tier", example = "TOP_PRIORITY")
    private CompanyPriorityTier priorityTier;

    @Schema(description = "Personal notes saved by the current user about this employer")
    private String userNotes;

    @Schema(description = "UTC timestamp when employer record was created")
    private Instant createdAt;

    @Schema(description = "UTC timestamp when employer record was last updated")
    private Instant updatedAt;
}

