import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api.models';
import {
  JobAssistantMessageResponse,
  JobAssistantRespondRequest,
  UpdateJobAssistantMessageRequest
} from '../models/job-assistant.models';

@Injectable({
  providedIn: 'root'
})
export class JobAssistantService {
  private readonly http = inject(HttpClient);
  private readonly jobsBaseUrl = `${environment.apiUrl}/jobs`;

  getHistory(jobId: number): Observable<readonly JobAssistantMessageResponse[]> {
    return this.http.get<readonly JobAssistantMessageResponse[]>(
      `${this.jobsBaseUrl}/${jobId}/assistant/history`
    );
  }

  respond(
    jobId: number,
    request: JobAssistantRespondRequest
  ): Observable<JobAssistantMessageResponse> {
    return this.http.post<JobAssistantMessageResponse>(
      `${this.jobsBaseUrl}/${jobId}/assistant/respond`,
      request
    );
  }

  updateMessage(
    jobId: number,
    messageId: number,
    request: UpdateJobAssistantMessageRequest
  ): Observable<JobAssistantMessageResponse> {
    return this.http.put<JobAssistantMessageResponse>(
      `${this.jobsBaseUrl}/${jobId}/assistant/${messageId}`,
      request
    );
  }

  deleteMessage(jobId: number, messageId: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(
      `${this.jobsBaseUrl}/${jobId}/assistant/${messageId}`
    );
  }
}

