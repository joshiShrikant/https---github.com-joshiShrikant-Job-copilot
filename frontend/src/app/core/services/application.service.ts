import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api.models';
import {
  ApplicationFilters,
  ApplicationStatus,
  CreateApplicationRequest,
  JobApplicationDetailResponse,
  UpdateApplicationRequest,
  UpdateApplicationStatusRequest
} from '../models/application.models';

@Injectable({
  providedIn: 'root'
})
export class ApplicationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/applications`;

  private readonly _applications = signal<readonly JobApplicationDetailResponse[]>([]);
  private readonly _selectedUuid = signal<string | null>(null);
  private readonly _isLoading = signal<boolean>(false);
  private readonly _isCreating = signal<boolean>(false);
  private readonly _updatingUuid = signal<string | null>(null);
  private readonly _transitioningUuid = signal<string | null>(null);
  private readonly _deletingUuid = signal<string | null>(null);

  readonly applications = this._applications.asReadonly();
  readonly selectedUuid = this._selectedUuid.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();
  readonly isCreating = this._isCreating.asReadonly();
  readonly updatingUuid = this._updatingUuid.asReadonly();
  readonly transitioningUuid = this._transitioningUuid.asReadonly();
  readonly deletingUuid = this._deletingUuid.asReadonly();

  readonly selectedApplication = computed<JobApplicationDetailResponse | null>(() => {
    const uuid = this._selectedUuid();
    if (!uuid) {
      return null;
    }
    return this._applications().find(app => app.uuid === uuid) ?? null;
  });

  readonly pipelineStats = computed(() => {
    const items = this._applications();
    const interviewingStages = new Set<ApplicationStatus>([
      'ASSESSMENT',
      'TECH_INTERVIEW',
      'HR_INTERVIEW',
      'FINAL_INTERVIEW'
    ]);
    let activeInterviews = 0;
    let scheduledInterviews = 0;
    let overdueFollowUps = 0;
    let pendingFollowUps = 0;
    let offersCount = 0;

    for (const app of items) {
      if (interviewingStages.has(app.status)) {
        activeInterviews++;
      }
      if (app.nextInterviewDate) {
        scheduledInterviews++;
      }
      if (app.overdueFollowUp) {
        overdueFollowUps++;
      }
      if (app.nextFollowUpDate && !app.followUpCompleted) {
        pendingFollowUps++;
      }
      if (app.status === 'OFFER') {
        offersCount++;
      }
    }

    return {
      totalCount: items.length,
      activeInterviews,
      scheduledInterviews,
      overdueFollowUps,
      pendingFollowUps,
      offersCount
    };
  });

  loadApplications(filters?: Partial<ApplicationFilters>): Observable<JobApplicationDetailResponse[]> {
    let params = new HttpParams();
    if (filters?.status) {
      params = params.set('status', filters.status);
    }
    if (filters?.query?.trim()) {
      params = params.set('query', filters.query.trim());
    }
    if (filters?.dueFollowUpOnly) {
      params = params.set('dueFollowUpOnly', 'true');
    }
    if (filters?.hasInterviewOnly) {
      params = params.set('hasInterviewOnly', 'true');
    }

    this._isLoading.set(true);
    return this.http.get<JobApplicationDetailResponse[]>(this.baseUrl, { params }).pipe(
      tap(items => this._applications.set(items)),
      finalize(() => this._isLoading.set(false))
    );
  }

  getApplication(uuid: string): Observable<JobApplicationDetailResponse> {
    this._isLoading.set(true);
    return this.http.get<JobApplicationDetailResponse>(`${this.baseUrl}/${uuid}`).pipe(
      tap(detail => {
        this.upsertApplication(detail);
        this._selectedUuid.set(detail.uuid);
      }),
      finalize(() => this._isLoading.set(false))
    );
  }

  createApplication(request: CreateApplicationRequest): Observable<JobApplicationDetailResponse> {
    this._isCreating.set(true);
    return this.http.post<JobApplicationDetailResponse>(this.baseUrl, request).pipe(
      tap(created => {
        this.upsertApplication(created, true);
        this._selectedUuid.set(created.uuid);
      }),
      finalize(() => this._isCreating.set(false))
    );
  }

  updateApplication(
    uuid: string,
    request: UpdateApplicationRequest
  ): Observable<JobApplicationDetailResponse> {
    this._updatingUuid.set(uuid);
    return this.http.put<JobApplicationDetailResponse>(`${this.baseUrl}/${uuid}`, request).pipe(
      tap(updated => this.upsertApplication(updated)),
      finalize(() => {
        if (this._updatingUuid() === uuid) {
          this._updatingUuid.set(null);
        }
      })
    );
  }

  updateApplicationStatus(
    uuid: string,
    request: UpdateApplicationStatusRequest
  ): Observable<JobApplicationDetailResponse> {
    this._transitioningUuid.set(uuid);
    return this.http
      .patch<JobApplicationDetailResponse>(`${this.baseUrl}/${uuid}/status`, request)
      .pipe(
        tap(updated => this.upsertApplication(updated)),
        finalize(() => {
          if (this._transitioningUuid() === uuid) {
            this._transitioningUuid.set(null);
          }
        })
      );
  }

  deleteApplication(uuid: string): Observable<ApiResponse<void>> {
    this._deletingUuid.set(uuid);
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/${uuid}`).pipe(
      tap(() => {
        this._applications.update(items => items.filter(item => item.uuid !== uuid));
        if (this._selectedUuid() === uuid) {
          this._selectedUuid.set(null);
        }
      }),
      finalize(() => {
        if (this._deletingUuid() === uuid) {
          this._deletingUuid.set(null);
        }
      })
    );
  }

  selectApplication(uuid: string | null): void {
    this._selectedUuid.set(uuid);
  }

  private upsertApplication(item: JobApplicationDetailResponse, prepend = false): void {
    this._applications.update(list => {
      const idx = list.findIndex(existing => existing.uuid === item.uuid);
      if (idx >= 0) {
        const next = [...list];
        next[idx] = item;
        return next;
      }
      return prepend ? [item, ...list] : [...list, item];
    });
  }
}

