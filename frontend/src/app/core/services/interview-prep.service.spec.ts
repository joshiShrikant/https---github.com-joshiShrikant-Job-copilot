import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { InterviewPrepService } from './interview-prep.service';

describe('InterviewPrepService', () => {
  let service: InterviewPrepService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(InterviewPrepService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create, list, fetch, grade, inspect history, and delete interview prep sessions', () => {
    service
      .createSession({ jobId: 101, categories: ['ANGULAR', 'JAVA'], difficulty: 'SENIOR', questionCount: 5 })
      .subscribe();
    const createReq = httpMock.expectOne('/api/v1/interview-prep/sessions');
    expect(createReq.request.method).toBe('POST');
    createReq.flush({ id: 7, jobId: 101 });

    service.listSessions(101).subscribe();
    const listReq = httpMock.expectOne(r => r.url === '/api/v1/interview-prep/sessions');
    expect(listReq.request.params.get('jobId')).toBe('101');
    listReq.flush([]);

    service.getSession(7).subscribe();
    httpMock.expectOne('/api/v1/interview-prep/sessions/7').flush({ id: 7 });

    service.submitAnswer(7, { questionId: 12, userAnswer: 'Use Signals for sync UI state' }).subscribe();
    const ansReq = httpMock.expectOne('/api/v1/interview-prep/sessions/7/answers');
    expect(ansReq.request.method).toBe('POST');
    ansReq.flush({ id: 7, answeredQuestions: 1 });

    service.getHistory().subscribe();
    httpMock.expectOne('/api/v1/interview-prep/history').flush({ totalSessions: 1 });

    service.deleteSession(7).subscribe();
    const delReq = httpMock.expectOne('/api/v1/interview-prep/sessions/7');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush({ success: true, message: 'Deleted' });
  });
});

