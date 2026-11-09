import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AutopilotStatusResponse, PagedRecommendedJobsResponse } from '../../core/models/autopilot.models';
import { NotificationService } from '../../core/services/notification.service';
import { RecommendedJobsComponent } from './recommended-jobs.component';

describe('RecommendedJobsComponent', () => {
  let fixture: ComponentFixture<RecommendedJobsComponent>;
  let httpMock: HttpTestingController;
  let notificationService: NotificationService;

  const mockStatus: AutopilotStatusResponse = {
    autopilotEnabled: true,
    autopilotPaused: false,
    emailNotificationsEnabled: false,
    serverEmailRelayEnabled: false,
    lastAutopilotRunAt: '2026-11-09T09:00:00Z',
    minMatchScore: 70,
    preferredJobTitles: ['Senior Java Engineer'],
    preferredCountries: ['Netherlands'],
    preferredCities: ['Amsterdam'],
    recommendedJobsCount: 1,
    unreadNotificationsCount: 2,
    latestRun: null
  };

  const mockRecommendations: PagedRecommendedJobsResponse = {
    content: [
      {
        job: {
          id: 101,
          title: 'Senior Java Engineer',
          companyName: 'Adyen N.V.',
          location: 'Amsterdam, Netherlands',
          city: 'Amsterdam',
          country: 'Netherlands',
          workArrangement: 'HYBRID',
          salaryMin: 90000,
          salaryMax: 120000,
          salaryCurrency: 'EUR',
          salaryPeriod: 'YEARLY',
          sponsorStatus: 'VERIFIED_SPONSOR',
          matchPercentage: 91,
          sourceProvider: 'ARBEITNOW',
          postedAt: '2026-11-09T08:00:00Z',
          isSaved: false,
          isInterested: true
        },
        matchingSkills: ['Java', 'Spring Boot'],
        missingSkills: ['Kafka'],
        recommendationReasons: ['Strong backend match in Amsterdam with IND sponsor verification.'],
        experienceCompatibility: 'Strong Fit',
        meetsMinMatchScore: true,
        matchesPreferredCountry: true,
        matchesPreferredTitle: true,
        verifiedSponsor: true,
        sponsorshipEvidenceSummary: 'Listed on NL IND public sponsor register.',
        aiAnalysisSummary: null,
        alreadyApplied: false
      }
    ],
    pageNumber: 0,
    pageSize: 20,
    totalElements: 1,
    totalPages: 1,
    isFirst: true,
    isLast: true,
    hasNext: false,
    hasPrevious: false,
    minMatchScoreUsed: 70,
    highMatchCount: 1,
    verifiedSponsorCount: 1,
    autopilotPaused: false
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RecommendedJobsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(RecommendedJobsComponent);
    httpMock = TestBed.inject(HttpTestingController);
    notificationService = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitial(): void {
    httpMock.expectOne('/api/v1/autopilot/status').flush(mockStatus);
    httpMock.expectOne(r => r.url === '/api/v1/autopilot/recommended-jobs').flush(mockRecommendations);
    httpMock.expectOne(r => r.url === '/api/v1/autopilot/history').flush({
      content: [],
      pageNumber: 0,
      pageSize: 8,
      totalElements: 0,
      totalPages: 0,
      isFirst: true,
      isLast: true,
      hasNext: false,
      hasPrevious: false
    });
    fixture.detectChanges();
  }

  it('should render ranked recommendations, run manual autopilot scan, and toggle pause state', () => {
    fixture.detectChanges();
    flushInitial();

    const host = fixture.nativeElement as HTMLElement;
    expect(notificationService.unreadCount()).toBe(2);
    expect(host.querySelector('[data-recommended-job="101"]')?.textContent).toContain('Adyen N.V.');
    expect(host.querySelector('[data-autopilot-status-card]')?.textContent).toContain('In-App Alerts Only');

    host.querySelector<HTMLButtonElement>('[data-autopilot-run-btn]')?.click();
    const runReq = httpMock.expectOne('/api/v1/autopilot/run');
    expect(runReq.request.method).toBe('POST');
    runReq.flush({
      id: 1,
      triggerType: 'MANUAL',
      status: 'COMPLETED',
      ingestionTriggered: false,
      jobsEvaluated: 10,
      newMatchesFound: 2,
      recommendedJobsCount: 1,
      notificationsCreated: 1,
      emailDispatched: false,
      summaryMessage: 'Evaluated 10 roles, 1 new alert.',
      errorMessage: null,
      startedAt: '2026-11-09T10:00:00Z',
      completedAt: '2026-11-09T10:00:02Z'
    });
    flushInitial();
    expect(host.querySelector('[data-autopilot-notice]')?.textContent).toContain('Evaluated 10 roles');

    host.querySelector<HTMLButtonElement>('[data-autopilot-pause-btn]')?.click();
    const pauseReq = httpMock.expectOne('/api/v1/autopilot/pause');
    pauseReq.flush({ ...mockStatus, autopilotPaused: true });
    fixture.detectChanges();
    expect(host.querySelector('[data-autopilot-resume-btn]')).toBeTruthy();
  });
});
