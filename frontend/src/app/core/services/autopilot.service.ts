import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AutopilotRunLog,
  AutopilotStatusResponse,
  PagedAutopilotHistoryResponse,
  PagedRecommendedJobsResponse,
  RecommendedJobsFilterParams,
  RunAutopilotRequest
} from '../models/autopilot.models';

@Injectable({
  providedIn: 'root'
})
export class AutopilotService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/autopilot`;

  getStatus(): Observable<AutopilotStatusResponse> {
    return this.http.get<AutopilotStatusResponse>(`${this.baseUrl}/status`);
  }

  runScan(request?: RunAutopilotRequest): Observable<AutopilotRunLog> {
    return this.http.post<AutopilotRunLog>(`${this.baseUrl}/run`, request ?? {});
  }

  pause(): Observable<AutopilotStatusResponse> {
    return this.http.post<AutopilotStatusResponse>(`${this.baseUrl}/pause`, {});
  }

  resume(): Observable<AutopilotStatusResponse> {
    return this.http.post<AutopilotStatusResponse>(`${this.baseUrl}/resume`, {});
  }

  getRecommendedJobs(
    filters?: RecommendedJobsFilterParams
  ): Observable<PagedRecommendedJobsResponse> {
    const page = Math.max(0, Math.trunc(filters?.page ?? 0));
    const size = Math.max(1, Math.min(100, Math.trunc(filters?.size ?? 20)));

    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('verifiedSponsorsOnly', String(Boolean(filters?.verifiedSponsorsOnly)))
      .set('excludeApplied', String(Boolean(filters?.excludeApplied)));

    if (
      filters?.minMatchScore !== null &&
      filters?.minMatchScore !== undefined &&
      Number.isFinite(Number(filters.minMatchScore))
    ) {
      const clamped = Math.max(0, Math.min(100, Math.trunc(Number(filters.minMatchScore))));
      params = params.set('minMatchScore', String(clamped));
    }

    return this.http.get<PagedRecommendedJobsResponse>(`${this.baseUrl}/recommended-jobs`, {
      params
    });
  }

  getHistory(page = 0, size = 10): Observable<PagedAutopilotHistoryResponse> {
    const params = new HttpParams()
      .set('page', String(Math.max(0, Math.trunc(page))))
      .set('size', String(Math.max(1, Math.min(100, Math.trunc(size)))));

    return this.http.get<PagedAutopilotHistoryResponse>(`${this.baseUrl}/history`, { params });
  }
}

