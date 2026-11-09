import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { CountrySponsorshipProfile } from '../../core/models/sponsorship.models';
import { AuthService } from '../../core/services/auth.service';
import { SponsorshipComponent } from './sponsorship.component';

const MOCK_COUNTRIES: CountrySponsorshipProfile[] = [
  'Germany',
  'Netherlands',
  'Luxembourg',
  'Switzerland',
  'Ireland',
  'Austria',
  'Belgium',
  'Sweden',
  'Denmark',
  'Finland',
  'France',
  'Poland',
  'Portugal',
  'Spain'
].map((country, idx) => ({
  country,
  isoCode: ['DE', 'NL', 'LU', 'CH', 'IE', 'AT', 'BE', 'SE', 'DK', 'FI', 'FR', 'PL', 'PT', 'ES'][idx],
  officialRegisterAvailable: country === 'Netherlands',
  registrySource: country === 'Netherlands' ? 'NL_IND_PUBLIC_REGISTER' : `${country.toUpperCase()}_PORTAL`,
  registryName: `${country} Immigration Portal`,
  primaryAuthority: `${country} Immigration Authority`,
  officialSourceUrl: `https://official.example.eu/${country.toLowerCase()}`,
  publicRegisterUrl: `https://official.example.eu/${country.toLowerCase()}/register`,
  verificationMethod:
    country === 'Netherlands' ? 'OFFICIAL_PUBLIC_REGISTER_AND_VACANCY_CHECK' : 'STATUTORY_PATHWAY_AND_JOB_EVIDENCE',
  visaPathways: [country === 'Netherlands' ? 'Highly Skilled Migrant (Kennismigrant)' : 'EU Blue Card'],
  guidanceSummary: `Official sponsorship rules for ${country}.`,
  vacancyRuleWarning: 'Recognized sponsorship does not guarantee every vacancy offers sponsorship.',
  syncedEntriesCount: country === 'Netherlands' ? 2 : 0,
  lastSyncedAt: country === 'Netherlands' ? '2026-11-09T09:00:00Z' : null
}));

