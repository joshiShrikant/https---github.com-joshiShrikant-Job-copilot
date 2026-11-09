package com.eurojobcopilot.modules.company.dto;

import com.eurojobcopilot.modules.company.entity.CompanyPriorityTier;
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
@Schema(description = "Employer intelligence summary card with sponsorship confidence, open job metrics, and user bookmark state")
public class CompanySummaryDto {

    @Schema(description = "Unique company identifier", example = "12")
    private Long id;

    @Schema(description = "Employer display name", example = "Adyen")
    private String name;

    @Schema(description = "Primary headquarters country", example = "Netherlands")
    private String headquartersCountry;

    @Schema(description = "Primary headquarters city", example = "Amsterdam")
    private String headquartersCity;

    @Schema(description = "Industry vertical", example = "FinTech & Payments")
    private String industry;

    @Schema(description = "Approximate organization headcount band", example = "1000-5000")
    private String companySize;

    @Schema(description = "Sanitized corporate website URL", example = "https://www.adyen.com")
    private String websiteUrl;

    @Schema(description = "Sanitized employer careers portal URL", example = "https://careers.adyen.com")
    private String careersUrl;

    @Schema(description = "Sanitized employer logo URL", example = "https://www.adyen.com/logo.png")
    private String logoUrl;

    @JsonProperty("isVerifiedSponsor")
    @Schema(name = "isVerifiedSponsor", description = "Whether the employer holds recognized visa sponsor verification at the company level", example = "true")
    private boolean verifiedSponsor;

    @Schema(description = "Sponsor confidence classification", example = "VERIFIED")
    private SponsorConfidenceLevel sponsorConfidenceLevel;

    @Schema(description = "Count of currently active, non-expired jobs for this employer", example = "7")
    private long activeJobsCount;

    @Schema(description = "Count of active jobs mentioning sponsorship or linked to verified sponsor status", example = "5")
    private long sponsorshipJobsCount;

    @Schema(description = "Count of active remote/hybrid-eligible jobs", example = "3")
    private long remoteJobsCount;

    @Schema(description = "Combined curated and active-job derived technology stack", example = "[\"Java\", \"Spring Boot\", \"PostgreSQL\", \"Kafka\"]")
    private List<String> techStack;

    @Schema(description = "Distinct European countries with active openings or headquarters", example = "[\"Netherlands\", \"Germany\"]")
    private List<String> activeCountries;

    @Schema(description = "Countries where official sponsor registry records exist for this employer", example = "[\"Netherlands\"]")
    private List<String> registryCountries;

    @JsonProperty("isSaved")
    @Schema(name = "isSaved", description = "Whether the current user bookmarked this employer as a target company", example = "true")
    private boolean saved;

    @Schema(description = "UTC timestamp when the current user bookmarked this employer")
    private Instant savedAt;

    @Schema(description = "User-assigned target priority tier when bookmarked", example = "TOP_PRIORITY")
    private CompanyPriorityTier priorityTier;
}

