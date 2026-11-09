package com.eurojobcopilot.modules.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Country-level European job market comparison metrics built strictly from ingested listings")
public class CountryMarketInsightDto {

    @Schema(example = "Germany")
    private String country;

    private long totalJobs;
    private long angularJobs;
    private long frontendJobs;
    private long javaJobs;
    private long fullStackJobs;
    private long sponsorshipOpportunities;
    private long verifiedSponsorJobs;
    private long englishConfirmedJobs;
    private long localLanguageRequiredJobs;
    private long languageUnspecifiedJobs;
    private long remoteJobs;
    private long hybridJobs;
    private long onsiteJobs;

    @Schema(description = "Count of jobs contributing normalized annual salary data in primarySalaryCurrency")
    private long salarySampleCount;

    @Schema(example = "EUR")
    private String primarySalaryCurrency;

    @Schema(example = "65000.00")
    private BigDecimal salaryMin;

    @Schema(example = "88000.00")
    private BigDecimal salaryMedian;

    @Schema(example = "125000.00")
    private BigDecimal salaryMax;
}

