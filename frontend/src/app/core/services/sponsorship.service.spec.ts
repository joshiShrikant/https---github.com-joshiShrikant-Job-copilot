import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SponsorshipService } from './sponsorship.service';

describe('SponsorshipService', () => {
  let service: SponsorshipService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(SponsorshipService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load country sponsorship profiles and store them in reactive signals', () => {
    service.loadCountries().subscribe(countries => {
      expect(countries.length).toBe(2);
    });

    const req = httpMock.expectOne('/api/v1/sponsorship/countries');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        country: 'Netherlands',
        isoCode: 'NL',
        officialRegisterAvailable: true,
        registrySource: 'NL_IND_PUBLIC_REGISTER',
        registryName: 'IND Public Register',
        primaryAuthority: 'IND',
        officialSourceUrl: 'https://ind.nl',
        publicRegisterUrl: 'https://ind.nl/en',
        verificationMethod: 'OFFICIAL_PUBLIC_REGISTER_AND_VACANCY_CHECK',
        visaPathways: ['Highly Skilled Migrant'],
        guidanceSummary: 'Official NL IND framework.',
        vacancyRuleWarning: 'Does not guarantee every vacancy.',
        syncedEntriesCount: 25,
        lastSyncedAt: '2026-11-09T10:00:00Z'
      },
      {
        country: 'Luxembourg',
        isoCode: 'LU',
        officialRegisterAvailable: false,
        registrySource: 'LU_GUICHET_THIRD_COUNTRY',
        registryName: 'Guichet.lu',
        primaryAuthority: 'ADEM',
        officialSourceUrl: 'https://guichet.public.lu',
        publicRegisterUrl: 'https://guichet.public.lu',
        verificationMethod: 'STATUTORY_PATHWAY_AND_JOB_EVIDENCE',
        visaPathways: ['EU Blue Card'],
        guidanceSummary: 'Luxembourg per-offer authorization.',
        vacancyRuleWarning: 'Confirm salary thresholds.',
        syncedEntriesCount: 0,
        lastSyncedAt: null
      }
    ]);

    expect(service.countries().length).toBe(2);
    expect(service.isLoadingCountries()).toBeFalse();
  });

  it('should verify company sponsorship with optional posting text and query params', () => {
    service
      .verifyCompany({
        companyName: 'Adyen N.V.',
        country: 'Netherlands',
        postingText: 'Unable to sponsor visas for this local support role.'
      })
      .subscribe(res => {
        expect(res.status).toBe('UNAVAILABLE');
        expect(res.vacancySponsorshipAssumed).toBeFalse();
      });

    const req = httpMock.expectOne(
      r =>
        r.url === '/api/v1/sponsorship/verify' &&
        r.params.get('companyName') === 'Adyen N.V.' &&
        r.params.get('country') === 'Netherlands' &&
        r.params.get('postingText') === 'Unable to sponsor visas for this local support role.'
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      verificationId: 10,
      companyId: 42,
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
      evidenceNotes: 'Vacancy explicitly declines visa sponsorship.',
      evidenceSignals: ['Vacancy explicitly declines sponsorship'],
      sourceUrl: 'https://ind.nl/en',
      vacancyCaveat: 'Recognized sponsorship never guarantees every opening offers sponsorship.',
      verifiedAt: '2026-11-09T10:05:00Z',
      countryProfile: null,
      recentHistory: []
    });

    expect(service.lastVerification()?.status).toBe('UNAVAILABLE');
    expect(service.isVerifying()).toBeFalse();
  });

  it('should load paginated history, IND registry search, and admin IND sync endpoints', () => {
    service.loadHistory({ companyName: 'adyen', country: 'Netherlands', page: 0, size: 10 }).subscribe();
    const histReq = httpMock.expectOne(
      r =>
        r.url === '/api/v1/sponsorship/history' &&
        r.params.get('companyName') === 'adyen' &&
        r.params.get('country') === 'Netherlands'
    );
    histReq.flush({
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

    service.searchIndRegistry({ query: 'ASML', page: 0, size: 15 }).subscribe();
    const indReq = httpMock.expectOne(
      r => r.url === '/api/v1/sponsorship/registry/ind' && r.params.get('query') === 'ASML'
    );
    indReq.flush({
      content: [],
      lastSyncStatus: null,
      pageNumber: 0,
      pageSize: 15,
      totalElements: 0,
      totalPages: 0,
      isFirst: true,
      isLast: true,
      hasNext: false,
      hasPrevious: false
    });

    service.triggerAdminIndSync().subscribe(sync => {
      expect(sync.status).toBe('SUCCESS');
    });
    const syncReq = httpMock.expectOne('/api/v1/admin/sponsorship/ind/sync');
    expect(syncReq.request.method).toBe('POST');
    syncReq.flush({
      registrySource: 'NL_IND_PUBLIC_REGISTER',
      country: 'Netherlands',
      status: 'SUCCESS',
      sourceUrl: 'https://ind.nl/en',
      entriesParsed: 20,
      entriesUpserted: 20,
      entriesDeactivated: 0,
      companiesLinked: 2,
      executionDurationMs: 120,
      message: 'Synced',
      syncedAt: '2026-11-09T10:10:00Z'
    });
  });
});

