import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardSummaryResponse } from '../../core/models/dashboard.models';
import { AuthService, SESSION_HINT_STORAGE_KEY } from '../../core/services/auth.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  const populatedSummary: DashboardSummaryResponse = {
    totalJobsDiscovered: 320,
    newJobsToday: 18,
    highMatchJobsCount: 14,
    sponsorshipOpportunitiesCount: 95,
    verifiedSponsorsCount: 42,
    savedJobsCount: 5,
    interestedJobsCount: 8,
    activeApplicationsCount: 4,
    unreadNotificationsCount: 2,
    readiness: {
      profileExists: true,
      profileCompletenessPercent: 80,
      skillsCount: 7,
      experiencesCount: 3,
      educationsCount: 1,
      resumesCount: 1,
      masterResumeApproved: true,
      preferencesConfigured: true,
      autopilotEnabled: true,
      minMatchScore: 75,
      preferredCountries: ['Germany', 'Netherlands'],
      preferredJobTitles: ['Senior Java Engineer', 'Staff Backend Engineer']
    },
    pipelineBreakdown: [
      { status: 'INTERESTED', label: 'Interested', count: 1 },
      { status: 'SAVED', label: 'Saved', count: 1 },
      { status: 'APPLIED', label: 'Applied', count: 1 },
      { status: 'TECH_INTERVIEW', label: 'Tech Interview', count: 1 },
      { status: 'REJECTED', label: 'Rejected', count: 1 }
    ],
    recentActivity: [
      {
        type: 'APPLICATION',
        title: 'Senior Java Engineer · Adyen',
        subtitle: 'Pipeline stage: Tech Interview',
        timestamp: '2026-11-09T08:15:00Z',
        targetRoute: '/applications'
      },
      {
        type: 'RESUME',
        title: 'Master Full-Stack CV 2026',
        subtitle: 'Approved Master CV · v1',
        timestamp: '2026-11-08T19:00:00Z',
        targetRoute: '/resumes'
      }
    ],
    generatedAt: '2026-11-09T08:45:00Z'
  };

  beforeEach(async () => {
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);
  });

  it('should render executive KPIs, readiness checklist, pipeline breakdown, search focus, and recent activity', () => {
    authService.login({ email: 'alex@eurojobcopilot.com', password: 'Password1!' }).subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush({
      accessToken: 'jwt-dash-1',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: {
        uuid: 'u-dash-1',
        email: 'alex@eurojobcopilot.com',
        firstName: 'Alex',
        lastName: 'Mueller',
        role: 'ROLE_USER',
        accountStatus: 'ACTIVE',
        emailVerified: true
      }
    });

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/v1/dashboard/summary');
    expect(req.request.method).toBe('GET');
    req.flush(populatedSummary);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Welcome back, Alex Mueller');
    expect(host.textContent).toContain('Autopilot Active');
    expect(host.querySelector('[data-kpi="discovered"] .kpi-value')?.textContent?.trim()).toBe('320');
    expect(host.querySelector('[data-kpi="high-match"] .kpi-value')?.textContent?.trim()).toBe('14');
    expect(host.querySelector('[data-kpi="sponsorship"] .kpi-value')?.textContent?.trim()).toBe('95');
    expect(host.querySelector('[data-kpi="applications"] .kpi-value')?.textContent?.trim()).toBe('4');

    expect(host.textContent).toContain('80%');
    expect(host.textContent).toContain('4 / 4 Ready');
    expect(host.textContent).toContain('Tech Interview');
    expect(host.textContent).toContain('Closed stages:');
    expect(host.textContent).toContain('Senior Java Engineer · Adyen');
    expect(host.textContent).toContain('Master Full-Stack CV 2026');
    expect(host.textContent).toContain('Germany');
    expect(host.textContent).toContain('Netherlands');
  });

  it('should handle empty state gracefully and support error retry + manual refresh', () => {
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    const failedReq = httpMock.expectOne('/api/v1/dashboard/summary');
    failedReq.flush(
      { message: 'Telemetry service unavailable' },
      { status: 503, statusText: 'Service Unavailable' }
    );
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.status-banner.error')?.textContent).toContain(
      'Telemetry service unavailable'
    );

    host.querySelector<HTMLButtonElement>('[data-action="retry-dashboard"]')?.click();
    const retryReq = httpMock.expectOne('/api/v1/dashboard/summary');
    retryReq.flush({
      totalJobsDiscovered: 0,
      newJobsToday: 0,
      highMatchJobsCount: 0,
      sponsorshipOpportunitiesCount: 0,
      verifiedSponsorsCount: 0,
      savedJobsCount: 0,
      interestedJobsCount: 0,
      activeApplicationsCount: 0,
      unreadNotificationsCount: 0,
      readiness: {
        profileExists: false,
        profileCompletenessPercent: 0,
        skillsCount: 0,
        experiencesCount: 0,
        educationsCount: 0,
        resumesCount: 0,
        masterResumeApproved: false,
        preferencesConfigured: false,
        autopilotEnabled: false,
        minMatchScore: 60,
        preferredCountries: [],
        preferredJobTitles: []
      },
      pipelineBreakdown: [],
      recentActivity: [],
      generatedAt: '2026-11-09T08:46:00Z'
    });
    fixture.detectChanges();

    expect(host.querySelector('.status-banner.error')).toBeNull();
    expect(host.textContent).toContain('Manual Discovery');
    expect(host.textContent).toContain('0 / 4 Ready');
    expect(host.textContent).toContain('No tracked applications yet');
    expect(host.textContent).toContain('Your activity timeline is clean');

    host.querySelector<HTMLButtonElement>('[data-action="refresh-dashboard"]')?.click();
    const refreshReq = httpMock.expectOne('/api/v1/dashboard/summary');
    refreshReq.flush(populatedSummary);
    fixture.detectChanges();

    expect(host.querySelector('[data-kpi="discovered"] .kpi-value')?.textContent?.trim()).toBe('320');
  });
});

