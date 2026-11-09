import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { SESSION_HINT_STORAGE_KEY, AuthService } from './core/services/auth.service';
import { THEME_STORAGE_KEY } from './core/services/theme.service';

describe('App Root Shell & Guarded Routing', () => {
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(async () => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations()
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(THEME_STORAGE_KEY);
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
  });

  function authenticateMockUser(): void {
    authService.login({ email: 'alex@eurojobcopilot.com', password: 'Password1!' }).subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush({
      accessToken: 'jwt-app-test',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: {
        uuid: 'uuid-1',
        email: 'alex@eurojobcopilot.com',
        firstName: 'Alex',
        lastName: 'Mueller',
        role: 'ROLE_USER',
        accountStatus: 'ACTIVE',
        emailVerified: true
      }
    });
  }

  it('should create the root app component', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should redirect unauthenticated navigation on /dashboard to /auth/login with returnUrl', async () => {
    const router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    await router.navigateByUrl('/dashboard');
    fixture.detectChanges();
    httpMock.expectNone('/api/v1/dashboard/summary');

    expect(router.url).toBe('/auth/login?returnUrl=%2Fdashboard');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-auth-layout')).toBeTruthy();
    expect(compiled.querySelector('app-shell')).toBeNull();
  });

  it('should render the workspace shell on /dashboard when authenticated and guard /auth/login back to /dashboard', async () => {
    authenticateMockUser();

    const router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    await router.navigateByUrl('/dashboard');
    fixture.detectChanges();
    TestBed.flushEffects();

    httpMock
      .match(req => req.method === 'GET' && req.url === '/api/v1/notifications/unread-count')
      .forEach(req => {
        req.flush({ unreadCount: 0 });
      });

    httpMock.match(req => req.method === 'GET' && req.url === '/api/v1/dashboard/summary').forEach(req => {
      req.flush({
        totalJobsDiscovered: 10,
        newJobsToday: 2,
        highMatchJobsCount: 1,
        sponsorshipOpportunitiesCount: 4,
        verifiedSponsorsCount: 2,
        savedJobsCount: 1,
        interestedJobsCount: 1,
        activeApplicationsCount: 0,
        unreadNotificationsCount: 0,
        readiness: {
          profileExists: true,
          profileCompletenessPercent: 50,
          skillsCount: 2,
          experiencesCount: 1,
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
        generatedAt: '2026-11-09T09:00:00Z'
      });
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-shell')).toBeTruthy();
    expect(compiled.querySelector('app-sidebar')).toBeTruthy();
    expect(compiled.querySelector('app-auth-layout')).toBeNull();

    // Guest guard redirects authenticated user away from login
    await router.navigateByUrl('/auth/login');
    fixture.detectChanges();
    expect(router.url).toBe('/dashboard');
  });

  it('should render AuthLayoutComponent without workspace sidebar across all auth routes when guest', async () => {
    const router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    for (const path of [
      '/auth/login',
      '/auth/register',
      '/auth/forgot-password',
      '/auth/reset-password',
      '/login'
    ]) {
      const ok = await router.navigateByUrl(path);
      expect(ok).withContext(`Expected navigation success for ${path}`).toBeTrue();
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('app-auth-layout')).toBeTruthy();
      expect(compiled.querySelector('app-shell')).toBeNull();
      expect(compiled.querySelector('app-sidebar')).toBeNull();
    }
  });

  it('should bind ?token query param into ResetPasswordComponent via router input binding', async () => {
    const router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    await router.navigateByUrl('/auth/reset-password?token=tok_from_query_param');
    fixture.detectChanges();

    const tokenInput = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
      '#reset-token'
    );
    expect(tokenInput?.value).toBe('tok_from_query_param');
  });
});
