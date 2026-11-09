import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { JobPreferencesResponse } from '../models/preferences.models';
import { PreferencesService, WORKSPACE_PREFS_STORAGE_KEY } from './preferences.service';

describe('PreferencesService', () => {
  let service: PreferencesService;
  let httpMock: HttpTestingController;

  const savedResponse: JobPreferencesResponse = {
    preferredJobTitles: ['Senior Java Engineer', 'Staff Backend Engineer'],
    preferredCountries: ['Germany', 'Netherlands'],
    preferredCities: ['Berlin', 'Amsterdam'],
    minSalary: 90000,
    salaryCurrency: 'EUR',
    requiresSponsorship: true,
    requiresRelocation: true,
    workArrangement: 'HYBRID',
    englishSpeakingOnly: true,
    minMatchScore: 75,
    autopilotEnabled: true,
    updatedAt: '2026-11-08T12:00:00Z'
  };

  beforeEach(() => {
    localStorage.removeItem(WORKSPACE_PREFS_STORAGE_KEY);
    document.documentElement.removeAttribute('data-density');

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(PreferencesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(WORKSPACE_PREFS_STORAGE_KEY);
    document.documentElement.removeAttribute('data-density');
  });

  it('should load job preferences via GET /api/v1/preferences and track hasSavedPreferences', () => {
    let loaded: JobPreferencesResponse | undefined;
    service.loadPreferences().subscribe(res => (loaded = res));

    expect(service.isLoading()).toBeTrue();
    const req = httpMock.expectOne('/api/v1/preferences');
    expect(req.request.method).toBe('GET');

    req.flush({
      ...savedResponse,
      updatedAt: null
    });

    expect(service.isLoading()).toBeFalse();
    expect(loaded?.salaryCurrency).toBe('EUR');
    expect(service.hasSavedPreferences()).toBeFalse();
  });

  it('should save preferences via PUT /api/v1/preferences and update reactive signals', () => {
    service
      .savePreferences({
        preferredJobTitles: ['Senior Java Engineer'],
        preferredCountries: ['Germany'],
        minSalary: 90000,
        salaryCurrency: 'EUR',
        workArrangement: 'HYBRID',
        minMatchScore: 75,
        autopilotEnabled: true
      })
      .subscribe();

    const req = httpMock.expectOne('/api/v1/preferences');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.minMatchScore).toBe(75);
    expect(req.request.body.autopilotEnabled).toBeTrue();

    req.flush(savedResponse);

    expect(service.preferences()).toEqual(savedResponse);
    expect(service.hasSavedPreferences()).toBeTrue();
  });

  it('should persist alert cadence and workspace density in localStorage and toggle data-density attribute', () => {
    expect(service.workspacePrefs()).toEqual({
      alertCadence: 'DAILY',
      density: 'COMFORTABLE'
    });
    expect(document.documentElement.getAttribute('data-density')).toBeNull();

    service.setAlertCadence('INSTANT');
    service.setDensity('COMPACT');
    TestBed.flushEffects();

    expect(service.workspacePrefs()).toEqual({
      alertCadence: 'INSTANT',
      density: 'COMPACT'
    });
    expect(document.documentElement.getAttribute('data-density')).toBe('compact');
    expect(JSON.parse(localStorage.getItem(WORKSPACE_PREFS_STORAGE_KEY) ?? '{}')).toEqual({
      alertCadence: 'INSTANT',
      density: 'COMPACT'
    });

    // Recover gracefully when localStorage contains corrupt JSON
    TestBed.resetTestingModule();
    localStorage.setItem(WORKSPACE_PREFS_STORAGE_KEY, '{invalid-json');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    const freshService = TestBed.inject(PreferencesService);
    expect(freshService.workspacePrefs()).toEqual({
      alertCadence: 'DAILY',
      density: 'COMFORTABLE'
    });
  });
});

