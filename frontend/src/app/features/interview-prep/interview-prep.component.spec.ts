import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import {
  InterviewPerformanceHistoryResponse,
  InterviewPrepSessionDetail,
  InterviewPrepSessionSummary
} from '../../core/models/interview-prep.models';
import { JobSummaryPageResponse } from '../../core/models/job.models';
import { InterviewPrepComponent } from './interview-prep.component';

describe('InterviewPrepComponent', () => {
  let fixture: ComponentFixture<InterviewPrepComponent>;
  let httpMock: HttpTestingController;
  let queryParamSubject: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  const jobsPage: JobSummaryPageResponse = {
    content: [
      {
        id: 101,
        title: 'Senior Angular & Java Full-Stack Engineer',
        companyName: 'Adyen N.V.',
        location: 'Amsterdam, Netherlands',
        city: 'Amsterdam',
        country: 'Netherlands',
        workArrangement: 'HYBRID',
        salaryMin: 95000,
        salaryMax: 125000,
        salaryCurrency: 'EUR',
        salaryPeriod: 'YEARLY',
        sponsorStatus: 'VERIFIED_SPONSOR',
        matchPercentage: 91,
        sourceProvider: 'ARBEITNOW',
        postedAt: '2026-11-08T10:00:00Z',
        isSaved: true,
        isInterested: true
      }
    ],
    pageNumber: 0,
    pageSize: 50,
    totalElements: 1,
    totalPages: 1,
    isFirst: true,
    isLast: true,
    hasNext: false,
    hasPrevious: false
  };

  const sessionDetail: InterviewPrepSessionDetail = {
    id: 12,
    jobId: 101,
    jobTitle: 'Senior Angular & Java Full-Stack Engineer',
    companyName: 'Adyen N.V.',
    country: 'Netherlands',
    sessionTitle: 'Adyen Round 1 Technical & System Design',
    targetCategories: ['ANGULAR', 'JAVA', 'SYSTEM_DESIGN'],
    targetDifficulty: 'SENIOR',
    totalQuestions: 2,
    answeredQuestions: 0,
    score: null,
    overallFeedback: null,
    companyPrepTopics: [
      {
        topic: 'Idempotent Payment Settlement & Angular Reactive Dashboards',
        whyRelevant: 'Explicitly highlighted in the Adyen Amsterdam role description.',
        groundingBasis: 'Job description & verified NL IND sponsor profile'
      }
    ],
    improvementRecommendations: [],
    aiProvider: 'GEMINI',
    aiModelUsed: 'gemini-2.5-flash',
    questions: [
      {
        id: 501,
        orderIndex: 0,
        category: 'ANGULAR',
        difficulty: 'SENIOR',
        questionText: 'How would you structure OnPush change detection with Signals in a live dashboard?',
        idealAnswer: 'Combine signal inputs, computed derived state, and RxJS interop with takeUntilDestroyed.',
        focusTags: ['Signals', 'OnPush', 'RxJS'],
        userAnswer: null,
        aiFeedback: null,
        score: null,
        strongPoints: [],
        missingElements: [],
        answeredAt: null,
        evaluationProvider: null,
        evaluationModelUsed: null
      },
      {
        id: 502,
        orderIndex: 1,
        category: 'JAVA',
        difficulty: 'SENIOR',
        questionText: 'How do you enforce idempotency across Spring Boot payment retries?',
        idealAnswer: 'Use unique client idempotency keys backed by a DB unique constraint and transactional outbox.',
        focusTags: ['Idempotency', 'Spring Boot'],
        userAnswer: null,
        aiFeedback: null,
        score: null,
        strongPoints: [],
        missingElements: [],
        answeredAt: null,
        evaluationProvider: null,
        evaluationModelUsed: null
      }
    ],
    createdAt: '2026-11-09T10:00:00Z',
    updatedAt: '2026-11-09T10:00:00Z'
  };

  const sessionSummary: InterviewPrepSessionSummary = {
    id: 12,
    jobId: 101,
    jobTitle: sessionDetail.jobTitle,
    companyName: sessionDetail.companyName,
    country: sessionDetail.country,
    sessionTitle: sessionDetail.sessionTitle,
    targetCategories: sessionDetail.targetCategories,
    targetDifficulty: 'SENIOR',
    totalQuestions: 2,
    answeredQuestions: 0,
    score: null,
    createdAt: sessionDetail.createdAt,
    updatedAt: sessionDetail.updatedAt
  };

  const emptyHistory: InterviewPerformanceHistoryResponse = {
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
  };

  beforeEach(async () => {
    queryParamSubject = new BehaviorSubject(convertToParamMap({}));
    await TestBed.configureTestingModule({
      imports: [InterviewPrepComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: queryParamSubject.asObservable() }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(InterviewPrepComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialEmptyWorkspace(): void {
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/jobs?page=0&size=50').flush(jobsPage);
    httpMock.expectOne('/api/v1/interview-prep/sessions').flush([]);
    httpMock.expectOne('/api/v1/interview-prep/history').flush(emptyHistory);
    fixture.detectChanges();
  }

  it('should render all 12 categories, generate AI session, reveal ideal answer, submit practice answer, and delete session', () => {
    flushInitialEmptyWorkspace();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('[data-prep-cat]').length).toBe(12);
    expect(host.querySelector('[data-prep-no-sessions]')).toBeTruthy();

    // Generate session
    host.querySelector<HTMLButtonElement>('[data-prep-generate-btn]')?.click();
    const createReq = httpMock.expectOne('/api/v1/interview-prep/sessions');
    expect(createReq.request.method).toBe('POST');
    expect(createReq.request.body.jobId).toBe(101);
    createReq.flush(sessionDetail);

    httpMock.expectOne('/api/v1/interview-prep/sessions').flush([sessionSummary]);
    httpMock.expectOne('/api/v1/interview-prep/history').flush({
      ...emptyHistory,
      totalSessions: 1,
      totalQuestions: 2,
      sessions: [sessionSummary]
    });
    fixture.detectChanges();

    expect(host.querySelector('[data-prep-workspace]')).toBeTruthy();
    expect(host.querySelector('[data-prep-grounding]')?.textContent).toContain(
      'Job description & verified NL IND sponsor profile'
    );

    // Reveal ideal answer on Q501
    host.querySelector<HTMLButtonElement>('[data-prep-toggle-ideal="501"]')?.click();
    fixture.detectChanges();
    expect(host.querySelector('[data-prep-ideal="501"]')?.textContent).toContain(
      'Combine signal inputs'
    );

    // Submit answer for AI grading
    const draftBox = host.querySelector<HTMLTextAreaElement>('[data-prep-answer-input="501"]')!;
    draftBox.value = 'I use input() signals, computed(), and takeUntilDestroyed with OnPush.';
    draftBox.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('[data-prep-submit-answer="501"]')?.click();
    const answerReq = httpMock.expectOne('/api/v1/interview-prep/sessions/12/answers');
    expect(answerReq.request.method).toBe('POST');
    expect(answerReq.request.body).toEqual({
      questionId: 501,
      userAnswer: 'I use input() signals, computed(), and takeUntilDestroyed with OnPush.'
    });

    const gradedDetail: InterviewPrepSessionDetail = {
      ...sessionDetail,
      answeredQuestions: 1,
      score: 88,
      overallFeedback: 'Strong reactive architecture depth.',
      questions: [
        {
          ...sessionDetail.questions[0],
          userAnswer: draftBox.value,
          score: 88,
          aiFeedback: 'Clear explanation of Zoneless-ready Signal patterns.',
          strongPoints: ['Identified signal inputs + OnPush'],
          missingElements: ['Mention rxjs-interop toSignal cleanup nuances'],
          answeredAt: '2026-11-09T10:15:00Z',
          evaluationProvider: 'GEMINI',
          evaluationModelUsed: 'gemini-2.5-flash'
        },
        sessionDetail.questions[1]
      ]
    };
    answerReq.flush(gradedDetail);
    httpMock.expectOne('/api/v1/interview-prep/sessions').flush([
      { ...sessionSummary, answeredQuestions: 1, score: 88 }
    ]);
    httpMock.expectOne('/api/v1/interview-prep/history').flush({
      ...emptyHistory,
      totalSessions: 1,
      totalQuestions: 2,
      totalAnsweredQuestions: 1,
      averageScore: 88,
      categoryStats: [
        { category: 'ANGULAR', totalQuestions: 1, answeredQuestions: 1, averageScore: 88 }
      ],
      recentScoreTrend: [
        {
          sessionId: 12,
          sessionTitle: sessionDetail.sessionTitle,
          companyName: 'Adyen N.V.',
          score: 88,
          answeredQuestions: 1,
          totalQuestions: 2,
          createdAt: sessionDetail.createdAt
        }
      ],
      sessions: [{ ...sessionSummary, answeredQuestions: 1, score: 88 }]
    });
    fixture.detectChanges();

    expect(host.querySelector('[data-prep-question-score="501"]')?.textContent).toContain('88/100');
    expect(host.querySelector('[data-prep-session-score]')?.textContent).toContain('88 / 100');
    expect(host.querySelector('[data-prep-history-avg]')?.textContent).toContain('88');

    // Delete session via confirmation modal
    host.querySelector<HTMLButtonElement>('[data-prep-delete-session="12"]')?.click();
    fixture.detectChanges();
    httpMock.expectNone('/api/v1/interview-prep/sessions/12');

    host.querySelector<HTMLButtonElement>('[data-dialog-action="confirm"]')?.click();
    const deleteReq = httpMock.expectOne('/api/v1/interview-prep/sessions/12');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush({ success: true, message: 'Session deleted' });
    httpMock.expectOne('/api/v1/interview-prep/sessions').flush([]);
    httpMock.expectOne('/api/v1/interview-prep/history').flush(emptyHistory);
    fixture.detectChanges();

    expect(host.querySelector('[data-prep-workspace]')).toBeNull();
  });

  it('should surface real AI unavailability error without inventing fallback questions', () => {
    flushInitialEmptyWorkspace();

    const host = fixture.nativeElement as HTMLElement;
    host.querySelector<HTMLButtonElement>('[data-prep-generate-btn]')?.click();

    httpMock
      .expectOne('/api/v1/interview-prep/sessions')
      .flush(
        { message: 'Gemini API key is not configured on backend' },
        { status: 503, statusText: 'Service Unavailable' }
      );
    fixture.detectChanges();

    expect(host.querySelector('[data-prep-error]')?.textContent).toContain(
      'Gemini API key is not configured on backend'
    );
    expect(host.querySelector('[data-prep-workspace]')).toBeNull();
  });

  it('should preselect deep-linked ?jobId and load fallback detail when absent from page 0', () => {
    queryParamSubject.next(convertToParamMap({ jobId: '999' }));
    fixture.detectChanges();

    const detailReq = httpMock.expectOne('/api/v1/jobs/999');
    expect(detailReq.request.method).toBe('GET');
    detailReq.flush({
      id: 999,
      title: 'Principal Distributed Java Engineer',
      companyName: 'Booking.com',
      location: 'Amsterdam, Netherlands',
      city: 'Amsterdam',
      country: 'Netherlands',
      workArrangement: 'HYBRID',
      employmentType: 'FULL_TIME',
      experienceLevel: 'PRINCIPAL',
      salaryMin: 120000,
      salaryMax: 155000,
      salaryCurrency: 'EUR',
      salaryPeriod: 'YEARLY',
      description: 'Global travel scale payment systems.',
      skills: ['Java', 'Spring Boot'],
      sponsorStatus: 'VERIFIED_SPONSOR',
      matchPercentage: 93,
      sourceProvider: 'ARBEITNOW',
      originalUrl: 'https://careers.booking.com/999',
      postedAt: '2026-11-08T12:00:00Z'
    });

    httpMock.expectOne('/api/v1/jobs?page=0&size=50').flush(jobsPage);
    httpMock.expectOne('/api/v1/interview-prep/sessions').flush([]);
    httpMock.expectOne('/api/v1/interview-prep/history').flush(emptyHistory);
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedJobId()).toBe(999);
    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>(
      '[data-prep-job-select]'
    );
    expect(select?.textContent).toContain('Booking.com');
  });
});
