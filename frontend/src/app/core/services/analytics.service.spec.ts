import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MarketInsightsResponse } from '../models/analytics.models';
import { AnalyticsService } from './analytics.service';

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AnalyticsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should request market insights with clamped days and repeated country params', () => {
    let actual: MarketInsightsResponse | undefined;
    service.getMarketInsights(['Germany', 'Netherlands'], 60).subscribe(res => {
      actual = res;
    });

    const req = httpMock.expectOne(
      r => r.url === '/api/v1/analytics/market-insights' && r.method === 'GET'
    );
    expect(req.request.params.get('days')).toBe('60');
    expect(req.request.params.getAll('countries')).toEqual(['Germany', 'Netherlands']);

    const mockResponse: MarketInsightsResponse = {
      generatedAt: '2026-11-09T09:00:00Z',
      lastDataRefreshAt: '2026-11-09T08:30:00Z',
      dataCoverageDisclaimer: 'Metrics reflect collected platform postings only.',
      trendWindowDays: 60,
      availableCountries: ['Germany', 'Netherlands'],
      selectedCountries: ['Germany', 'Netherlands'],
      totals: {
        totalActiveJobs: 10,
        angularJobs: 3,
        frontendJobs: 4,
        javaJobs: 5,
        fullStackJobs: 2,
        sponsorshipOpportunities: 4,
        verifiedSponsorJobs: 2,
        englishConfirmedJobs: 6,
        localLanguageRequiredJobs: 1,
        languageUnspecifiedJobs: 3,
        remoteJobs: 3,
        hybridJobs: 5,
        onsiteJobs: 2
      },
      countryComparisons: [],
      discoveryTrends: [],
      sourceCoverage: []
    };
    req.flush(mockResponse);
    expect(actual).toEqual(mockResponse);
  });
});

