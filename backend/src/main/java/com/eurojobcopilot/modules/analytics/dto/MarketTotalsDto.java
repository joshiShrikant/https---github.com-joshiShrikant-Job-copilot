package com.eurojobcopilot.modules.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated role counts across all selected or discovered European jobs")
public class MarketTotalsDto {

    private long totalActiveJobs;
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
}