describe('SponsorshipComponent', () => {
  let fixture: ComponentFixture<SponsorshipComponent>;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SponsorshipComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialLoads(): void {
    httpMock.expectOne('/api/v1/sponsorship/countries').flush(MOCK_COUNTRIES);
    httpMock
      .expectOne(req => req.url === '/api/v1/sponsorship/history')
      .flush({
        content: [
          {
            id: 1,
            companyId: 10,
            jobId: null,
            companyName: 'Adyen N.V.',
            normalizedCompanyName: 'adyen',
            country: 'Netherlands',
            countryIsoCode: 'NL',
            status: 'VERIFIED_SPONSOR',
            statusLabel: 'Verified sponsor',
            vacancyStatus: 'NOT_MENTIONED',
            vacancySponsorshipConfirmed: false,
            vacancySponsorshipAssumed: false,
            confidenceLevel: 'VERIFIED',
            registrySource: 'NL_IND_PUBLIC_REGISTER',
            registryIdentifier: 'KVK-34259528',
            officialRegisteredName: 'Adyen N.V.',
            evidenceSummary: 'Employer accredited; confirm role directly.',
            evidenceSignals: ['Matched NL IND Public Register'],
            sourceUrl: 'https://ind.nl/en',
            vacancyCaveat: 'Does not assume every vacancy offers sponsorship.',
            verifiedAt: '2026-11-09T09:30:00Z'
          }
        ],
        pageNumber: 0,
        pageSize: 10,
        totalElements: 1,
        totalPages: 1,
        isFirst: true,
        isLast: true,
        hasNext: false,
        hasPrevious: false
      });
    httpMock
      .expectOne(req => req.url === '/api/v1/sponsorship/registry/ind')
      .flush({
        content: [
          {
            id: 5,
            country: 'Netherlands',
            registrySource: 'NL_IND_PUBLIC_REGISTER',
            registryIdentifier: 'KVK-34259528',
            officialName: 'Adyen N.V.',
            normalizedName: 'adyen',
            status: 'RECOGNIZED_SPONSOR',
            sourceUrl: 'https://ind.nl/en',
            active: true,
            syncedAt: '2026-11-09T09:00:00Z'
          }
        ],
        lastSyncStatus: {
          registrySource: 'NL_IND_PUBLIC_REGISTER',
          country: 'Netherlands',
          status: 'SUCCESS',
          sourceUrl: 'https://ind.nl/en',
          activeEntriesCount: 1,
          entriesParsed: 1,
          entriesUpserted: 1,
          companiesLinked: 1,
          errorMessage: null,
          syncedAt: '2026-11-09T09:00:00Z'
        },
        pageNumber: 0,
        pageSize: 12,
        totalElements: 1,
        totalPages: 1,
        isFirst: true,
        isLast: true,
        hasNext: false,
        hasPrevious: false
      });
  }

  it('should load 14 countries, history, IND entries, verify employer, and display vacancy caveat', () => {
    fixture = TestBed.createComponent(SponsorshipComponent);
    fixture.detectChanges();
    flushInitialLoads();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-sponsorship-guardrail]')?.textContent).toContain(
      'never assumes every vacancy offers visa sponsorship'
    );
    expect(host.querySelector('[data-sponsorship-legend]')?.textContent).toContain('Verified sponsor');
    expect(host.querySelector('[data-sponsorship-legend]')?.textContent).toContain('Unavailable');
    expect(host.querySelector('[data-sponsorship-history-panel]')?.textContent).toContain('Adyen N.V.');

    // Verify company with negative vacancy snippet overriding to UNAVAILABLE
    fixture.componentInstance.onCompanyInput('Adyen N.V.');
    fixture.componentInstance.onCountrySelect('Netherlands');
    fixture.componentInstance.onPostingTextInput('No visa sponsorship for this role.');
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('[data-sponsorship-verify-btn]')?.click();

    const verifyReq = httpMock.expectOne(
      req =>
        req.url === '/api/v1/sponsorship/verify' &&
        req.params.get('companyName') === 'Adyen N.V.' &&
        req.params.get('country') === 'Netherlands'
    );
    verifyReq.flush({
      verificationId: 99,
      companyId: 10,
      jobId: null,
      companyName: 'Adyen N.V.',
      normalizedCompanyName: 'adyen',
      country: 'Netherlands',
      countryIsoCode: 'NL',
      status: 'UNAVAILABLE',
      statusLabel: 'Unavailable',
      vacancyStatus: 'UNAVAILABLE',
      vacancyStatusLabel: 'Unavailable',
      vacancySponsorshipConfirmed: false,
      vacancySponsorshipAssumed: false,
      companyRecognizedSponsor: true,
      confidenceLevel: 'LOW',
      registrySource: 'NL_IND_PUBLIC_REGISTER',
      registryIdentifier: 'KVK-34259528',
      officialName: 'Adyen N.V.',
      evidenceNotes: 'Vacancy states sponsorship is unavailable despite employer IND recognition.',
      evidenceSignals: ['Vacancy explicitly declines sponsorship'],
      sourceUrl: 'https://ind.nl/en',
      vacancyCaveat: 'Recognized sponsorship never guarantees sponsorship for every opening.',
      verifiedAt: '2026-11-09T10:00:00Z',
      countryProfile: MOCK_COUNTRIES[1],
      recentHistory: []
    });

    // Post-verify refreshes user verification history
    httpMock.expectOne(req => req.url === '/api/v1/sponsorship/history').flush({
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
    fixture.detectChanges();

    expect(host.querySelector('[data-sponsorship-result]')?.textContent).toContain('Unavailable');
    expect(host.querySelector('[data-sponsorship-result-caveat]')?.textContent).toContain(
      'never guarantees sponsorship for every opening'
    );
    expect(host.querySelector('[data-sponsorship-company-link]')?.getAttribute('href')).toBe(
      '/companies?companyId=10'
    );

    // Switch to Country Rules tab (all 14 country cards)
    host.querySelector<HTMLButtonElement>('[data-sponsorship-tab="countries"]')?.click();
    fixture.detectChanges();
    expect(host.querySelectorAll('[data-country-card]').length).toBe(14);
    expect(host.querySelector('[data-country-card="LU"]')?.textContent).toContain('Luxembourg');

    // Switch to IND Registry tab
    host.querySelector<HTMLButtonElement>('[data-sponsorship-tab="ind-registry"]')?.click();
    fixture.detectChanges();
    expect(host.querySelector('[data-sponsorship-ind-panel]')?.textContent).toContain('KVK-34259528');
  });

  it('should expose admin IND sync action only for ROLE_ADMIN users', () => {
    fixture = TestBed.createComponent(SponsorshipComponent);
    fixture.detectChanges();
    flushInitialLoads();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-sponsorship-admin-sync]')).toBeNull();

    // Sign in as admin via login response
    authService.login({ email: 'admin@eurojob.eu', password: 'Password1!' }).subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush({
      accessToken: 'jwt-admin',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: {
        uuid: 'admin-1',
        email: 'admin@eurojob.eu',
        firstName: 'System',
        lastName: 'Admin',
        role: 'ROLE_ADMIN',
        accountStatus: 'ACTIVE',
        emailVerified: true
      }
    });
    fixture.detectChanges();

    const syncBtn = host.querySelector<HTMLButtonElement>('[data-sponsorship-admin-sync]');
    expect(syncBtn).toBeTruthy();
    syncBtn?.click();

    const syncReq = httpMock.expectOne('/api/v1/admin/sponsorship/ind/sync');
    expect(syncReq.request.method).toBe('POST');
    syncReq.flush({
      registrySource: 'NL_IND_PUBLIC_REGISTER',
      country: 'Netherlands',
      status: 'SUCCESS',
      sourceUrl: 'https://ind.nl/en',
      entriesParsed: 2,
      entriesUpserted: 2,
      entriesDeactivated: 0,
      companiesLinked: 1,
      executionDurationMs: 90,
      message: 'Parsed 2 IND recognized sponsor entries.',
      syncedAt: '2026-11-09T10:15:00Z'
    });

    httpMock.expectOne('/api/v1/sponsorship/countries').flush(MOCK_COUNTRIES);
    httpMock.expectOne(req => req.url === '/api/v1/sponsorship/registry/ind').flush({
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
    fixture.detectChanges();

    expect(host.querySelector('[data-sponsorship-sync-notice]')?.textContent).toContain(
      'Parsed 2 IND recognized sponsor entries'
    );
  });

  it('should prefill and auto-verify when navigated with companyName, country, and jobId query params', async () => {
    await TestBed.resetTestingModule()
      .configureTestingModule({
        imports: [SponsorshipComponent],
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          {
            provide: ActivatedRoute,
            useValue: {
              snapshot: {
                queryParamMap: convertToParamMap({
                  companyName: 'Booking.com',
                  country: 'Netherlands',
                  jobId: '777'
                })
              }
            }
          }
        ]
      })
      .compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SponsorshipComponent);
    fixture.detectChanges();

    flushInitialLoads();
    const verifyReq = httpMock.expectOne(
      req =>
        req.url === '/api/v1/sponsorship/verify' &&
        req.params.get('companyName') === 'Booking.com' &&
        req.params.get('country') === 'Netherlands' &&
        req.params.get('jobId') === '777'
    );
    verifyReq.flush({
      verificationId: 101,
      companyId: 20,
      jobId: 777,
      companyName: 'Booking.com',
      normalizedCompanyName: 'booking com',
      country: 'Netherlands',
      countryIsoCode: 'NL',
      status: 'VERIFIED_SPONSOR',
      statusLabel: 'Verified sponsor',
      vacancyStatus: 'NOT_MENTIONED',
      vacancyStatusLabel: 'Not mentioned',
      vacancySponsorshipConfirmed: false,
      vacancySponsorshipAssumed: false,
      companyRecognizedSponsor: true,
      confidenceLevel: 'VERIFIED',
      registrySource: 'NL_IND_PUBLIC_REGISTER',
      registryIdentifier: 'KVK-60754980',
      officialName: 'Booking.com B.V.',
      evidenceNotes: 'Employer is verified sponsor; vacancy confirmation required.',
      evidenceSignals: ['IND register match'],
      sourceUrl: 'https://ind.nl/en',
      vacancyCaveat: 'Does not guarantee every vacancy.',
      verifiedAt: '2026-11-09T10:20:00Z',
      countryProfile: MOCK_COUNTRIES[1],
      recentHistory: []
    });
    httpMock.expectOne(req => req.url === '/api/v1/sponsorship/history').flush({
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
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-sponsorship-linked-job]')?.textContent).toContain('#777');
    expect(host.querySelector('[data-sponsorship-result]')?.textContent).toContain('Booking.com B.V.');
  });
});

