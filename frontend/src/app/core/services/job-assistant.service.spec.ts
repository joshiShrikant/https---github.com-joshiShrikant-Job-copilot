import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { JobAssistantMessageResponse } from '../models/job-assistant.models';
import { JobAssistantService } from './job-assistant.service';

describe('JobAssistantService', () => {
  let service: JobAssistantService;
  let httpMock: HttpTestingController;

  const mockTurn: JobAssistantMessageResponse = {
    id: 11,
    uuid: 'assist-uuid-11',
    jobId: 42,
    actionType: 'EXPLAIN_MATCH_SCORE',
    actionLabel: 'Explain Match Score',
    userPrompt: null,
    assistantTitle: '90% Fit Analysis for Adyen',
    assistantContent: 'Strong Java & Spring Boot overlap.',
    editableContent: 'Strong Java & Spring Boot overlap.',
    highlights: ['Java 21', 'IND Verified'],
    editedByUser: false,
    aiGenerated: true,
    aiProvider: 'GEMINI',
    aiModelUsed: 'gemini-2.5-flash',
    createdAt: '2026-11-09T09:00:00Z',
    updatedAt: '2026-11-09T09:00:00Z'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(JobAssistantService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load history, invoke assistant respond, save edits, and delete a turn', () => {
    service.getHistory(42).subscribe(turns => expect(turns.length).toBe(1));
    httpMock.expectOne('/api/v1/jobs/42/assistant/history').flush([mockTurn]);

    service.respond(42, { actionType: 'EXPLAIN_MATCH_SCORE' }).subscribe(res => {
      expect(res.id).toBe(11);
    });
    const postReq = httpMock.expectOne('/api/v1/jobs/42/assistant/respond');
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body).toEqual({ actionType: 'EXPLAIN_MATCH_SCORE' });
    postReq.flush(mockTurn);

    service.updateMessage(42, 11, { editableContent: 'Updated candidate notes' }).subscribe(res => {
      expect(res.editedByUser).toBeTrue();
    });
    const putReq = httpMock.expectOne('/api/v1/jobs/42/assistant/11');
    expect(putReq.request.method).toBe('PUT');
    putReq.flush({ ...mockTurn, editableContent: 'Updated candidate notes', editedByUser: true });

    service.deleteMessage(42, 11).subscribe(res => expect(res.success).toBeTrue());
    const delReq = httpMock.expectOne('/api/v1/jobs/42/assistant/11');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush({ success: true, message: 'Deleted' });
  });
});

