import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ApplyRedirectResponse,
  BatchMatchRecalculationResponse,
  JobAiAnalysis,
  JobDetail,
  JobInteractionResponse,
  JobMatchBreakdown,
  JobSummaryPageResponse,
  createDefaultJobSearchFilters
} from '../models/job.models';
import { JobService } from './job.service';

describe('JobService', () => {
  let service: JobService;
  let httpMock: HttpTestingController;

  const samplePage: JobSummaryPageResponse = {
    content: [
      {
        id: 1042,
        title: 'Senior Java & Spring Boot Engineer',
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
        postedAt: '2026-11-08T10:00:00Z',
        isSaved: false,
        isInterested: false,
        isHidden: false,
        skills: ['Java 21', 'Spring Boot'],
        descriptionSnippet: 'Build distributed payments APIs.',
        originalUrl: 'https://careers.adyen.com/jobs/1042'
      }
    ],
    pageNumber: 0,
    pageSize: 20,
    totalElements: 1,
    totalPages: 1,
    isFirst: true,
    isLast: true,
    hasNext: false,
    hasPrevious: false
  };

  const sampleDetail: JobDetail = {
    id: 1042,
    title: 'Senior Java & Spring Boot Engineer',
    companyName: 'Adyen N.V.',
    country: 'Netherlands',
    city: 'Amsterdam',
    location: 'Amsterdam, Netherlands',
    workArrangement: 'HYBRID',
    employmentType: 'FULL_TIME',
    experienceLevel: 'SENIOR',
    minExperienceYears: 5,
    salaryMin: 85000,
    salaryMax: 112000,
    salaryCurrency: 'EUR',
    salaryPeriod: 'YEARLY',
    description: 'Full payment systems role description.',
    skills: ['Java 21', 'Spring Boot', 'Kafka'],
    sponsorStatus: 'VERIFIED_SPONSOR',
    matchPercentage: 90,
    sourceProvider: 'ARBEITNOW',
    originalUrl: 'https://careers.adyen.com/jobs/1042',
    applicationUrl: 'https://careers.adyen.com/jobs/1042/apply',
    postedAt: '2026-11-08T10:00:00Z',
    isActive: true,
    isSaved: false,
    isInterested: false,
    isHidden: false,
    hasApplied: false,
    sponsorshipBreakdown: {
      sponsorStatus: 'VERIFIED_SPONSOR',
      isVerifiedSponsor: true,
      confidenceLevel: 'VERIFIED',
      summary: 'IND Recognized Sponsor in the Netherlands.',
      evidenceSignals: ['Verified on IND public register'],
      registryRecords: []
    },
    matchBreakdown: {
      finalScore: 90,
      matchPercentage: 90,
      deterministicScore: 88,
      aiScore: 92,
      skillScore: 95,
      experienceScore: 90,
      titleScore: 95,
      locationScore: 100,
      sponsorshipScore: 100,
      salaryScore: 85,
      matchingSkills: ['Java 21', 'Spring Boot'],
      missingSkills: ['Kafka'],
      experienceCompatibility: 'Strong Fit · 6 yrs vs 5 yrs required (Senior)',
      recommendationReasons: [
        'Matched 2 of 3 core technical skills (Java 21, Spring Boot).',
        'Experience meets Senior target (6 yrs vs 5 yrs required).',
        'Missing skill: Kafka.'
      ],
      aiRationale: 'Strong Java Payments match.',
      calculatedAt: '2026-11-08T11:00:00Z'
    }
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(JobService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should search jobs with clean query params and update signals', () => {
    let received: JobSummaryPageResponse | undefined;
    service
      .searchJobs(
        {
          ...createDefaultJobSearchFilters(),
          query: '  Spring Boot ',
          country: 'Netherlands',
          city: 'Amsterdam',
          workArrangement: 'HYBRID',
          employmentType: 'FULL_TIME',
          experienceLevel: 'SENIOR',
          sponsorStatus: 'VERIFIED_SPONSOR',
          minMatchScore: 65,
          minSalary: 80000,
          savedOnly: true,
          interestedOnly: false,
          includeHidden: true,
          sort: 'matchScore,desc'
        },
        1,
        10
      )
      .subscribe(res => (received = res));

    expect(service.isSearching()).toBeTrue();

    const req = httpMock.expectOne(r => r.url === '/api/v1/jobs');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('query')).toBe('Spring Boot');
    expect(req.request.params.get('country')).toBe('Netherlands');
    expect(req.request.params.get('city')).toBe('Amsterdam');
    expect(req.request.params.get('workArrangement')).toBe('HYBRID');
    expect(req.request.params.get('employmentType')).toBe('FULL_TIME');
    expect(req.request.params.get('experienceLevel')).toBe('SENIOR');
    expect(req.request.params.get('sponsorStatus')).toBe('VERIFIED_SPONSOR');
    expect(req.request.params.get('minMatchScore')).toBe('65');
    expect(req.request.params.get('minSalary')).toBe('80000');
    expect(req.request.params.get('savedOnly')).toBe('true');
    expect(req.request.params.has('interestedOnly')).toBeFalse();
    expect(req.request.params.get('includeHidden')).toBe('true');
    expect(req.request.params.get('sort')).toBe('matchScore,desc');

    req.flush(samplePage);

    expect(service.isSearching()).toBeFalse();
    expect(received).toEqual(samplePage);
    expect(service.jobs().length).toBe(1);
    expect(service.searchResult()?.totalElements).toBe(1);
  });

  it('should load job detail via GET /api/v1/jobs/{id} and handle failures cleanly', () => {
    let loaded: JobDetail | undefined;
    service.getJobDetail(1042).subscribe(detail => (loaded = detail));

    expect(service.isLoadingDetail()).toBeTrue();
    const okReq = httpMock.expectOne('/api/v1/jobs/1042');
    expect(okReq.request.method).toBe('GET');
    okReq.flush(sampleDetail);

    expect(service.isLoadingDetail()).toBeFalse();
    expect(loaded).toEqual(sampleDetail);
    expect(service.selectedJobDetail()).toEqual(sampleDetail);

    let failed = false;
    service.getJobDetail(9999).subscribe({ error: () => (failed = true) });
    const errReq = httpMock.expectOne('/api/v1/jobs/9999');
    errReq.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });

    expect(failed).toBeTrue();
    expect(service.isLoadingDetail()).toBeFalse();
  });

  it('should persist save, interested, hide, and apply-redirect interactions and synchronize signals', () => {
    service.searchJobs().subscribe();
    httpMock.expectOne(r => r.url === '/api/v1/jobs').flush(samplePage);

    service.getJobDetail(1042).subscribe();
    httpMock.expectOne('/api/v1/jobs/1042').flush(sampleDetail);

    const savedResponse: JobInteractionResponse = {
      jobId: 1042,
      isSaved: true,
      savedAt: '2026-11-09T09:00:00Z',
      isInterested: false,
      interestedAt: null,
      isHidden: false,
      hiddenAt: null,
      hasApplied: false,
      appliedAt: null,
      redirectClickedAt: null
    };

    service.setSaved(1042, true).subscribe();
    expect(service.isInteractionPending(1042)).toBeTrue();
    const saveReq = httpMock.expectOne('/api/v1/jobs/1042/save');
    expect(saveReq.request.method).toBe('POST');
    saveReq.flush(savedResponse);
    expect(service.isInteractionPending(1042)).toBeFalse();
    expect(service.jobs()[0].isSaved).toBeTrue();
    expect(service.selectedJobDetail()?.isSaved).toBeTrue();

    service.setInterested(1042, true).subscribe();
    const interestedReq = httpMock.expectOne('/api/v1/jobs/1042/interested');
    expect(interestedReq.request.method).toBe('POST');
    interestedReq.flush({
      ...savedResponse,
      isInterested: true,
      interestedAt: '2026-11-09T09:01:00Z'
    });
    expect(service.jobs()[0].isInterested).toBeTrue();
    expect(service.selectedJobDetail()?.isInterested).toBeTrue();

    service.setHidden(1042, true).subscribe();
    const hideReq = httpMock.expectOne('/api/v1/jobs/1042/hide');
    expect(hideReq.request.method).toBe('POST');
    hideReq.flush({
      ...savedResponse,
      isInterested: true,
      isHidden: true,
      hiddenAt: '2026-11-09T09:02:00Z'
    });
    expect(service.jobs()[0].isHidden).toBeTrue();
    expect(service.selectedJobDetail()?.isHidden).toBeTrue();

    service.setHidden(1042, false).subscribe();
    const unhideReq = httpMock.expectOne('/api/v1/jobs/1042/hide');
    expect(unhideReq.request.method).toBe('DELETE');
    unhideReq.flush({ ...savedResponse, isInterested: true, isHidden: false });
    expect(service.jobs()[0].isHidden).toBeFalse();

    const redirectResponse: ApplyRedirectResponse = {
      jobId: 1042,
      targetUrl: 'https://careers.adyen.com/jobs/1042/apply',
      redirectClickedAt: '2026-11-09T09:05:00Z',
      hasApplied: true,
      appliedAt: '2026-11-09T09:05:00Z',
      interaction: {
        ...savedResponse,
        isInterested: true,
        hasApplied: true,
        appliedAt: '2026-11-09T09:05:00Z',
        redirectClickedAt: '2026-11-09T09:05:00Z'
      }
    };

    service.trackApplyRedirect(1042, { markApplied: true }).subscribe();
    const applyReq = httpMock.expectOne('/api/v1/jobs/1042/apply-redirect');
    expect(applyReq.request.method).toBe('POST');
    expect(applyReq.request.body).toEqual({ markApplied: true });
    applyReq.flush(redirectResponse);

    expect(service.selectedJobDetail()?.hasApplied).toBeTrue();
    expect(service.selectedJobDetail()?.redirectClickedAt).toBe('2026-11-09T09:05:00Z');
  });

  it('should calculate deterministic match score for a job and update detail and search signals', () => {
    service.searchJobs().subscribe();
    httpMock.expectOne(r => r.url === '/api/v1/jobs').flush(samplePage);

    service.getJobDetail(1042).subscribe();
    httpMock.expectOne('/api/v1/jobs/1042').flush({
      ...sampleDetail,
      matchPercentage: 70,
      matchBreakdown: null
    });

    const updatedBreakdown: JobMatchBreakdown = {
      finalScore: 86.5,
      matchPercentage: 87,
      deterministicScore: 86.5,
      aiScore: null,
      skillScore: 100,
      experienceScore: 90,
      titleScore: 95,
      locationScore: 100,
      sponsorshipScore: 100,
      salaryScore: 80,
      matchingSkills: ['Java 21', 'Spring Boot', 'Kafka'],
      missingSkills: [],
      experienceCompatibility: 'Strong Fit · 6 yrs vs 5 yrs required (Senior)',
      recommendationReasons: ['Matched all 3 core technical skills (Java 21, Spring Boot, Kafka).'],
      aiRationale: null,
      calculatedAt: '2026-11-09T10:15:00Z'
    };

    let received: JobMatchBreakdown | undefined;
    service.calculateJobMatch(1042).subscribe(res => (received = res));
    expect(service.isMatchCalculationPending(1042)).toBeTrue();

    const calcReq = httpMock.expectOne('/api/v1/jobs/1042/intelligence/match-score');
    expect(calcReq.request.method).toBe('GET');
    calcReq.flush(updatedBreakdown);

    expect(service.isMatchCalculationPending(1042)).toBeFalse();
    expect(received).toEqual(updatedBreakdown);
    expect(service.selectedJobDetail()?.matchPercentage).toBe(87);
    expect(service.selectedJobDetail()?.matchBreakdown).toEqual(updatedBreakdown);
    expect(service.jobs()[0].matchPercentage).toBe(87);
  });

  it('should recalculate all job matches via POST /api/v1/matching/recalculate and clean up busy state', () => {
    const summary: BatchMatchRecalculationResponse = {
      evaluatedJobsCount: 12,
      highMatchJobsCount: 5,
      minMatchThreshold: 70,
      calculatedAt: '2026-11-09T10:20:00Z',
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
          jobId: 1042,
          title: 'Senior Java & Spring Boot Engineer',
          companyName: 'Adyen N.V.',
          country: 'Netherlands',
          matchPercentage: 87,
          finalScore: 86.5,
          experienceCompatibility: 'Strong Fit · 6 yrs vs 5 yrs required (Senior)'
        }
      ]
    };

    let received: BatchMatchRecalculationResponse | undefined;
    service.recalculateAllMatches().subscribe(res => (received = res));
    expect(service.isRecalculatingMatches()).toBeTrue();

    const recalcReq = httpMock.expectOne('/api/v1/matching/recalculate');
    expect(recalcReq.request.method).toBe('POST');
    expect(recalcReq.request.body).toEqual({});
    recalcReq.flush(summary);

    expect(service.isRecalculatingMatches()).toBeFalse();
    expect(received).toEqual(summary);

    let failed = false;
    service.recalculateAllMatches().subscribe({ error: () => (failed = true) });
    expect(service.isRecalculatingMatches()).toBeTrue();
    httpMock
      .expectOne('/api/v1/matching/recalculate')
      .flush({ message: 'Profile incomplete' }, { status: 400, statusText: 'Bad Request' });

    expect(failed).toBeTrue();
    expect(service.isRecalculatingMatches()).toBeFalse();
  });

  it('should evaluate AI semantic job match via POST /api/v1/jobs/{id}/intelligence/ai-match and sync signals', () => {
    service.searchJobs().subscribe();
    httpMock.expectOne(r => r.url === '/api/v1/jobs').flush(samplePage);

    service.getJobDetail(1042).subscribe();
    httpMock.expectOne('/api/v1/jobs/1042').flush({
      ...sampleDetail,
      matchPercentage: 84,
      matchBreakdown: null
    });

    const aiMatchedBreakdown: JobMatchBreakdown = {
      finalScore: 89,
      matchPercentage: 89,
      deterministicScore: 86,
      aiScore: 92,
      skillScore: 95,
      experienceScore: 90,
      titleScore: 95,
      locationScore: 100,
      sponsorshipScore: 100,
      salaryScore: 85,
      matchingSkills: ['Java 21', 'Spring Boot'],
      missingSkills: ['Kafka'],
      experienceCompatibility: 'Strong Fit · 6 yrs vs 5 yrs required (Senior)',
      recommendationReasons: ['Matched 2 of 3 core technical skills (Java 21, Spring Boot).'],
      aiRationale: 'Strong Java FinTech backend alignment; master resume highlights high-throughput clearing work.',
      aiStrongMatches: [
        '6 yrs Java 21 & Spring Boot microservice leadership aligns with settlement platform core',
        'Master resume SEPA gateway project mirrors payment orchestration responsibilities'
      ],
      aiMissingSkills: ['Production Apache Kafka consumer tuning'],
      aiExperienceGaps: ['Limited evidence of multi-region active-active failover operations'],
      aiPotentialConcerns: ['Requires NL Highly Skilled Migrant visa sponsorship'],
      aiImprovementSuggestions: ['Quantify SLA latency in master resume settlement project'],
      aiProvider: 'GEMINI',
      aiModelUsed: 'gemini-2.5-flash',
      aiEvaluatedAt: '2026-11-09T10:22:00Z',
      calculatedAt: '2026-11-09T10:22:00Z'
    };

    let received: JobMatchBreakdown | undefined;
    service.evaluateAiJobMatch(1042, true).subscribe(res => (received = res));
    expect(service.isAiMatchPending(1042)).toBeTrue();

    const aiReq = httpMock.expectOne('/api/v1/jobs/1042/intelligence/ai-match');
    expect(aiReq.request.method).toBe('POST');
    expect(aiReq.request.body).toEqual({ forceRefresh: true });
    aiReq.flush(aiMatchedBreakdown);

    expect(service.isAiMatchPending(1042)).toBeFalse();
    expect(received).toEqual(aiMatchedBreakdown);
    expect(service.selectedJobDetail()?.matchPercentage).toBe(89);
    expect(service.selectedJobDetail()?.matchBreakdown?.aiStrongMatches?.length).toBe(2);
    expect(service.selectedJobDetail()?.matchBreakdown?.aiProvider).toBe('GEMINI');
    expect(service.jobs()[0].matchPercentage).toBe(89);

    let failed = false;
    service.evaluateAiJobMatch(1042, true).subscribe({ error: () => (failed = true) });
    expect(service.isAiMatchPending(1042)).toBeTrue();
    httpMock
      .expectOne('/api/v1/jobs/1042/intelligence/ai-match')
      .flush({ message: 'AI provider unavailable' }, { status: 503, statusText: 'Service Unavailable' });

    expect(failed).toBeTrue();
    expect(service.isAiMatchPending(1042)).toBeFalse();
  });

  it('should analyze job description with AI, sync detail signal, and fetch version history', () => {
    service.getJobDetail(1042).subscribe();
    httpMock.expectOne('/api/v1/jobs/1042').flush(sampleDetail);

    const analysisResult: JobAiAnalysis = {
      id: 91,
      jobId: 1042,
      analysisVersion: 2,
      extractedTitle: 'Senior Java & Spring Boot Engineer',
      extractedCompany: 'Adyen N.V.',
      extractedCountry: 'Netherlands',
      extractedLocation: 'Amsterdam, Netherlands',
      requiredExperienceYears: 5,
      requiredExperienceLevel: 'SENIOR',
      requiredExperienceSummary: '5+ years distributed backend Java systems',
      requiredSkills: ['Java 21', 'Spring Boot', 'PostgreSQL'],
      niceToHaveSkills: ['Kafka'],
      responsibilities: ['Architect core settlement APIs'],
      benefits: ['Relocation package'],
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
      relocationAnalysis: 'Full international relocation flight included.',
      summary: 'High-impact payment systems role in Amsterdam.',
      aiProvider: 'GEMINI',
      aiModelUsed: 'gemini-2.5-flash',
      analyzedAt: '2026-11-09T10:25:00Z'
    };

    let analyzed: JobAiAnalysis | undefined;
    service.analyzeJobDescription(1042, true).subscribe(res => (analyzed = res));
    expect(service.isAiAnalysisPending(1042)).toBeTrue();

    const postReq = httpMock.expectOne('/api/v1/jobs/1042/intelligence/analyze-jd');
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body).toEqual({ forceRefresh: true });
    postReq.flush(analysisResult);

    expect(service.isAiAnalysisPending(1042)).toBeFalse();
    expect(analyzed).toEqual(analysisResult);
    expect(service.selectedJobDetail()?.aiAnalysis).toEqual(analysisResult);

    let historyLoaded: readonly JobAiAnalysis[] | undefined;
    service.getJobAiAnalysisHistory(1042).subscribe(list => (historyLoaded = list));
    const histReq = httpMock.expectOne('/api/v1/jobs/1042/intelligence/analyze-jd/history');
    expect(histReq.request.method).toBe('GET');
    histReq.flush([
      analysisResult,
      { ...analysisResult, analysisVersion: 1, aiModelUsed: 'gpt-4o-mini', aiProvider: 'OPENAI' }
    ]);

    expect(historyLoaded?.length).toBe(2);
    expect(historyLoaded?.[0].analysisVersion).toBe(2);
  });

  it('should validate safe HTTP/HTTPS external URLs and block script schemes', () => {
    expect(service.isSafeExternalUrl('https://careers.adyen.com/jobs/1042')).toBeTrue();
    expect(service.isSafeExternalUrl('http://jobs.example.eu/apply')).toBeTrue();
    expect(service.isSafeExternalUrl('javascript:alert(1)')).toBeFalse();
    expect(service.isSafeExternalUrl('data:text/html,xss')).toBeFalse();
    expect(service.isSafeExternalUrl('  ')).toBeFalse();
    expect(service.isSafeExternalUrl(null)).toBeFalse();
  });
});

