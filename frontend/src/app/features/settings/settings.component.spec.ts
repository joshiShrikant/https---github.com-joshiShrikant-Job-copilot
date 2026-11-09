import { BreakpointObserver } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { JobPreferencesResponse } from '../../core/models/preferences.models';
import { AuthService, SESSION_HINT_STORAGE_KEY } from '../../core/services/auth.service';
import {
  LayoutStateService,
  SIDEBAR_COLLAPSE_STORAGE_KEY
} from '../../core/services/layout-state.service';
import { WORKSPACE_PREFS_STORAGE_KEY } from '../../core/services/preferences.service';
import { THEME_STORAGE_KEY, ThemeService } from '../../core/services/theme.service';
import { SettingsComponent } from './settings.component';

describe('SettingsComponent', () => {
  let fixture: ComponentFixture<SettingsComponent>;
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let themeService: ThemeService;
  let layoutService: LayoutStateService;
  let router: Router;

  const defaultPrefs: JobPreferencesResponse = {
    preferredJobTitles: ['Java Engineer'],
    preferredCountries: ['Germany'],
    preferredCities: ['Berlin'],
    minSalary: 75000,
    salaryCurrency: 'EUR',
    requiresSponsorship: true,
    requiresRelocation: true,
    workArrangement: 'HYBRID',
    englishSpeakingOnly: true,
    minMatchScore: 60,
    autopilotEnabled: false,
    autopilotPaused: false,
    emailNotificationsEnabled: false,
    lastAutopilotRunAt: null,
    updatedAt: null
  };

  beforeEach(async () => {
    localStorage.removeItem(WORKSPACE_PREFS_STORAGE_KEY);
    localStorage.removeItem(THEME_STORAGE_KEY);
    localStorage.removeItem(SIDEBAR_COLLAPSE_STORAGE_KEY);
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.removeAttribute('data-density');

    await TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: BreakpointObserver,
          useValue: {
            observe: () => of({ matches: false, breakpoints: {} })
          }
        }
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    themeService = TestBed.inject(ThemeService);
    layoutService = TestBed.inject(LayoutStateService);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(WORKSPACE_PREFS_STORAGE_KEY);
    localStorage.removeItem(THEME_STORAGE_KEY);
    localStorage.removeItem(SIDEBAR_COLLAPSE_STORAGE_KEY);
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.removeAttribute('data-density');
  });

  function createAndFlushInitialLoad(prefs: JobPreferencesResponse = defaultPrefs): void {
    fixture = TestBed.createComponent(SettingsComponent);
    fixture.detectChanges();

    const loadReq = httpMock.expectOne('/api/v1/preferences');
    expect(loadReq.request.method).toBe('GET');
    loadReq.flush(prefs);
    fixture.detectChanges();
  }

  it('should load initial preferences, show default banner when unsaved, and retry on error', () => {
    fixture = TestBed.createComponent(SettingsComponent);
    fixture.detectChanges();

    const failedReq = httpMock.expectOne('/api/v1/preferences');
    failedReq.flush(
      { message: 'Preferences service temporarily unavailable' },
      { status: 503, statusText: 'Service Unavailable' }
    );
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.status-banner.error')?.textContent).toContain(
      'Preferences service temporarily unavailable'
    );

    host.querySelector<HTMLButtonElement>('[data-action="retry-load"]')?.click();
    const retryReq = httpMock.expectOne('/api/v1/preferences');
    retryReq.flush(defaultPrefs);
    fixture.detectChanges();

    expect(host.querySelector('.status-banner.error')).toBeNull();
    expect(host.querySelector('.status-banner.info')?.textContent).toContain(
      'System defaults are preloaded'
    );
    expect(host.querySelector('.header-status-pill')?.textContent).toContain('Manual Discovery');
  });

  it('should manage target chips, quick EU country presets, and save search preferences via PUT', () => {
    createAndFlushInitialLoad();

    const host = fixture.nativeElement as HTMLElement;

    // Add a new preferred job title
    const titleInput = host.querySelector<HTMLInputElement>('#pref-titles-input');
    expect(titleInput).toBeTruthy();
    if (titleInput) {
      titleInput.value = 'Staff Backend Engineer';
      titleInput.dispatchEvent(new Event('input'));
      titleInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    }

    // Add Netherlands using the quick preset pill and enter Amsterdam as target city
    const nlPreset = Array.from(host.querySelectorAll<HTMLButtonElement>('.preset-pill')).find(
      btn => btn.textContent?.trim() === 'Netherlands'
    );
    nlPreset?.click();

    const cityInput = host.querySelector<HTMLInputElement>('#pref-cities-input');
    if (cityInput) {
      cityInput.value = 'Amsterdam';
      cityInput.dispatchEvent(new Event('input'));
      cityInput.dispatchEvent(new KeyboardEvent('keydown', { key: ',' }));
    }

    // Set match threshold and enable autopilot
    const scoreNumber = host.querySelector<HTMLInputElement>('#pref-match-score-number');
    if (scoreNumber) {
      scoreNumber.value = '80';
      scoreNumber.dispatchEvent(new Event('input'));
    }

    const autopilotCheckbox = host.querySelector<HTMLInputElement>('#pref-autopilot-enabled');
    autopilotCheckbox?.click();

    const emailCheckbox = host.querySelector<HTMLInputElement>('#pref-email-notifications');
    emailCheckbox?.click();

    const form = host.querySelector<HTMLFormElement>('form');
    form?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const saveReq = httpMock.expectOne('/api/v1/preferences');
    expect(saveReq.request.method).toBe('PUT');
    expect(saveReq.request.body).toEqual({
      preferredJobTitles: ['Java Engineer', 'Staff Backend Engineer'],
      preferredCountries: ['Germany', 'Netherlands'],
      preferredCities: ['Berlin', 'Amsterdam'],
      minSalary: 75000,
      salaryCurrency: 'EUR',
      requiresSponsorship: true,
      requiresRelocation: true,
      workArrangement: 'HYBRID',
      englishSpeakingOnly: true,
      minMatchScore: 80,
      autopilotEnabled: true,
      emailNotificationsEnabled: true
    });

    saveReq.flush({
      ...saveReq.request.body,
      updatedAt: '2026-11-09T09:00:00Z'
    });
    fixture.detectChanges();

    expect(host.querySelector('.status-banner.success')?.textContent).toContain(
      'job search and autopilot preferences have been saved'
    );
    expect(host.querySelector('.header-status-pill')?.textContent).toContain('Autopilot Active');
    expect(host.querySelector('.header-status-pill')?.textContent).toContain('80% match');
  });

  it('should switch to Workspace Appearance tab and update theme, density, sidebar collapse, and alert cadence', () => {
    createAndFlushInitialLoad();
    const host = fixture.nativeElement as HTMLElement;

    host.querySelector<HTMLButtonElement>('[data-tab="appearance"]')?.click();
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('[data-theme-choice="dark"]')?.click();
    host.querySelector<HTMLButtonElement>('[data-density-choice="COMPACT"]')?.click();
    host.querySelector<HTMLButtonElement>('[data-cadence-choice="INSTANT"]')?.click();

    const sidebarCheckbox = host.querySelector<HTMLInputElement>('#pref-sidebar-collapsed');
    sidebarCheckbox?.click();

    fixture.detectChanges();
    TestBed.flushEffects();

    expect(themeService.isDarkMode()).toBeTrue();
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.documentElement.getAttribute('data-density')).toBe('compact');
    expect(layoutService.isSidebarCollapsed()).toBeTrue();
    expect(JSON.parse(localStorage.getItem(WORKSPACE_PREFS_STORAGE_KEY) ?? '{}')).toEqual({
      alertCadence: 'INSTANT',
      density: 'COMPACT'
    });
  });

  it('should display account summary and handle two-step Sign Out All Devices via AuthService', () => {
    authService.login({ email: 'alex@eurojobcopilot.com', password: 'Password1!' }, true).subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush({
      accessToken: 'jwt-settings-1',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: {
        uuid: 'user-uuid-999',
        email: 'alex@eurojobcopilot.com',
        firstName: 'Alex',
        lastName: 'Mueller',
        role: 'ROLE_USER',
        accountStatus: 'ACTIVE',
        emailVerified: true
      }
    });

    const navSpy = spyOn(router, 'navigateByUrl').and.resolveTo(true);

    createAndFlushInitialLoad();
    const host = fixture.nativeElement as HTMLElement;

    host.querySelector<HTMLButtonElement>('[data-tab="security"]')?.click();
    fixture.detectChanges();

    expect(host.textContent).toContain('Alex Mueller');
    expect(host.textContent).toContain('alex@eurojobcopilot.com');
    expect(host.textContent).toContain('user-uuid-999');
    expect(host.textContent).toContain('Email Verified');

    // Step 1: open confirmation dialog without making HTTP call yet
    host.querySelector<HTMLButtonElement>('[data-action="request-sign-out-all"]')?.click();
    fixture.detectChanges();
    expect(host.querySelector('[role="alertdialog"]')).toBeTruthy();

    // Step 2: confirm global revocation
    host.querySelector<HTMLButtonElement>('[data-action="confirm-sign-out-all"]')?.click();
    fixture.detectChanges();

    const logoutReq = httpMock.expectOne('/api/v1/auth/logout');
    expect(logoutReq.request.method).toBe('POST');
    expect(logoutReq.request.body).toEqual({ revokeAllSessions: true });
    logoutReq.flush({ success: true, message: 'All sessions revoked' });

    expect(authService.isAuthenticated()).toBeFalse();
    expect(navSpy).toHaveBeenCalledWith('/auth/login');
  });
});

