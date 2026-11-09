import { DOCUMENT } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  ApplyRedirectResponse,
  BatchMatchRecalculationResponse,
  JobAiAnalysis,
  JobDetail,
  JobInteractionResponse,
  JobMatchBreakdown,
  JobSummary,
  JobSummaryPageResponse
} from '../../core/models/job.models';
import { JobsComponent } from './jobs.component';

describe('JobsComponent (Live API Discovery & Interactions)', () => {
  let fixture: ComponentFixture<JobsComponent>;
  let httpMock: HttpTestingController;
  let openSpy: jasmine.Spy;

  const jobAdyen: JobSummary = {
    id: 101,
    title: 'Senior Java & Spring Boot Backend Engineer',
    companyName: 'Adyen N.V.',
    location: 'Amsterdam, Netherlands',
    city: 'Amsterdam',
    country: 'Netherlands',
    workArrangement: 'HYBRID',
    employmentType: 'FULL_TIME',
    experienceLevel: 'SENIOR',
    salaryMin: 85000,
    salaryMax: 112000,
    salaryCurrency: 'EUR',
    salaryPeriod: 'YEARLY',
    sponsorStatus: 'VERIFIED_SPONSOR',
    matchPercentage: 90,
    sourceProvider: 'ARBEITNOW',
    postedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
    isSaved: false,
    isInterested: false,
    isHidden: false,
    skills: ['Java 21', 'Spring Boot', 'PostgreSQL', 'Kafka'],
    descriptionSnippet: 'Build distributed settlement systems in Amsterdam.',
    originalUrl: 'https://careers.adyen.com/jobs/101'
  };

  const jobPersonio: JobSummary = {
    id: 102,
    title: 'Staff Fullstack Engineer (Angular & Java)',
    companyName: 'Personio SE',
    location: 'Munich, Germany',
    city: 'Munich',
    country: 'Germany',
    workArrangement: 'HYBRID',
    employmentType: 'FULL_TIME',
    experienceLevel: 'LEAD',
    salaryMin: 95000,
    salaryMax: 125000,
    salaryCurrency: 'EUR',
    salaryPeriod: 'YEARLY',
    sponsorStatus: 'RELOCATION_OFFERED',
    matchPercentage: 78,
    sourceProvider: 'ADZUNA',
    postedAt: new Date(Date.now() - 6 * 60 * 60 * 1000).toISOString(),
    isSaved: false,
    isInterested: false,
    isHidden: false,
    skills: ['Angular', 'TypeScript', 'Java'],
    descriptionSnippet: 'Lead HR platform experiences with EU Blue Card support.',
    originalUrl: 'https://careers.personio.de/jobs/102'
  };

  const detailAdyen: JobDetail = {
    ...jobAdyen,
    employmentType: 'FULL_TIME',
    description: 'Design resilient payment microservices with full IND visa sponsorship.',
    skills: ['Java 21', 'Spring Boot', 'PostgreSQL', 'Kafka'],
    originalUrl: 'https://careers.adyen.com/jobs/101',
    applicationUrl: 'https://careers.adyen.com/jobs/101/apply',
    isActive: true,
    hasApplied: false,
    sponsorshipBreakdown: {
      sponsorStatus: 'VERIFIED_SPONSOR',
      isVerifiedSponsor: true,
      confidenceLevel: 'VERIFIED',
      summary: 'Recognized referent listed in NL IND public register.',
      evidenceSignals: ['Employer listed in NL IND public register'],
      registryRecords: [
        {
          id: 1,
          country: 'Netherlands',
          registrySource: 'NL_IND_PUBLIC_REGISTER',
          registryIdentifier: 'IND-34259528',
          officialRegisteredName: 'Adyen N.V.',
          status: 'RECOGNIZED_SPONSOR',
          evidenceNotes: 'Verified',
          sourceUrl: 'https://ind.nl/en/public-register-recognised-sponsors',
          verifiedAt: '2026-11-01T00:00:00Z'
        }
      ]
    },
    matchBreakdown: {
      finalScore: 89.5,
      matchPercentage: 90,
      deterministicScore: 87,
      aiScore: 92,
      skillScore: 95,
      experienceScore: 88,
      titleScore: 94,
      locationScore: 100,
      sponsorshipScore: 100,
      salaryScore: 85,
      matchingSkills: ['Java 21', 'Spring Boot'],
      missingSkills: ['Kafka'],
      aiRationale: 'Strong backend Java and Payments fit.',
      calculatedAt: '2026-11-08T10:00:00Z'
    }
  };

  const detailPersonio: JobDetail = {
    ...jobPersonio,
    employmentType: 'FULL_TIME',
    description: 'Architect Angular + Java SaaS workflows in Munich.',
    skills: ['Angular', 'TypeScript', 'Java'],
    originalUrl: 'https://careers.personio.de/jobs/102',
    isActive: true,
    hasApplied: false,
    sponsorshipBreakdown: {
      sponsorStatus: 'RELOCATION_OFFERED',
      isVerifiedSponsor: false,
      confidenceLevel: 'MEDIUM',
      summary: 'Relocation and EU Blue Card support mentioned in posting.',
      evidenceSignals: ['Posting mentions EU Blue Card'],
      registryRecords: []
    },
    matchBreakdown: null
  };

  function makePage(
    content: readonly JobSummary[],
    pageNumber = 0,
    totalElements = content.length,
    totalPages = 1
  ): JobSummaryPageResponse {
    return {
      content,
      pageNumber,
      pageSize: 20,
      totalElements,
      totalPages,
      isFirst: pageNumber === 0,
      isLast: pageNumber >= totalPages - 1,
      hasNext: pageNumber < totalPages - 1,
      hasPrevious: pageNumber > 0
    };
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JobsComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    const doc = TestBed.inject(DOCUMENT);
    if (doc.defaultView) {
      openSpy = spyOn(doc.defaultView, 'open').and.returnValue(null);
    }

    fixture = TestBed.createComponent(JobsComponent);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushAssistantHistory(jobId: number): void {
    httpMock
      .match(req => req.url === `/api/v1/jobs/${jobId}/assistant/history` && req.method === 'GET')
      .forEach(req => req.flush([]));
  }

  function bootstrapInitialLoad(
    page: JobSummaryPageResponse = makePage([jobAdyen, jobPersonio]),
    firstDetail: JobDetail = detailAdyen
  ): void {
    fixture.detectChanges();
    const listReq = httpMock.expectOne(req => req.url === '/api/v1/jobs' && req.method === 'GET');
    listReq.flush(page);

    if (page.content.length > 0) {
      const detailReq = httpMock.expectOne(`/api/v1/jobs/${page.content[0].id}`);
      detailReq.flush(firstDetail);
    }
    fixture.detectChanges();
    if (page.content.length > 0) {
      flushAssistantHistory(page.content[0].id);
      fixture.detectChanges();
    }
  }

  it('should load paginated jobs on init, auto-load first job detail, and inspect a second job', () => {
    bootstrapInitialLoad();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('app-job-card').length).toBe(2);
    const inspector = host.querySelector('[data-jobs-inspector]');
    expect(inspector?.textContent).toContain('Adyen N.V.');
    expect(inspector?.textContent).toContain('NL_IND_PUBLIC_REGISTER');
    expect(inspector?.textContent).toContain('Strong backend Java and Payments fit.');

    // Click second card -> fetches GET /api/v1/jobs/102
    const cards = host.querySelectorAll('app-job-card');
    cards[1].querySelector<HTMLButtonElement>('[data-card-action="inspect"]')?.click();

    const detail2Req = httpMock.expectOne('/api/v1/jobs/102');
    expect(detail2Req.request.method).toBe('GET');
    detail2Req.flush(detailPersonio);
    fixture.detectChanges();
    flushAssistantHistory(102);
    fixture.detectChanges();

    expect(host.querySelector('[data-jobs-inspector]')?.textContent).toContain('Personio SE');
    expect(host.querySelector('[data-assistant-panel]')).toBeTruthy();
    expect(host.querySelector('[data-inspector-action="interview-prep"]')).toBeTruthy();
    expect(host.querySelector('[data-jobs-inspector]')?.textContent).toContain(
      'Architect Angular + Java SaaS workflows in Munich.'
    );
  });

  it('should persist save, interested, hide confirmation, and apply-redirect markApplied interactions', () => {
    bootstrapInitialLoad();

    const host = fixture.nativeElement as HTMLElement;
    const cards = host.querySelectorAll('app-job-card');

    // 1. Save toggle on first card
    cards[0].querySelector<HTMLButtonElement>('[data-card-action="save"]')?.click();
    const saveReq = httpMock.expectOne('/api/v1/jobs/101/save');
    expect(saveReq.request.method).toBe('POST');
    const savedState: JobInteractionResponse = {
      jobId: 101,
      isSaved: true,
      savedAt: '2026-11-09T10:00:00Z',
      isInterested: false,
      interestedAt: null,
      isHidden: false,
      hiddenAt: null,
      hasApplied: false,
      appliedAt: null,
      redirectClickedAt: null
    };
    saveReq.flush(savedState);
    fixture.detectChanges();

    expect(host.querySelector('[data-jobs-feedback]')?.textContent).toContain('Saved');
    expect(
      cards[0].querySelector('[data-card-action="save"]')?.getAttribute('aria-pressed')
    ).toBe('true');

    // 2. Apply redirect -> opens safe URL and shows confirm-applied bar -> Confirm marks hasApplied
    cards[0].querySelector<HTMLButtonElement>('[data-card-action="apply"]')?.click();
    const redirectReq = httpMock.expectOne('/api/v1/jobs/101/apply-redirect');
    expect(redirectReq.request.method).toBe('POST');
    expect(redirectReq.request.body).toEqual({ markApplied: false });

    const redirectResp: ApplyRedirectResponse = {
      jobId: 101,
      targetUrl: 'https://careers.adyen.com/jobs/101/apply',
      redirectClickedAt: '2026-11-09T10:02:00Z',
      hasApplied: false,
      appliedAt: null,
      interaction: { ...savedState, redirectClickedAt: '2026-11-09T10:02:00Z' }
    };
    redirectReq.flush(redirectResp);
    fixture.detectChanges();

    if (openSpy) {
      expect(openSpy).toHaveBeenCalledWith(
        'https://careers.adyen.com/jobs/101/apply',
        '_blank',
        'noopener,noreferrer'
      );
    }
    expect(host.querySelector('[data-jobs-apply-confirm]')).toBeTruthy();

    host.querySelector<HTMLButtonElement>('[data-jobs-action="confirm-applied"]')?.click();
    const markReq = httpMock.expectOne('/api/v1/jobs/101/apply-redirect');
    expect(markReq.request.body).toEqual({ markApplied: true });
    markReq.flush({
      ...redirectResp,
      hasApplied: true,
      appliedAt: '2026-11-09T10:02:30Z',
      interaction: {
        ...savedState,
        hasApplied: true,
        appliedAt: '2026-11-09T10:02:30Z',
        redirectClickedAt: '2026-11-09T10:02:00Z'
      }
    });
    fixture.detectChanges();

    expect(host.querySelector('[data-jobs-apply-confirm]')).toBeNull();
    expect(host.querySelector('[data-inspector-applied]')).toBeTruthy();

    // 3. Hide job -> confirms modal -> POST /hide -> refetches default list
    cards[1].querySelector<HTMLButtonElement>('[data-card-action="hide"]')?.click();
    fixture.detectChanges();

    const dialog = host.querySelector('[role="dialog"]');
    expect(dialog).toBeTruthy();
    dialog?.querySelector<HTMLButtonElement>('[data-dialog-action="confirm"]')?.click();

    const hideReq = httpMock.expectOne('/api/v1/jobs/102/hide');
    expect(hideReq.request.method).toBe('POST');
    hideReq.flush({
      jobId: 102,
      isSaved: false,
      savedAt: null,
      isInterested: false,
      interestedAt: null,
      isHidden: true,
      hiddenAt: '2026-11-09T10:03:00Z',
      hasApplied: false,
      appliedAt: null,
      redirectClickedAt: null
    });

    // Refetches page without hidden job 102
    const refreshReq = httpMock.expectOne(req => req.url === '/api/v1/jobs' && req.method === 'GET');
    refreshReq.flush(makePage([jobAdyen]));
    fixture.detectChanges();

    expect(host.querySelectorAll('app-job-card').length).toBe(1);
    expect(host.querySelector('[data-jobs-feedback]')?.textContent).toContain('Hid');
  });

  it('should filter by backend params, navigate pagination, and recover from search errors cleanly', () => {
    bootstrapInitialLoad(makePage([jobAdyen, jobPersonio], 0, 25, 2), detailAdyen);

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-jobs-pagination]')?.textContent).toContain('Showing 1–2 of 25 roles');

    // Click Next page
    host.querySelector<HTMLButtonElement>('[data-jobs-page="next"]')?.click();
    const page2Req = httpMock.expectOne(
      req => req.url === '/api/v1/jobs' && req.params.get('page') === '1'
    );
    page2Req.flush(makePage([jobPersonio], 1, 25, 2));
    httpMock.expectOne('/api/v1/jobs/102').flush(detailPersonio);
    fixture.detectChanges();
    flushAssistantHistory(102);
    fixture.detectChanges();

    expect(host.querySelector('[data-jobs-pagination]')?.textContent).toContain('Showing 21–21 of 25 roles');

    // Filter change resets page back to 0 and triggers error -> retry flow
    const countrySelect = host.querySelector<HTMLSelectElement>('[data-filter="country"]')!;
    countrySelect.value = 'Switzerland';
    countrySelect.dispatchEvent(new Event('change'));

    const failReq = httpMock.expectOne(
      req =>
        req.url === '/api/v1/jobs' &&
        req.params.get('country') === 'Switzerland' &&
        req.params.get('page') === '0'
    );
    failReq.flush({ message: 'Search backend busy' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    expect(host.querySelector('[data-jobs-error]')?.textContent).toContain('Search backend busy');

    host.querySelector<HTMLButtonElement>('[data-jobs-action="retry-search"]')?.click();
    const retryReq = httpMock.expectOne(
      req => req.url === '/api/v1/jobs' && req.params.get('country') === 'Switzerland'
    );
    retryReq.flush(makePage([], 0, 0, 0));
    fixture.detectChanges();

    expect(host.querySelector('[data-jobs-empty]')).toBeTruthy();
  });

  it('should compute single-job match breakdown and recalculate all job matches from discovery toolbar', () => {
    bootstrapInitialLoad();

    const host = fixture.nativeElement as HTMLElement;
    const cards = host.querySelectorAll('app-job-card');

    // Select Personio (which starts with matchBreakdown: null)
    cards[1].querySelector<HTMLButtonElement>('[data-card-action="inspect"]')?.click();
    httpMock.expectOne('/api/v1/jobs/102').flush(detailPersonio);
    fixture.detectChanges();
    flushAssistantHistory(102);
    fixture.detectChanges();

    const calcBtn = host.querySelector<HTMLButtonElement>('[data-inspector-action="calculate-match"]');
    expect(calcBtn).toBeTruthy();
    calcBtn?.click();

    const calcReq = httpMock.expectOne('/api/v1/jobs/102/intelligence/match-score');
    expect(calcReq.request.method).toBe('GET');

    const calculatedBreakdown: JobMatchBreakdown = {
      finalScore: 84.5,
      matchPercentage: 85,
      deterministicScore: 84.5,
      aiScore: null,
      skillScore: 100,
      experienceScore: 80,
      titleScore: 90,
      locationScore: 100,
      sponsorshipScore: 55,
      salaryScore: 100,
      matchingSkills: ['Angular', 'TypeScript', 'Java'],
      missingSkills: [],
      experienceCompatibility: 'Close Fit · 6 yrs vs 7 yrs required (Lead)',
      recommendationReasons: [
        'Matched all 3 core technical skills (Angular, TypeScript, Java).',
        'Location matches your target hub: Munich, Germany (Hybrid).'
      ],
      aiRationale: null,
      calculatedAt: '2026-11-09T10:15:00Z'
    };
    calcReq.flush(calculatedBreakdown);
    fixture.detectChanges();

    expect(host.querySelector('[data-inspector-match]')?.textContent).toContain('85% composite');
    expect(host.querySelector('[data-inspector-experience-fit]')?.textContent).toContain('Close Fit');
    expect(host.querySelector('[data-inspector-reasons]')?.textContent).toContain(
      'Matched all 3 core technical skills (Angular, TypeScript, Java).'
    );
    expect(host.querySelector('[data-jobs-feedback]')?.textContent).toContain(
      'Computed 85% deterministic match for this role.'
    );

    // Trigger batch recalculate from toolbar
    host.querySelector<HTMLButtonElement>('[data-jobs-action="recalculate-matches"]')?.click();
    const recalcReq = httpMock.expectOne('/api/v1/matching/recalculate');
    expect(recalcReq.request.method).toBe('POST');
    expect(recalcReq.request.body).toEqual({});

    const batchSummary: BatchMatchRecalculationResponse = {
      evaluatedJobsCount: 2,
      highMatchJobsCount: 2,
      minMatchThreshold: 70,
      calculatedAt: '2026-11-09T10:16:00Z',
      weightsUsed: {
        technicalSkills: 40,
        experience: 20,
        jobTitle: 15,
        location: 10,
        sponsorship: 10,
        salary: 5
      },
      topMatches: [
        {
          jobId: 101,
          title: jobAdyen.title,
          companyName: jobAdyen.companyName,
          country: 'Netherlands',
          matchPercentage: 90,
          finalScore: 90,
          experienceCompatibility: 'Strong Fit · 6 yrs vs 5 yrs required (Senior)'
        }
      ]
    };
    recalcReq.flush(batchSummary);

    // Batch handler refreshes both job list and active detail
    const refreshSearchReq = httpMock.expectOne(
      req => req.url === '/api/v1/jobs' && req.method === 'GET'
    );
    const refreshDetailReq = httpMock.expectOne('/api/v1/jobs/102');

    refreshDetailReq.flush({ ...detailPersonio, matchBreakdown: calculatedBreakdown, matchPercentage: 85 });
    refreshSearchReq.flush(makePage([jobAdyen, { ...jobPersonio, matchPercentage: 85 }]));
    fixture.detectChanges();

    expect(host.querySelector('[data-jobs-feedback]')?.textContent).toContain(
      'Scored 2 active roles (2 at ≥ 70% match).'
    );
  });

  it('should analyze selected job description with AI and load versioned analysis history', () => {
    bootstrapInitialLoad();

    const host = fixture.nativeElement as HTMLElement;
    const analyzeBtn = host.querySelector<HTMLButtonElement>('[data-inspector-action="analyze-jd"]');
    expect(analyzeBtn).toBeTruthy();
    analyzeBtn?.click();

    const postReq = httpMock.expectOne('/api/v1/jobs/101/intelligence/analyze-jd');
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body).toEqual({ forceRefresh: true });

    const analysisV1: JobAiAnalysis = {
      id: 40,
      jobId: 101,
      analysisVersion: 1,
      extractedTitle: 'Senior Java & Spring Boot Backend Engineer',
      extractedCompany: 'Adyen N.V.',
      extractedCountry: 'Netherlands',
      extractedLocation: 'Amsterdam, Netherlands',
      requiredExperienceYears: 5,
      requiredExperienceLevel: 'SENIOR',
      requiredExperienceSummary: '5+ years distributed payment systems',
      requiredSkills: ['Java 21', 'Spring Boot', 'PostgreSQL'],
      niceToHaveSkills: ['Kafka'],
      responsibilities: ['Design resilient settlement APIs'],
      benefits: ['25 vacation days'],
      languageRequirements: ['English (Fluent)'],
      salaryMin: 85000,
      salaryMax: 112000,
      salaryCurrency: 'EUR',
      salaryPeriod: 'YEARLY',
      workArrangement: 'HYBRID',
      employmentType: 'FULL_TIME',
      sponsorshipOffered: true,
      visaSponsorshipAnalysis: 'IND sponsor verified.',
      relocationAssistanceOffered: true,
      relocationAnalysis: 'International relocation covered.',
      summary: 'High-scale backend role in Amsterdam.',
      aiProvider: 'GEMINI',
      aiModelUsed: 'gemini-2.5-flash',
      analyzedAt: '2026-11-09T10:30:00Z'
    };
    postReq.flush(analysisV1);
    fixture.detectChanges();

    expect(host.querySelector('[data-inspector-ai]')?.textContent).toContain('GEMINI · gemini-2.5-flash');
    expect(host.querySelector('[data-inspector-ai-version]')?.textContent).toContain('v1');
    expect(host.querySelector('[data-jobs-feedback]')?.textContent).toContain(
      'AI extracted structured role requirements (v1).'
    );

    host.querySelector<HTMLButtonElement>('[data-inspector-action="toggle-ai-history"]')?.click();
    const histReq = httpMock.expectOne('/api/v1/jobs/101/intelligence/analyze-jd/history');
    expect(histReq.request.method).toBe('GET');
    histReq.flush([analysisV1]);
    fixture.detectChanges();

    expect(host.querySelector('[data-inspector-ai-history]')?.textContent).toContain('v1');
  });

  it('should evaluate AI semantic match from inspector and render separate semantic breakdown sections', () => {
    bootstrapInitialLoad();

    const host = fixture.nativeElement as HTMLElement;
    const cards = host.querySelectorAll('app-job-card');

    cards[1].querySelector<HTMLButtonElement>('[data-card-action="inspect"]')?.click();
    httpMock.expectOne('/api/v1/jobs/102').flush(detailPersonio);
    fixture.detectChanges();
    flushAssistantHistory(102);
    fixture.detectChanges();

    const aiMatchBtn = host.querySelector<HTMLButtonElement>('[data-inspector-action="evaluate-ai-match"]');
    expect(aiMatchBtn).toBeTruthy();
    expect(aiMatchBtn?.textContent).toContain('Run AI Semantic Match');
    aiMatchBtn?.click();

    const postReq = httpMock.expectOne('/api/v1/jobs/102/intelligence/ai-match');
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body).toEqual({ forceRefresh: true });

    const semanticBreakdown: JobMatchBreakdown = {
      finalScore: 87.5,
      matchPercentage: 88,
      deterministicScore: 84,
      aiScore: 91,
      skillScore: 100,
      experienceScore: 82,
      titleScore: 90,
      locationScore: 100,
      sponsorshipScore: 55,
      salaryScore: 100,
      matchingSkills: ['Angular', 'TypeScript', 'Java'],
      missingSkills: [],
      experienceCompatibility: 'Close Fit · 6 yrs vs 7 yrs required (Lead)',
      recommendationReasons: ['Matched all 3 core technical skills (Angular, TypeScript, Java).'],
      aiRationale: 'Strong Angular + Java fullstack overlap backed by enterprise SaaS project delivery.',
      aiStrongMatches: ['Direct fullstack overlap across Angular 19, TypeScript & Spring Java backend'],
      aiMissingSkills: ['GraphQL federation'],
      aiExperienceGaps: ['6 yrs experience is slightly below 7+ yr Staff/Lead benchmark'],
      aiPotentialConcerns: ['Confirm Munich relocation package & EU Blue Card eligibility before final stage'],
      aiImprovementSuggestions: ['Highlight team leadership scope and system design projects in master resume'],
      aiProvider: 'GEMINI',
      aiModelUsed: 'gemini-2.5-flash',
      aiEvaluatedAt: '2026-11-09T10:35:00Z',
      calculatedAt: '2026-11-09T10:35:00Z'
    };
    postReq.flush(semanticBreakdown);
    fixture.detectChanges();

    expect(host.querySelector('[data-inspector-match]')?.textContent).toContain('88% composite');
    expect(host.querySelector('[data-inspector-ai-match]')?.textContent).toContain('AI Semantic 91%');
    expect(host.querySelector('[data-inspector-ai-strong-matches]')?.textContent).toContain('Angular 19');
    expect(host.querySelector('[data-inspector-ai-missing-skills]')?.textContent).toContain('GraphQL federation');
    expect(host.querySelector('[data-inspector-ai-experience-gaps]')?.textContent).toContain('Staff/Lead benchmark');
    expect(host.querySelector('[data-inspector-ai-concerns]')?.textContent).toContain('EU Blue Card eligibility');
    expect(host.querySelector('[data-inspector-ai-suggestions]')?.textContent).toContain(
      'Highlight team leadership scope'
    );
    expect(host.querySelector('[data-jobs-feedback]')?.textContent).toContain(
      'Evaluated 91% AI semantic match (overall 88%).'
    );
  });
});
