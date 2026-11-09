package com.eurojobcopilot.modules.analytics.dto;

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
@Schema(description = "Multi-country European tech job market comparison built strictly from ingested platform data")
public class MarketInsightsResponse {

    private Instant generatedAt;
    private Instant lastDataRefreshAt;

    @Schema(description = "Explicit coverage disclaimer clarifying source limits so collected records are not mistaken for national statistics")
    private String dataCoverageDisclaimer;

    private int trendWindowDays;
    private List<String> availableCountries;
    private List<String> selectedCountries;
    private MarketTotalsDto totals;
    private List<CountryMarketInsightDto> countryComparisons;
    private List<DiscoveryTrendPointDto> discoveryTrends;
    private List<SourceCoverageDto> sourceCoverage;
}

