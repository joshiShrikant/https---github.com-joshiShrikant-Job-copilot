import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { WORKSPACE_ROUTES } from '../../app.routes';
import { SESSION_HINT_STORAGE_KEY, AuthService } from '../services/auth.service';
import { SIDEBAR_COLLAPSE_STORAGE_KEY } from '../services/layout-state.service';
import { THEME_STORAGE_KEY } from '../services/theme.service';
import { ALL_NAV_ITEMS } from './navigation.config';
import { ShellComponent } from './shell.component';

describe('ShellComponent & Layout Navigation Integration', () => {
  let breakpointSubject: BehaviorSubject<BreakpointState>;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(async () => {
    localStorage.removeItem(SIDEBAR_COLLAPSE_STORAGE_KEY);
    localStorage.setItem(THEME_STORAGE_KEY, 'light');
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');

    breakpointSubject = new BehaviorSubject<BreakpointState>({
      matches: false,
      breakpoints: {}
    });

    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter(WORKSPACE_ROUTES, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        {
          provide: BreakpointObserver,
          useValue: {
            observe: () => breakpointSubject.asObservable()
          }
        }
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(SIDEBAR_COLLAPSE_STORAGE_KEY);
    localStorage.setItem(THEME_STORAGE_KEY, 'light');
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
  });

  it('should update breadcrumbs and render every route across all 14 navigation entries', async () => {
    const router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    for (const item of ALL_NAV_ITEMS) {
      const navigated = await router.navigateByUrl(item.route);
      expect(navigated).withContext(`Navigation failed for ${item.route}`).toBeTrue();
      fixture.detectChanges();

      httpMock
        .match(
          req =>
            req.method === 'GET' &&
            (req.url === '/api/v1/profile' ||
              req.url === '/api/v1/resumes' ||
              req.url === '/api/v1/tailored-documents/resumes' ||
              req.url === '/api/v1/tailored-documents/cover-letters' ||
              req.url === '/api/v1/applications' ||
              req.url === '/api/v1/recruiters/reminders' ||
              req.url === '/api/v1/preferences' ||
              req.url === '/api/v1/dashboard/summary' ||
              req.url === '/api/v1/jobs' ||
              req.url === '/api/v1/companies' ||
              req.url === '/api/v1/sponsorship/countries' ||
              req.url === '/api/v1/sponsorship/history' ||
              req.url === '/api/v1/sponsorship/registry/ind' ||
              req.url === '/api/v1/analytics/market-insights' ||
              req.url === '/api/v1/autopilot/status' ||
              req.url === '/api/v1/autopilot/recommended-jobs' ||
              req.url === '/api/v1/autopilot/history' ||
              req.url === '/api/v1/notifications' ||
              req.url === '/api/v1/notifications/unread-count' ||
              req.url === '/api/v1/interview-prep/sessions' ||
              req.url === '/api/v1/interview-prep/history')
        )
        .forEach(testReq => {
          if (
            testReq.request.url === '/api/v1/resumes' ||
            testReq.request.url === '/api/v1/tailored-documents/resumes' ||
            testReq.request.url === '/api/v1/tailored-documents/cover-letters' ||
            testReq.request.url === '/api/v1/applications' ||
            testReq.request.url === '/api/v1/sponsorship/countries' ||
            testReq.request.url === '/api/v1/interview-prep/sessions'
          ) {
            testReq.flush([]);
          } else if (testReq.request.url === '/api/v1/notifications/unread-count') {
            testReq.flush({ unreadCount: 0 });
          } else if (testReq.request.url === '/api/v1/analytics/market-insights') {
            testReq.flush({
              generatedAt: '2026-11-09T09:00:00Z',
              lastDataRefreshAt: null,
              dataCoverageDisclaimer: 'Discovered jobs inside workspace.',
              trendWindowDays: 30,
              availableCountries: [],
              selectedCountries: [],
              totals: {
                totalActiveJobs: 0,
                angularJobs: 0,
                frontendJobs: 0,
                javaJobs: 0,
                fullStackJobs: 0,
                sponsorshipOpportunities: 0,
                verifiedSponsorJobs: 0,
                englishConfirmedJobs: 0,
                localLanguageRequiredJobs: 0,
                languageUnspecifiedJobs: 0,
                remoteJobs: 0,
                hybridJobs: 0,
                onsiteJobs: 0
              },
              countryComparisons: [],
              discoveryTrends: [],
              sourceCoverage: []
            });
          } else if (testReq.request.url === '/api/v1/autopilot/status') {
            testReq.flush({
              autopilotEnabled: false,
              autopilotPaused: false,
              emailNotificationsEnabled: false,
              serverEmailRelayEnabled: false,
              lastAutopilotRunAt: null,
              minMatchScore: 60,
              preferredJobTitles: [],
              preferredCountries: [],
              preferredCities: [],
              recommendedJobsCount: 0,
              unreadNotificationsCount: 0,
              latestRun: null
            });
          } else if (testReq.request.url === '/api/v1/autopilot/recommended-jobs') {
            testReq.flush({
              content: [],
              pageNumber: 0,
              pageSize: 20,
              totalElements: 0,
              totalPages: 0,
              isFirst: true,
              isLast: true,
              hasNext: false,
              hasPrevious: false,
              minMatchScoreUsed: 60,
              highMatchCount: 0,
              verifiedSponsorCount: 0,
              autopilotPaused: false
            });
          } else if (testReq.request.url === '/api/v1/notifications') {
            testReq.flush({
              content: [],
              pageNumber: 0,
              pageSize: 20,
              totalElements: 0,
              totalPages: 0,
              isFirst: true,
              isLast: true,
              hasNext: false,
              hasPrevious: false,
              unreadCount: 0
            });
          } else if (testReq.request.url === '/api/v1/interview-prep/history') {
            testReq.flush({
              totalSessions: 0,
              totalQuestions: 0,
              totalAnsweredQuestions: 0,
              averageScore: null,
              categoryStats: [],
              difficultyStats: [],
              recentScoreTrend: [],
              weakestCategories: [],
              improvementRecommendations: [],
              sessions: []
            });
          } else if (testReq.request.url === '/api/v1/recruiters/reminders') {
            testReq.flush({
              overdueCount: 0,
              dueTodayCount: 0,
              upcomingCount: 0,
              totalPendingCount: 0,
              items: []
            });
          } else if (testReq.request.url === '/api/v1/companies') {
            testReq.flush({
              content: [],
              pageNumber: 0,
              pageSize: 20,
              totalElements: 0,
              totalPages: 0,
              isFirst: true,
              isLast: true,
              hasNext: false,
              hasPrevious: false,
              verifiedSponsorsTotal: 0,
              savedCompaniesTotal: 0
            });
          } else if (
            testReq.request.url === '/api/v1/jobs' ||
            testReq.request.url === '/api/v1/sponsorship/history' ||
            testReq.request.url === '/api/v1/autopilot/history'
          ) {
            testReq.flush({
              content: [],
              pageNumber: 0,
              pageSize: 20,
              totalElements: 0,
              totalPages: 0,
              isFirst: true,
              isLast: true,
              hasNext: false,
              hasPrevious: false
            });
          } else if (testReq.request.url === '/api/v1/sponsorship/registry/ind') {
            testReq.flush({
              content: [],
              lastSyncStatus: null,
              pageNumber: 0,
              pageSize: 12,
              totalElements: 0,
              totalPages: 0,
              isFirst: true,
              isLast: true,
              hasNext: false,
              hasPrevious: false
            });
          } else if (testReq.request.url === '/api/v1/dashboard/summary') {
            testReq.flush({
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
          } else if (testReq.request.url === '/api/v1/preferences') {
            testReq.flush({
              preferredJobTitles: [],
              preferredCountries: [],
              preferredCities: [],
              minSalary: null,
              salaryCurrency: 'EUR',
              requiresSponsorship: true,
              requiresRelocation: true,
              workArrangement: 'ANY',
              englishSpeakingOnly: true,
              minMatchScore: 60,
              autopilotEnabled: false,
              updatedAt: null
            });
          } else {
            testReq.flush({ skills: [], educations: [], experiences: [] });
          }
        });

      const host = fixture.nativeElement as HTMLElement;
      const currentCrumb = host.querySelector('.breadcrumb-current');
      expect(currentCrumb?.textContent?.trim()).toBe(item.label);
    }
  });

  it('should persist desktop sidebar collapse state and show expand button in header when collapsed', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance.layout.isSidebarCollapsed()).toBeFalse();

    const collapseBtn = host.querySelector<HTMLButtonElement>('app-sidebar .header-icon-btn');
    collapseBtn?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.layout.isSidebarCollapsed()).toBeTrue();
    expect(localStorage.getItem(SIDEBAR_COLLAPSE_STORAGE_KEY)).toBe('true');
    expect(host.querySelector('app-header .desktop-expand-btn')).toBeTruthy();
  });

  it('should open mobile drawer on responsive viewport and close via Escape key or navigation', async () => {
    breakpointSubject.next({ matches: true, breakpoints: {} });
    const router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const mobileMenuBtn = host.querySelector<HTMLButtonElement>('app-header .mobile-menu-btn');
    expect(mobileMenuBtn).toBeTruthy();

    mobileMenuBtn?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.layout.isMobileDrawerOpen()).toBeTrue();
    expect(host.querySelector('.drawer-backdrop')).toBeTruthy();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(fixture.componentInstance.layout.isMobileDrawerOpen()).toBeFalse();
    expect(host.querySelector('.drawer-backdrop')).toBeNull();

    mobileMenuBtn?.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.layout.isMobileDrawerOpen()).toBeTrue();

    await router.navigateByUrl('/sponsorship');
    fixture.detectChanges();
    httpMock
      .match(
        req =>
          req.method === 'GET' &&
          (req.url === '/api/v1/sponsorship/countries' ||
            req.url === '/api/v1/sponsorship/history' ||
            req.url === '/api/v1/sponsorship/registry/ind')
      )
      .forEach(req => {
        if (req.request.url === '/api/v1/sponsorship/countries') {
          req.flush([]);
        } else if (req.request.url === '/api/v1/sponsorship/registry/ind') {
          req.flush({
            content: [],
            lastSyncStatus: null,
            pageNumber: 0,
            pageSize: 12,
            totalElements: 0,
            totalPages: 0,
            isFirst: true,
            isLast: true,
            hasNext: false,
            hasPrevious: false
          });
        } else {
          req.flush({
            content: [],
            pageNumber: 0,
            pageSize: 10,
            totalElements: 0,
            totalPages: 0,
            isFirst: true,
            isLast: true,
            hasNext: false,
            hasPrevious: false
          });
        }
      });
    expect(fixture.componentInstance.layout.isMobileDrawerOpen()).toBeFalse();
  });

  it('should open guest user menu popover and switch between dark, light, and system themes cleanly', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const userTrigger = host.querySelector<HTMLButtonElement>('app-user-menu .user-trigger');
    expect(userTrigger).toBeTruthy();
    expect(userTrigger?.getAttribute('aria-expanded')).toBe('false');

    userTrigger?.click();
    fixture.detectChanges();

    expect(userTrigger?.getAttribute('aria-expanded')).toBe('true');
    const dropdown = host.querySelector('.user-dropdown');
    expect(dropdown).toBeTruthy();
    expect(dropdown?.textContent).toContain('Workspace Guest');
    expect(dropdown?.querySelector('a[href="/auth/login"]')?.textContent).toContain('Sign in');
    expect(dropdown?.querySelector('a[href="/auth/register"]')?.textContent).toContain(
      'Create account'
    );

    const darkMenuPill = host.querySelector<HTMLButtonElement>(
      '.user-dropdown [data-theme-option="dark"]'
    );
    expect(darkMenuPill?.textContent).toContain('Dark');
    expect(
      host.querySelector('.user-dropdown [data-theme-option="system"]')?.textContent
    ).toContain('System');

    darkMenuPill?.click();
    fixture.detectChanges();
    TestBed.flushEffects();

    expect(fixture.componentInstance.theme.theme()).toBe('dark');
    expect(fixture.componentInstance.theme.isDarkMode()).toBeTrue();
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme-mode')).toBe('dark');
    expect(host.querySelector('.user-dropdown')).toBeNull();

    host.querySelector<HTMLButtonElement>('app-header [data-theme-option="system"]')?.click();
    fixture.detectChanges();
    TestBed.flushEffects();

    expect(fixture.componentInstance.theme.theme()).toBe('system');
    expect(document.documentElement.getAttribute('data-theme-mode')).toBe('system');
  });

  it('should show authenticated user identity and sign out cleanly via AuthService', () => {
    authService.login({ email: 'alex@eurojobcopilot.com', password: 'Password1!' }).subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush({
      accessToken: 'jwt-shell-1',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: {
        uuid: 'u-1',
        email: 'alex@eurojobcopilot.com',
        firstName: 'Alex',
        lastName: 'Mueller',
        role: 'ROLE_USER',
        accountStatus: 'ACTIVE',
        emailVerified: true
      }
    });

    const router = TestBed.inject(Router);
    const navSpy = spyOn(router, 'navigateByUrl').and.resolveTo(true);

    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    TestBed.flushEffects();

    const unreadReq = httpMock.expectOne('/api/v1/notifications/unread-count');
    expect(unreadReq.request.method).toBe('GET');
    unreadReq.flush({ unreadCount: 3 });
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-header-unread-badge]')?.textContent?.trim()).toBe('3');
    expect(host.querySelector('app-user-menu .avatar')?.textContent?.trim()).toBe('AM');
    expect(host.querySelector('app-user-menu .user-name')?.textContent?.trim()).toBe('Alex Mueller');

    host.querySelector<HTMLButtonElement>('app-user-menu .user-trigger')?.click();
    fixture.detectChanges();

    const dropdown = host.querySelector('.user-dropdown');
    expect(dropdown?.textContent).toContain('alex@eurojobcopilot.com');

    const signOutBtn = host.querySelector<HTMLButtonElement>('.danger-menu-item');
    expect(signOutBtn?.textContent).toContain('Sign out');
    signOutBtn?.click();

    const logoutReq = httpMock.expectOne('/api/v1/auth/logout');
    logoutReq.flush({ success: true, message: 'Logged out successfully' });
    fixture.detectChanges();
    TestBed.flushEffects();

    expect(authService.isAuthenticated()).toBeFalse();
    expect(host.querySelector('[data-header-unread-badge]')).toBeNull();
    expect(navSpy).toHaveBeenCalledWith('/auth/login');
  });
});
