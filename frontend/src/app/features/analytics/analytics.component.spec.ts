import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MarketInsightsResponse } from '../../core/models/analytics.models';
import { AnalyticsComponent } from './analytics.component';

describe('AnalyticsComponent', () => {
  let fixture: ComponentFixture<AnalyticsComponent>;
  let httpMock: HttpTestingController;

  const mockInsights: MarketInsightsResponse = {
    generatedAt: '2026-11-09T10:00:00Z',
    lastDataRefreshAt: '2026-11-09T09:30:00Z',
    dataCoverageDisclaimer:
      'Collected from integrated European job feeds only; does not represent official national labor statistics.',
    trendWindowDays: 30,
    availableCountries: ['Germany', 'Netherlands'],
    selectedCountries: ['Germany', 'Netherlands'],
    totals: {
      totalActiveJobs: 15,
      angularJobs: 4,
      frontendJobs: 6,
      javaJobs: 7,
      fullStackJobs: 3,
      sponsorshipOpportunities: 8,
      verifiedSponsorJobs: 5,
      englishConfirmedJobs: 10,
      localLanguageRequiredJobs: 2,
      languageUnspecifiedJobs: 3,
      remoteJobs: 4,
      hybridJobs: 8,
      onsiteJobs: 3
    },
    countryComparisons: [
      {
        country: 'Germany',
        totalJobs: 9,
        angularJobs: 2,
        frontendJobs: 3,
        javaJobs: 5,
        fullStackJobs: 2,
        sponsorshipOpportunities: 4,
        verifiedSponsorJobs: 2,
        englishConfirmedJobs: 5,
        localLanguageRequiredJobs: 2,
        languageUnspecifiedJobs: 2,
        remoteJobs: 2,
        hybridJobs: 5,
        onsiteJobs: 2,
        salarySampleCount: 6,
        primarySalaryCurrency: 'EUR',
        salaryMin: 70000,
        salaryMedian: 92000,
        salaryMax: 120000
      },
      {
        country: 'Netherlands',
        totalJobs: 6,
        angularJobs: 2,
        frontendJobs: 3,
        javaJobs: 2,
        fullStackJobs: 1,
        sponsorshipOpportunities: 4,
        verifiedSponsorJobs: 3,
        englishConfirmedJobs: 5,
        localLanguageRequiredJobs: 0,
        languageUnspecifiedJobs: 1,
        remoteJobs: 2,
        hybridJobs: 3,
        onsiteJobs: 1,
        salarySampleCount: 0,
        primarySalaryCurrency: null,
        salaryMin: null,
        salaryMedian: null,
        salaryMax: null
      }
    ],
    discoveryTrends: [
      {
        date: '2026-11-08',
        totalDiscovered: 5,
        angularDiscovered: 2,
        frontendDiscovered: 2,
        javaDiscovered: 2,
        fullStackDiscovered: 1,
        sponsorshipDiscovered: 3,
        remoteDiscovered: 1
      },
      {
        date: '2026-11-09',
        totalDiscovered: 10,
        angularDiscovered: 2,
        frontendDiscovered: 4,
        javaDiscovered: 5,
        fullStackDiscovered: 2,
        sponsorshipDiscovered: 5,
        remoteDiscovered: 3
      }
    ],
    sourceCoverage: [
      {
        sourceProvider: 'ARBEITNOW',
        activeJobsCount: 15,
        sharePercentage: 100,
        lastFetchAt: '2026-11-09T09:30:00Z',
        lastFetchStatus: 'SUCCESS',
        totalRunsLogged: 3
      }
    ]
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalyticsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(AnalyticsComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should render market insights, disclaimer, SVG trend chart, salary table, and reload on filter selection', () => {
    fixture.detectChanges();
    const initialReq = httpMock.expectOne(r => r.url === '/api/v1/analytics/market-insights');
    expect(initialReq.request.params.get('days')).toBe('30');
    initialReq.flush(mockInsights);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-analytics-total-jobs]')?.textContent?.trim()).toBe('15');
    expect(host.querySelector('[data-analytics-disclaimer]')?.textContent).toContain(
      'does not represent official national labor statistics'
    );
    expect(host.querySelector('[data-analytics-trend-chart] svg')).toBeTruthy();
    expect(host.querySelector('[data-country-row="Germany"]')?.textContent).toContain(
      'EUR 70k–120k · median EUR 92k'
    );
    expect(host.querySelector('[data-country-row="Netherlands"]')?.textContent).toContain(
      'Insufficient salary samples'
    );
    expect(host.querySelector('[data-analytics-sources]')?.textContent).toContain('ARBEITNOW');

    // Toggle country filter & lookback window
    host.querySelector<HTMLButtonElement>('[data-analytics-country="Germany"]')?.click();
    const filteredReq = httpMock.expectOne(r => r.url === '/api/v1/analytics/market-insights');
    expect(filteredReq.request.params.getAll('countries')).toEqual(['Germany']);
    filteredReq.flush({ ...mockInsights, selectedCountries: ['Germany'] });
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('[data-analytics-days="90"]')?.click();
    const daysReq = httpMock.expectOne(r => r.url === '/api/v1/analytics/market-insights');
    expect(daysReq.request.params.get('days')).toBe('90');
    daysReq.flush({ ...mockInsights, trendWindowDays: 90 });
    fixture.detectChanges();
  });
});

