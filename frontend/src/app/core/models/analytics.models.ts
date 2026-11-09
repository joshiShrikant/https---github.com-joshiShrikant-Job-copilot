export interface MarketTotals {
  readonly totalActiveJobs: number;
  readonly angularJobs: number;
  readonly frontendJobs: number;
  readonly javaJobs: number;
  readonly fullStackJobs: number;
  readonly sponsorshipOpportunities: number;
  readonly verifiedSponsorJobs: number;
  readonly englishConfirmedJobs: number;
  readonly localLanguageRequiredJobs: number;
  readonly languageUnspecifiedJobs: number;
  readonly remoteJobs: number;
  readonly hybridJobs: number;
  readonly onsiteJobs: number;
}

export interface CountryMarketInsight {
  readonly country: string;
  readonly totalJobs: number;
  readonly angularJobs: number;
  readonly frontendJobs: number;
  readonly javaJobs: number;
  readonly fullStackJobs: number;
  readonly sponsorshipOpportunities: number;
  readonly verifiedSponsorJobs: number;
  readonly englishConfirmedJobs: number;
  readonly localLanguageRequiredJobs: number;
  readonly languageUnspecifiedJobs: number;
  readonly remoteJobs: number;
  readonly hybridJobs: number;
  readonly onsiteJobs: number;
  readonly salarySampleCount: number;
  readonly primarySalaryCurrency: string | null;
  readonly salaryMin: number | null;
  readonly salaryMedian: number | null;
  readonly salaryMax: number | null;
}

export interface DiscoveryTrendPoint {
  readonly date: string;
  readonly totalDiscovered: number;
  readonly angularDiscovered: number;
  readonly frontendDiscovered: number;
  readonly javaDiscovered: number;
  readonly fullStackDiscovered: number;
  readonly sponsorshipDiscovered: number;
  readonly remoteDiscovered: number;
}

export interface SourceCoverage {
  readonly sourceProvider: string;
  readonly activeJobsCount: number;
  readonly sharePercentage: number;
  readonly lastFetchAt: string | null;
  readonly lastFetchStatus: string | null;
  readonly totalRunsLogged: number;
}

export interface MarketInsightsResponse {
  readonly generatedAt: string;
  readonly lastDataRefreshAt: string | null;
  readonly dataCoverageDisclaimer: string;
  readonly trendWindowDays: number;
  readonly availableCountries: readonly string[];
  readonly selectedCountries: readonly string[];
  readonly totals: MarketTotals;
  readonly countryComparisons: readonly CountryMarketInsight[];
  readonly discoveryTrends: readonly DiscoveryTrendPoint[];
  readonly sourceCoverage: readonly SourceCoverage[];
}

