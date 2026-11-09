import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AutopilotService } from './autopilot.service';

describe('AutopilotService', () => {
  let service: AutopilotService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AutopilotService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should call status, pause, resume, manual run, recommendations, and history endpoints', () => {
    service.getStatus().subscribe(s => expect(s.autopilotEnabled).toBeTrue());
    httpMock.expectOne('/api/v1/autopilot/status').flush({
      autopilotEnabled: true,
      autopilotPaused: false,
      emailNotificationsEnabled: false,
      serverEmailRelayEnabled: false,
      lastAutopilotRunAt: null,
      minMatchScore: 70,
      preferredJobTitles: ['Java Engineer'],
      preferredCountries: ['Netherlands'],
      preferredCities: ['Amsterdam'],
      recommendedJobsCount: 3,
      unreadNotificationsCount: 2,
      latestRun: null
    });

    service.runScan({ refreshExternalJobs: true, analyzeTopMatchesWithAi: false }).subscribe();
    const runReq = httpMock.expectOne('/api/v1/autopilot/run');
    expect(runReq.request.method).toBe('POST');
    expect(runReq.request.body).toEqual({ refreshExternalJobs: true, analyzeTopMatchesWithAi: false });
    runReq.flush({ id: 1, triggerType: 'MANUAL', status: 'COMPLETED' });

    service.pause().subscribe();
    httpMock.expectOne('/api/v1/autopilot/pause').flush({ autopilotPaused: true });

    service.resume().subscribe();
    httpMock.expectOne('/api/v1/autopilot/resume').flush({ autopilotPaused: false });

    service
      .getRecommendedJobs({
        minMatchScore: 80,
        verifiedSponsorsOnly: true,
        excludeApplied: true,
        page: 1,
        size: 10
      })
      .subscribe();
    const recReq = httpMock.expectOne(r => r.url === '/api/v1/autopilot/recommended-jobs');
    expect(recReq.request.params.get('minMatchScore')).toBe('80');
    expect(recReq.request.params.get('verifiedSponsorsOnly')).toBe('true');
    expect(recReq.request.params.get('excludeApplied')).toBe('true');
    expect(recReq.request.params.get('page')).toBe('1');
    expect(recReq.request.params.get('size')).toBe('10');
    recReq.flush({ content: [] });

    service.getHistory(0, 5).subscribe();
    const histReq = httpMock.expectOne(r => r.url === '/api/v1/autopilot/history');
    expect(histReq.request.params.get('size')).toBe('5');
    histReq.flush({ content: [] });
  });
});

