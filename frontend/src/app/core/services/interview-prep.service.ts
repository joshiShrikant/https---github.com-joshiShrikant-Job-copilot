import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api.models';
import {
  CreateInterviewPrepSessionRequest,
  InterviewPerformanceHistoryResponse,
  InterviewPrepSessionDetail,
  InterviewPrepSessionSummary,
  SubmitInterviewAnswerRequest
} from '../models/interview-prep.models';

@Injectable({
  providedIn: 'root'
})
export class InterviewPrepService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/interview-prep`;

  createSession(
    request: CreateInterviewPrepSessionRequest
  ): Observable<InterviewPrepSessionDetail> {
    return this.http.post<InterviewPrepSessionDetail>(`${this.baseUrl}/sessions`, request);
  }

  listSessions(jobId?: number | null): Observable<readonly InterviewPrepSessionSummary[]> {
    let params = new HttpParams();
    if (jobId !== null && jobId !== undefined && jobId > 0) {
      params = params.set('jobId', String(jobId));
    }
    return this.http.get<readonly InterviewPrepSessionSummary[]>(`${this.baseUrl}/sessions`, {
      params
    });
  }

  getSession(sessionId: number): Observable<InterviewPrepSessionDetail> {
    return this.http.get<InterviewPrepSessionDetail>(`${this.baseUrl}/sessions/${sessionId}`);
  }

  submitAnswer(
    sessionId: number,
    request: SubmitInterviewAnswerRequest
  ): Observable<InterviewPrepSessionDetail> {
    return this.http.post<InterviewPrepSessionDetail>(
      `${this.baseUrl}/sessions/${sessionId}/answers`,
      request
    );
  }

  deleteSession(sessionId: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/sessions/${sessionId}`);
  }

  getHistory(): Observable<InterviewPerformanceHistoryResponse> {
    return this.http.get<InterviewPerformanceHistoryResponse>(`${this.baseUrl}/history`);
  }
}

