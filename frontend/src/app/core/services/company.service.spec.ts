import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CompanyDetail, PagedCompaniesResponse } from '../models/company.models';
import { CompanyService } from './company.service';

describe('CompanyService', () => {
  let service: CompanyService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CompanyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should search companies with multi-facet params and update search signals', () => {
    const mockPage: PagedCompaniesResponse = {
      content: [
        {
          id: 10,
          name: 'Adyen',
          headquartersCountry: 'Netherlands',
          headquartersCity: 'Amsterdam',
          industry: 'FinTech',
          companySize: '1000-5000',
          websiteUrl: 'https://www.adyen.com',
          careersUrl: 'https://careers.adyen.com',
          logoUrl: null,
          isVerifiedSponsor: true,
          sponsorConfidenceLevel: 'VERIFIED',
          activeJobsCount: 4,
          sponsorshipJobsCount: 3,
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

    service
      .searchCompanies(
        {
          query: 'Adyen',
          country: 'Netherlands',
          industry: 'FinTech',
          tech: 'Java',
          verifiedSponsorOnly: true,
          savedOnly: false,
          withOpenJobsOnly: true,
          sort: 'verified,desc'
        },
        1,
        15
      )
      .subscribe(res => expect(res.totalElements).toBe(1));

    const req = httpMock.expectOne(
      r =>
        r.url === '/api/v1/companies' &&
        r.params.get('query') === 'Adyen' &&
        r.params.get('country') === 'Netherlands' &&
        r.params.get('industry') === 'FinTech' &&
        r.params.get('tech') === 'Java' &&
        r.params.get('verifiedSponsorOnly') === 'true' &&
        r.params.get('withOpenJobsOnly') === 'true' &&
        r.params.get('sort') === 'verified,desc' &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '15'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);

    expect(service.companies().length).toBe(1);
    expect(service.searchResult()?.verifiedSponsorsTotal).toBe(1);
  });

  it('should load detail, paginated company jobs, and synchronize save/unsave state across signals', () => {
    const initialPage: PagedCompaniesResponse = {
      content: [
        {
          id: 10,
          name: 'Adyen',
          headquartersCountry: 'Netherlands',
          headquartersCity: 'Amsterdam',
          industry: 'FinTech',
          companySize: '1000-5000',
          websiteUrl: 'https://www.adyen.com',
          careersUrl: null,
          logoUrl: null,
          isVerifiedSponsor: true,
          sponsorConfidenceLevel: 'VERIFIED',
          activeJobsCount: 2,
          sponsorshipJobsCount: 2,
          remoteJobsCount: 0,
          techStack: ['Java'],
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

    service.searchCompanies().subscribe();
    httpMock.expectOne(r => r.url === '/api/v1/companies').flush(initialPage);

    const mockDetail: CompanyDetail = {
      id: 10,
      name: 'Adyen',
      normalizedName: 'adyen',
      description: 'Unified payments engineering platform.',
      websiteUrl: 'https://www.adyen.com',
      careersUrl: 'https://careers.adyen.com',
      logoUrl: null,
      headquartersCountry: 'Netherlands',
      headquartersCity: 'Amsterdam',
      industry: 'FinTech',
      companySize: '1000-5000',
      isVerifiedSponsor: true,
      sponsorConfidenceLevel: 'VERIFIED',
      activeJobsCount: 2,
      sponsorshipJobsCount: 2,
      remoteJobsCount: 0,
      techStack: ['Java', 'PostgreSQL'],
      activeCountries: ['Netherlands'],
      activeCities: ['Amsterdam'],
      vacancyCaveat: 'Recognized sponsor status does not guarantee every vacancy offers sponsorship.',
      countryGuideSummary: 'NL Highly Skilled Migrant rules apply.',
      officialRegisterUrl: 'https://ind.nl/en/public-register-recognised-sponsors',
      registryRecords: [],
      isSaved: false,
      savedAt: null,
      priorityTier: null,
      userNotes: null,
      createdAt: null,
      updatedAt: null
    };

    service.getCompanyDetail(10).subscribe();
    httpMock.expectOne('/api/v1/companies/10').flush(mockDetail);
    expect(service.selectedCompany()?.name).toBe('Adyen');

    service.getCompanyJobs(10, { page: 0, size: 5, sort: 'postedAt,desc' }).subscribe();
    const jobsReq = httpMock.expectOne(
      r =>
        r.url === '/api/v1/companies/10/jobs' &&
        r.params.get('size') === '5' &&
        r.params.get('sort') === 'postedAt,desc'
    );
    jobsReq.flush({
      content: [],
      pageNumber: 0,
      pageSize: 5,
      totalElements: 0,
      totalPages: 0,
      isFirst: true,
      isLast: true,
      hasNext: false,
      hasPrevious: false
    });
    expect(service.companyJobsPage()?.pageSize).toBe(5);

    service
      .saveCompany(10, { priorityTier: 'TOP_PRIORITY', notes: 'Great Java stack' })
      .subscribe();
    const saveReq = httpMock.expectOne('/api/v1/companies/10/save');
    expect(saveReq.request.method).toBe('POST');
    saveReq.flush({
      companyId: 10,
      companyName: 'Adyen',
      isSaved: true,
      priorityTier: 'TOP_PRIORITY',
      notes: 'Great Java stack',
      savedAt: '2026-11-09T10:00:00Z'
    });

    expect(service.selectedCompany()?.isSaved).toBeTrue();
    expect(service.selectedCompany()?.userNotes).toBe('Great Java stack');
    expect(service.companies()[0].isSaved).toBeTrue();
    expect(service.searchResult()?.savedCompaniesTotal).toBe(1);

    service.unsaveCompany(10).subscribe();
    const unsaveReq = httpMock.expectOne('/api/v1/companies/10/save');
    expect(unsaveReq.request.method).toBe('DELETE');
    unsaveReq.flush({
      companyId: 10,
      companyName: 'Adyen',
      isSaved: false,
      priorityTier: null,
      notes: null,
      savedAt: null
    });

    expect(service.selectedCompany()?.isSaved).toBeFalse();
    expect(service.companies()[0].isSaved).toBeFalse();
    expect(service.searchResult()?.savedCompaniesTotal).toBe(0);
  });
});

