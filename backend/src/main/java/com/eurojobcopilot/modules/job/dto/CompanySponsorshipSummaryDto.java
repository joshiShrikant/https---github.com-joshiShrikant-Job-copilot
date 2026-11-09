package com.eurojobcopilot.modules.job.dto;

import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Normalized employer company profile and sponsorship verification status")
public class CompanySponsorshipSummaryDto {

    @Schema(description = "Company identifier", example = "15")
    private Long id;

    @Schema(description = "Company display name", example = "Adyen N.V.")
    private String name;

    @Schema(description = "Primary company website URL", example = "https://www.adyen.com")
    private String websiteUrl;

    @Schema(description = "Official careers portal URL", example = "https://careers.adyen.com")
    private String careersUrl;

    @Schema(description = "Headquarters country", example = "Netherlands")
    private String headquartersCountry;

    @Schema(description = "Headquarters city", example = "Amsterdam")
    private String headquartersCity;

    @Schema(description = "Industry classification", example = "FinTech Payments")
    private String industry;

    @Schema(description = "Estimated company headcount band", example = "1000-5000")
    private String companySize;

    @Schema(description = "Company logo URL")
    private String logoUrl;

    @JsonProperty("isVerifiedSponsor")
    @Schema(name = "isVerifiedSponsor", description = "Whether the employer is verified in official government sponsor registries", example = "true")
    private boolean verifiedSponsor;

    @Schema(description = "Confidence level assigned to the employer sponsorship profile", example = "VERIFIED")
    private SponsorConfidenceLevel sponsorConfidenceLevel;
}

