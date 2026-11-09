import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { CompanyDetail, PagedCompaniesResponse } from '../../core/models/company.models';
import { CompaniesComponent } from './companies.component';

describe('CompaniesComponent', () => {
  let httpMock: HttpTestingController;

  const mockSearchPage: PagedCompaniesResponse = {
    content: [
      {
        id: 101,
        name: 'Adyen N.V.',
        headquartersCountry: 'Netherlands',
        headquartersCity: 'Amsterdam',
        industry: 'FinTech',
        companySize: '1000-5000',
        websiteUrl: 'https://www.adyen.com',
        careersUrl: 'https://careers.adyen.com',
        logoUrl: null,
        isVerifiedSponsor: true,
        sponsorConfidenceLevel: 'VERIFIED',
        activeJobsCount: 3,
        sponsorshipJobsCount: 2,
        remoteJobsCount: 1,
        techStack: ['Java', 'PostgreSQL', 'Angular'],
        activeCountries: ['Netherlands'],
        registryCountries: ['Netherlands'],
        isSaved: false,
        savedAt: null,
        priorityTier: null
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
    verifiedSponsorsTotal: 1,
    savedCompaniesTotal: 0
  };

  const mockDetail: CompanyDetail = {
    id: 101,
    name: 'Adyen N.V.',
    normalizedName: 'adyen',
    description: 'Global unified commerce and payments platform.',
    websiteUrl: 'https://www.adyen.com',
    careersUrl: 'javascript:alert(1)',
    logoUrl: null,
    headquartersCountry: 'Netherlands',
    headquartersCity: 'Amsterdam',
    industry: 'FinTech',
    companySize: '1000-5000',
    isVerifiedSponsor: true,
    sponsorConfidenceLevel: 'VERIFIED',
    activeJobsCount: 3,
    sponsorshipJobsCount: 2,
    remoteJobsCount: 1,
    techStack: ['Java', 'PostgreSQL', 'Angular'],
    activeCountries: ['Netherlands'],
    activeCities: ['Amsterdam'],
    vacancyCaveat:
      'Recognized sponsor status applies to the employer organization and does NOT guarantee every vacancy offers visa sponsorship.',
    countryGuideSummary: 'Employers must hold IND recognised sponsor status for Kennismigrant visas.',
    officialRegisterUrl: 'https://ind.nl/en/public-register-recognised-sponsors',
    registryRecords: [
      {
        id: 7,
        country: 'Netherlands',
        registrySource: 'NL_IND_PUBLIC_REGISTER',
        registryIdentifier: 'KVK-34259528',
        officialRegisteredName: 'Adyen N.V.',
        status: 'RECOGNIZED_SPONSOR',
        evidenceNotes: 'Active IND public register entry',
        sourceUrl: 'https://ind.nl/en/public-register-recognised-sponsors',
        verifiedAt: '2026-11-08T10:00:00Z'
      }
    ],
    isSaved: false,
    savedAt: null,
    priorityTier: null,
    userNotes: null,
    createdAt: null,
    updatedAt: null
  };

  afterEach(() => {
    httpMock.verify();
  });

  function setupTest(queryParams: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [CompaniesComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(queryParams)
            }
          }
        }
      ]
    });
    httpMock = TestBed.inject(HttpTestingController);
    return TestBed.createComponent(CompaniesComponent);
  }

  it('should load directory, auto-select first employer, render dossier caveat + safe links + open jobs, and save target notes', () => {
    const fixture = setupTest();
    fixture.detectChanges();

    const searchReq = httpMock.expectOne(r => r.url === '/api/v1/companies');
    searchReq.flush(mockSearchPage);

    httpMock.expectOne('/api/v1/companies/101').flush(mockDetail);
    httpMock.expectOne(r => r.url === '/api/v1/companies/101/jobs').flush({
      content: [
        {
          id: 501,
          title: 'Senior Java Engineer',
          companyName: 'Adyen N.V.',
          location: 'Amsterdam, Netherlands',
          city: 'Amsterdam',
          country: 'Netherlands',
          workArrangement: 'HYBRID',
          salaryMin: 90000,
          salaryMax: 115000,
          salaryCurrency: 'EUR',
          salaryPeriod: 'YEARLY',
          sponsorStatus: 'VERIFIED_SPONSOR',
          matchPercentage: 91,
          sourceProvider: 'ARBEITNOW',
          postedAt: '2026-11-08T09:00:00Z',
          isSaved: false,
          isInterested: false
        }
      ],
      pageNumber: 0,
      pageSize: 8,
      totalElements: 1,
      totalPages: 1,
      isFirst: true,
      isLast: true,
      hasNext: false,
      hasPrevious: false
    });
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-companies-kpis]')?.textContent).toContain('Verified Visa Sponsors');
    expect(host.querySelector('[data-company-dossier]')?.textContent).toContain('Adyen N.V.');
    expect(host.querySelector('[data-company-vacancy-caveat]')?.textContent).toContain('does NOT guarantee');
    expect(host.querySelector('[data-company-website-link]')).toBeTruthy();
    // Unsafe javascript: careers link must NOT render
    expect(host.querySelector('[data-company-careers-link]')).toBeNull();
    expect(host.querySelector('[data-company-job="501"]')?.textContent).toContain('Senior Java Engineer');

    // Save bookmark with TOP_PRIORITY tier and notes
    const tierSelect = host.querySelector('[data-company-tier-select]') as HTMLSelectElement;
    tierSelect.value = 'TOP_PRIORITY';
    tierSelect.dispatchEvent(new Event('change'));

    const notesInput = host.querySelector('[data-company-notes-input]') as HTMLTextAreaElement;
    notesInput.value = 'Reach out to Amsterdam platform lead';
    notesInput.dispatchEvent(new Event('input'));

    (host.querySelector('[data-company-save-btn]') as HTMLButtonElement).click();

    const saveReq = httpMock.expectOne('/api/v1/companies/101/save');
    expect(saveReq.request.method).toBe('POST');
    expect(saveReq.request.body).toEqual({
      priorityTier: 'TOP_PRIORITY',
      notes: 'Reach out to Amsterdam platform lead'
    });
    saveReq.flush({
      companyId: 101,
      companyName: 'Adyen N.V.',
      isSaved: true,
      priorityTier: 'TOP_PRIORITY',
      notes: 'Reach out to Amsterdam platform lead',
      savedAt: '2026-11-09T11:00:00Z'
    });
    fixture.detectChanges();

    expect(host.querySelector('[data-companies-action-notice]')?.textContent).toContain('Saved target profile');
    expect(host.querySelector('[data-company-unsave-btn]')).toBeTruthy();

    // Remove bookmark
    (host.querySelector('[data-company-unsave-btn]') as HTMLButtonElement).click();
    const deleteReq = httpMock.expectOne('/api/v1/companies/101/save');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush({
      companyId: 101,
      companyName: 'Adyen N.V.',
      isSaved: false,
      priorityTier: null,
      notes: null,
      savedAt: null
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedCompany()?.isSaved).toBeFalse();
  });

  it('should honor initial query params, filter toggles, and show clean empty state when no companies exist', () => {
    const fixture = setupTest({
      query: 'SAP',
      country: 'Germany',
      verifiedSponsorOnly: 'true'
    });
    fixture.detectChanges();

    const initialReq = httpMock.expectOne(
      r =>
        r.url === '/api/v1/companies' &&
        r.params.get('query') === 'SAP' &&
        r.params.get('country') === 'Germany' &&
        r.params.get('verifiedSponsorOnly') === 'true'
    );
    initialReq.flush({
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
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-companies-empty]')?.textContent).toContain('No employers match these filters');

    (host.querySelector('[data-companies-reset-btn]') as HTMLButtonElement).click();
    const resetReq = httpMock.expectOne(
      r => r.url === '/api/v1/companies' && !r.params.has('query') && !r.params.has('country')
    );
    resetReq.flush({
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
    fixture.detectChanges();

    expect(host.querySelector('[data-companies-empty]')?.textContent).toContain('No employers indexed yet');
  });
});

