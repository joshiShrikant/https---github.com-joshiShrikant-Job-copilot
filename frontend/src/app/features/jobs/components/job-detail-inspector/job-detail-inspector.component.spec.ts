import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { JobDetail, JobSummary } from '../../../../core/models/job.models';
import { JobDetailInspectorComponent } from './job-detail-inspector.component';

describe('JobDetailInspectorComponent', () => {
  let fixture: ComponentFixture<JobDetailInspectorComponent>;
  let httpMock: HttpTestingController;

  const sampleSummary: JobSummary = {
    id: 501,
    title: 'Staff Java Architect',
    companyName: 'Adyen N.V.',
    location: 'Amsterdam, Netherlands',
    city: 'Amsterdam',
    country: 'Netherlands',
    workArrangement: 'HYBRID',
    employmentType: 'FULL_TIME',
    experienceLevel: 'LEAD',
    salaryMin: 95000,
    salaryMax: 125000,
    salaryCurrency: 'EUR',
    salaryPeriod: 'YEARLY',
    sponsorStatus: 'VERIFIED_SPONSOR',
    matchPercentage: 89,
    sourceProvider: 'ARBEITNOW',
    postedAt: '2026-11-08T10:00:00Z',
    isSaved: false,
    isInterested: false,
    isHidden: false,
    skills: ['Java 21', 'Spring Boot'],
    descriptionSnippet: 'Lead core payment systems architecture.'
  };

  const sampleDetail: JobDetail = {
    ...sampleSummary,
    employmentType: 'FULL_TIME',
    description: 'Architect low-latency payment processing APIs across European hubs.',
    skills: ['Java 21', 'Spring Boot', 'Kafka'],
    originalUrl: 'javascript:alert(1)',
    isActive: true,
    isSaved: true,
    isInterested: true,
    hasApplied: true,
    sponsorshipBreakdown: {
      sponsorStatus: 'VERIFIED_SPONSOR',
      detailedStatus: 'VERIFIED_SPONSOR',
      detailedStatusLabel: 'Verified sponsor',
      vacancyStatus: 'SPONSORSHIP_MENTIONED',
      vacancyStatusLabel: 'Sponsorship explicitly mentioned',
      vacancySponsorshipConfirmed: true,
      vacancySponsorshipAssumed: false,
      isVerifiedSponsor: true,
      confidenceLevel: 'VERIFIED',
      summary: 'Confirmed IND recognized sponsor for Highly Skilled Migrants.',
      vacancyCaveat:
        'Recognized sponsor status does not guarantee every vacancy offers sponsorship.',
      officialRegisterUrl: 'https://ind.nl/en/public-register-recognised-sponsors',
      evidenceSignals: ['IND register match'],
      registryRecords: [
        {
          id: 12,
          country: 'Netherlands',
          registrySource: 'NL_IND_PUBLIC_REGISTER',
          registryIdentifier: 'IND-34259528',
          officialRegisteredName: 'Adyen N.V.',
          status: 'RECOGNIZED_SPONSOR',
          evidenceNotes: null,
          sourceUrl: 'https://ind.nl/en/public-register-recognised-sponsors',
          verifiedAt: '2026-11-01T00:00:00Z'
        }
      ]
    },
    matchBreakdown: {
      finalScore: 89,
      matchPercentage: 89,
      deterministicScore: 86,
      aiScore: 92,
      skillScore: 94,
      experienceScore: 88,
      titleScore: 95,
      locationScore: 100,
      sponsorshipScore: 100,
      salaryScore: 82,
      matchingSkills: ['Java 21', 'Spring Boot'],
      missingSkills: ['Kafka'],
      experienceCompatibility: 'Strong Fit · 8 yrs vs 7 yrs required (Lead)',
      recommendationReasons: [
        'Matched 2 of 3 core technical skills (Java 21, Spring Boot).',
        'IND recognized sponsor verified for Skilled Migrant visa.',
        'Missing skill: Kafka.'
      ],
      aiRationale: 'Excellent fit across JVM and payments systems.',
      aiStrongMatches: [
        '8 yrs Java 21 & Spring Boot payments architecture',
        'Proven ownership of distributed clearing engines on master resume'
      ],
      aiMissingSkills: ['Apache Kafka event streaming'],
      aiExperienceGaps: ['Limited exposure to regulatory SEPA ISO-20022 auditing'],
      aiPotentialConcerns: ['Requires relocation & Highly Skilled Migrant permit processing'],
      aiImprovementSuggestions: ['Quantify settlement TPS metrics inside master resume bullets'],
      aiProvider: 'GEMINI',
      aiModelUsed: 'gemini-2.5-flash',
      aiEvaluatedAt: '2026-11-08T11:05:00Z',
      calculatedAt: '2026-11-08T11:00:00Z'
    },
    aiAnalysis: {
      analysisVersion: 2,
      extractedTitle: 'Staff Java Architect',
      extractedCompany: 'Adyen N.V.',
      extractedCountry: 'Netherlands',
      extractedLocation: 'Amsterdam, Netherlands',
      requiredExperienceYears: 7,
      requiredExperienceLevel: 'LEAD',
      requiredExperienceSummary: '7+ years designing distributed backend platforms',
      requiredSkills: ['Java 21'],
      niceToHaveSkills: ['Kafka'],
      responsibilities: ['Lead core systems'],
      benefits: ['Relocation package'],
      languageRequirements: ['English'],
      salaryMin: 95000,
      salaryMax: 125000,
      salaryCurrency: 'EUR',
      salaryPeriod: 'YEARLY',
      workArrangement: 'HYBRID',
      employmentType: 'FULL_TIME',
      sponsorshipOffered: true,
      visaSponsorshipAnalysis: 'IND sponsor verified',
      relocationAssistanceOffered: true,
      relocationAnalysis: 'Full relocation included',
      summary: 'Leadership role in Amsterdam.',
      aiProvider: 'GEMINI',
      aiModelUsed: 'gemini-2.5-flash',
      analyzedAt: '2026-11-08T09:00:00Z'
    }
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JobDetailInspectorComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(JobDetailInspectorComponent);
    fixture.componentRef.setInput('summaryJob', sampleSummary);
    fixture.componentRef.setInput('detail', sampleDetail);
    fixture.detectChanges();

    httpMock
      .match(req => req.url === '/api/v1/jobs/501/assistant/history' && req.method === 'GET')
      .forEach(req => req.flush([]));
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should render sponsorship registry records, match dimensions, AI insights, and suppress unsafe URLs', () => {
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-inspector-applied]')?.textContent).toContain('Applied');
    expect(host.querySelector('[data-inspector-sponsorship]')?.textContent).toContain(
      'IND-34259528'
    );
    expect(host.querySelector('[data-inspector-vacancy-caveat]')?.textContent).toContain(
      'does not guarantee every vacancy'
    );
    expect(host.querySelector('[data-inspector-vacancy-status]')?.textContent).toContain(
      'Sponsorship explicitly mentioned'
    );
    expect(host.querySelector('[data-inspector-match]')?.textContent).toContain('89% composite');
    expect(host.querySelector('[data-inspector-match]')?.textContent).toContain('Kafka');
    expect(host.querySelector('[data-inspector-experience-fit]')?.textContent).toContain(
      'Strong Fit · 8 yrs vs 7 yrs required (Lead)'
    );
    expect(host.querySelector('[data-inspector-reasons]')?.textContent).toContain(
      'Matched 2 of 3 core technical skills (Java 21, Spring Boot).'
    );

    const aiMatchBox = host.querySelector('[data-inspector-ai-match]');
    expect(aiMatchBox?.textContent).toContain('Overall 89%');
    expect(host.querySelector('[data-inspector-ai-score]')?.textContent).toContain('AI Semantic 92%');
    expect(aiMatchBox?.textContent).toContain('Deterministic 86%');
    expect(aiMatchBox?.textContent).toContain('GEMINI · gemini-2.5-flash');
    expect(host.querySelector('[data-inspector-ai-rationale]')?.textContent).toContain(
      'Excellent fit across JVM and payments systems.'
    );
    expect(host.querySelector('[data-inspector-ai-strong-matches]')?.textContent).toContain(
      '8 yrs Java 21 & Spring Boot payments architecture'
    );
    expect(host.querySelector('[data-inspector-ai-missing-skills]')?.textContent).toContain(
      'Apache Kafka event streaming'
    );
    expect(host.querySelector('[data-inspector-ai-experience-gaps]')?.textContent).toContain(
      'SEPA ISO-20022 auditing'
    );
    expect(host.querySelector('[data-inspector-ai-concerns]')?.textContent).toContain(
      'Highly Skilled Migrant'
    );
    expect(host.querySelector('[data-inspector-ai-suggestions]')?.textContent).toContain(
      'Quantify settlement TPS metrics'
    );

    expect(host.querySelector('[data-inspector-ai]')?.textContent).toContain('GEMINI · gemini-2.5-flash');
    expect(host.querySelector('[data-inspector-ai-version]')?.textContent).toContain('v2');
    expect(host.querySelector('[data-inspector-ai-facts]')?.textContent).toContain('Staff Java Architect');
    expect(host.querySelector('[data-inspector-ai-sponsorship-flag]')?.textContent).toContain(
      'Visa Sponsorship Offered'
    );
    expect(host.querySelector('[data-inspector-ai-relocation-flag]')?.textContent).toContain(
      'Relocation Assistance'
    );
    expect(host.querySelector('.full-description')?.textContent).toContain(
      'Architect low-latency payment processing APIs'
    );

    const calcSpy = jasmine.createSpy('calculateMatch');
    const aiMatchSpy = jasmine.createSpy('evaluateAiMatch');
    const analyzeSpy = jasmine.createSpy('analyzeJd');
    const historySpy = jasmine.createSpy('toggleHistory');
    fixture.componentInstance.calculateMatchRequested.subscribe(calcSpy);
    fixture.componentInstance.evaluateAiMatchRequested.subscribe(aiMatchSpy);
    fixture.componentInstance.analyzeJdRequested.subscribe(analyzeSpy);
    fixture.componentInstance.toggleAiHistoryRequested.subscribe(historySpy);

    host.querySelector<HTMLButtonElement>('[data-inspector-action="recalculate-match"]')?.click();
    host.querySelector<HTMLButtonElement>('[data-inspector-action="evaluate-ai-match"]')?.click();
    host.querySelector<HTMLButtonElement>('[data-inspector-action="reanalyze-jd"]')?.click();
    host.querySelector<HTMLButtonElement>('[data-inspector-action="toggle-ai-history"]')?.click();
    expect(calcSpy).toHaveBeenCalledTimes(1);
    expect(aiMatchSpy).toHaveBeenCalledTimes(1);
    expect(analyzeSpy).toHaveBeenCalledTimes(1);
    expect(historySpy).toHaveBeenCalledTimes(1);

    // Unsafe javascript: originalUrl must not render as an anchor link
    const anchors = Array.from(host.querySelectorAll('a[href]')).map(a => a.getAttribute('href'));
    expect(anchors.some(href => href?.startsWith('javascript:'))).toBeFalse();
    expect(anchors).toContain('https://ind.nl/en/public-register-recognised-sponsors');
    expect(host.querySelector('[data-inspector-company-link]')?.getAttribute('href')).toContain(
      '/companies?query=Adyen%20N.V.'
    );
    expect(host.querySelector('[data-inspector-action="tailor-resume"]')?.getAttribute('href')).toContain(
      '/resume-builder?jobId=501'
    );
    expect(host.querySelector('[data-inspector-action="cover-letter"]')?.getAttribute('href')).toContain(
      '/cover-letter?jobId=501'
    );
    expect(
      host.querySelector('[data-inspector-action="track-application"]')?.getAttribute('href')
    ).toContain('/applications?jobId=501');
    expect(
      host.querySelector('[data-inspector-action="interview-prep"]')?.getAttribute('href')
    ).toContain('/interview-prep?jobId=501');
  });

  it('should emit action and retry events cleanly and show unscored profile hint when matchBreakdown is null', () => {
    const retrySpy = jasmine.createSpy('retry');
    const applySpy = jasmine.createSpy('apply');
    const calcSpy = jasmine.createSpy('calculateMatch');
    const aiMatchSpy = jasmine.createSpy('evaluateAiMatch');
    fixture.componentInstance.retryDetail.subscribe(retrySpy);
    fixture.componentInstance.applyRequested.subscribe(applySpy);
    fixture.componentInstance.calculateMatchRequested.subscribe(calcSpy);
    fixture.componentInstance.evaluateAiMatchRequested.subscribe(aiMatchSpy);

    fixture.componentRef.setInput('detail', { ...sampleDetail, matchBreakdown: null });
    fixture.componentRef.setInput('errorMessage', 'Timed out fetching detail');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Candidate Profile');
    expect(host.querySelector('[data-inspector-error]')?.textContent).toContain(
      'Timed out fetching detail'
    );
    expect(host.querySelector('[data-inspector-ai-match-empty]')?.textContent).toContain(
      'Run AI Semantic Match'
    );

    host.querySelector<HTMLButtonElement>('[data-inspector-action="retry"]')?.click();
    host.querySelector<HTMLButtonElement>('[data-inspector-action="apply"]')?.click();
    host.querySelector<HTMLButtonElement>('[data-inspector-action="calculate-match"]')?.click();
    host.querySelector<HTMLButtonElement>('[data-inspector-action="evaluate-ai-match"]')?.click();
    expect(retrySpy).toHaveBeenCalledTimes(1);
    expect(applySpy).toHaveBeenCalledTimes(1);
    expect(calcSpy).toHaveBeenCalledTimes(1);
    expect(aiMatchSpy).toHaveBeenCalledTimes(1);

    fixture.componentRef.setInput('matchBusy', true);
    fixture.componentRef.setInput('aiMatchBusy', true);
    fixture.detectChanges();
    const calcBtn = host.querySelector<HTMLButtonElement>('[data-inspector-action="calculate-match"]');
    expect(calcBtn?.disabled).toBeTrue();
    expect(calcBtn?.textContent).toContain('Calculating...');
    const aiMatchBtn = host.querySelector<HTMLButtonElement>(
      '[data-inspector-action="evaluate-ai-match"]'
    );
    expect(aiMatchBtn?.disabled).toBeTrue();
    expect(aiMatchBtn?.textContent).toContain('Evaluating...');

    fixture.componentRef.setInput('detail', { ...sampleDetail, aiAnalysis: null });
    fixture.detectChanges();
    const analyzeEmptySpy = jasmine.createSpy('analyzeEmpty');
    fixture.componentInstance.analyzeJdRequested.subscribe(analyzeEmptySpy);
    host.querySelector<HTMLButtonElement>('[data-inspector-action="analyze-jd"]')?.click();
    expect(analyzeEmptySpy).toHaveBeenCalledTimes(1);

    fixture.componentRef.setInput('detail', sampleDetail);
    fixture.componentRef.setInput('aiHistoryOpen', true);
    fixture.componentRef.setInput('aiAnalysisHistory', [
      { ...sampleDetail.aiAnalysis!, analysisVersion: 2 },
      { ...sampleDetail.aiAnalysis!, analysisVersion: 1, aiProvider: 'OPENAI', aiModelUsed: 'gpt-4o-mini' }
    ]);
    fixture.detectChanges();
    expect(host.querySelector('[data-inspector-ai-history]')?.textContent).toContain('v2');
    expect(host.querySelector('[data-inspector-ai-history]')?.textContent).toContain('gpt-4o-mini');
  });
});

