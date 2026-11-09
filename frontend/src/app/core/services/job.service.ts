import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AnalyzeJobDescriptionRequest,
  ApplyRedirectRequest,
  ApplyRedirectResponse,
  BatchMatchRecalculationResponse,
  EvaluateAiMatchRequest,
  JobAiAnalysis,
  JobDetail,
  JobInteractionResponse,
  JobMatchBreakdown,
  JobSearchFilters,
  JobSummaryPageResponse
} from '../models/job.models';

@Injectable({
  providedIn: 'root'
})
export class JobService {
  private readonly http = inject(HttpClient);
  private readonly jobsBaseUrl = `${environment.apiUrl}/jobs`;
  private readonly matchingBaseUrl = `${environment.apiUrl}/matching`;

  private readonly _searchResult = signal<JobSummaryPageResponse | null>(null);
  private readonly _selectedJobDetail = signal<JobDetail | null>(null);
  private readonly _activeSearchRequests = signal<number>(0);
  private readonly _activeDetailRequests = signal<number>(0);
  private readonly _pendingInteractionJobIds = signal<ReadonlySet<number>>(new Set());
  private readonly _pendingMatchJobIds = signal<ReadonlySet<number>>(new Set());
  private readonly _pendingAiMatchJobIds = signal<ReadonlySet<number>>(new Set());
  private readonly _pendingAiAnalysisJobIds = signal<ReadonlySet<number>>(new Set());
  private readonly _activeBatchRecalculations = signal<number>(0);

  readonly searchResult = this._searchResult.asReadonly();
  readonly jobs = computed(() => this._searchResult()?.content ?? []);
  readonly selectedJobDetail = this._selectedJobDetail.asReadonly();
  readonly isSearching = computed(() => this._activeSearchRequests() > 0);
  readonly isLoadingDetail = computed(() => this._activeDetailRequests() > 0);
  readonly pendingInteractionJobIds = this._pendingInteractionJobIds.asReadonly();
  readonly pendingMatchJobIds = this._pendingMatchJobIds.asReadonly();
  readonly pendingAiMatchJobIds = this._pendingAiMatchJobIds.asReadonly();
  readonly pendingAiAnalysisJobIds = this._pendingAiAnalysisJobIds.asReadonly();
  readonly isRecalculatingMatches = computed(() => this._activeBatchRecalculations() > 0);

  searchJobs(
    filters?: Partial<JobSearchFilters>,
    page = 0,
    size = environment.defaultPageSize
  ): Observable<JobSummaryPageResponse> {
    let params = new HttpParams()
      .set('page', String(Math.max(0, page)))
      .set('size', String(Math.max(1, Math.min(100, size))));

    if (filters?.query?.trim()) {
      params = params.set('query', filters.query.trim());
    }
    if (filters?.country?.trim()) {
      params = params.set('country', filters.country.trim());
    }
    if (filters?.city?.trim()) {
      params = params.set('city', filters.city.trim());
    }
    if (filters?.workArrangement) {
      params = params.set('workArrangement', filters.workArrangement);
    }
    if (filters?.employmentType) {
      params = params.set('employmentType', filters.employmentType);
    }
    if (filters?.experienceLevel) {
      params = params.set('experienceLevel', filters.experienceLevel);
    }
    if (filters?.sponsorStatus) {
      params = params.set('sponsorStatus', filters.sponsorStatus);
    }
    if (
      filters?.minMatchScore !== null &&
      filters?.minMatchScore !== undefined &&
      filters.minMatchScore > 0
    ) {
      params = params.set('minMatchScore', String(filters.minMatchScore));
    }
    if (
      filters?.minSalary !== null &&
      filters?.minSalary !== undefined &&
      filters.minSalary > 0
    ) {
      params = params.set('minSalary', String(filters.minSalary));
    }
    if (filters?.savedOnly) {
      params = params.set('savedOnly', 'true');
    }
    if (filters?.interestedOnly) {
      params = params.set('interestedOnly', 'true');
    }
    if (filters?.includeHidden) {
      params = params.set('includeHidden', 'true');
    }
    if (filters?.sort) {
      params = params.set('sort', filters.sort);
    }

    this._activeSearchRequests.update(count => count + 1);
    return this.http.get<JobSummaryPageResponse>(this.jobsBaseUrl, { params }).pipe(
      tap(response => this._searchResult.set(response)),
      finalize(() => this._activeSearchRequests.update(count => Math.max(0, count - 1)))
    );
  }

  getJobDetail(jobId: number): Observable<JobDetail> {
    this._activeDetailRequests.update(count => count + 1);
    return this.http.get<JobDetail>(`${this.jobsBaseUrl}/${jobId}`).pipe(
      tap(detail => {
        this._selectedJobDetail.set(detail);
        this.syncDetailFlagsIntoSearchResult(detail);
      }),
      finalize(() => this._activeDetailRequests.update(count => Math.max(0, count - 1)))
    );
  }

  setSaved(jobId: number, save: boolean): Observable<JobInteractionResponse> {
    const url = `${this.jobsBaseUrl}/${jobId}/save`;
    const request$ = save
      ? this.http.post<JobInteractionResponse>(url, {})
      : this.http.delete<JobInteractionResponse>(url);

    return this.trackPendingInteraction(jobId, request$).pipe(
      tap(response => this.applyInteractionState(response))
    );
  }

  setInterested(jobId: number, interested: boolean): Observable<JobInteractionResponse> {
    const url = `${this.jobsBaseUrl}/${jobId}/interested`;
    const request$ = interested
      ? this.http.post<JobInteractionResponse>(url, {})
      : this.http.delete<JobInteractionResponse>(url);

    return this.trackPendingInteraction(jobId, request$).pipe(
      tap(response => this.applyInteractionState(response))
    );
  }

  setHidden(jobId: number, hidden: boolean): Observable<JobInteractionResponse> {
    const url = `${this.jobsBaseUrl}/${jobId}/hide`;
    const request$ = hidden
      ? this.http.post<JobInteractionResponse>(url, {})
      : this.http.delete<JobInteractionResponse>(url);

    return this.trackPendingInteraction(jobId, request$).pipe(
      tap(response => this.applyInteractionState(response))
    );
  }

  trackApplyRedirect(
    jobId: number,
    request?: ApplyRedirectRequest
  ): Observable<ApplyRedirectResponse> {
    const url = `${this.jobsBaseUrl}/${jobId}/apply-redirect`;
    const body: ApplyRedirectRequest = request ?? {};
    return this.trackPendingInteraction(
      jobId,
      this.http.post<ApplyRedirectResponse>(url, body)
    ).pipe(
      tap(response => {
        if (response?.interaction) {
          this.applyInteractionState(response.interaction);
        }
      })
    );
  }

  clearSelectedDetail(): void {
    this._selectedJobDetail.set(null);
  }

  isInteractionPending(jobId: number): boolean {
    return this._pendingInteractionJobIds().has(jobId);
  }

  isMatchCalculationPending(jobId: number): boolean {
    return this._pendingMatchJobIds().has(jobId);
  }

  isAiMatchPending(jobId: number): boolean {
    return this._pendingAiMatchJobIds().has(jobId);
  }

  isAiAnalysisPending(jobId: number): boolean {
    return this._pendingAiAnalysisJobIds().has(jobId);
  }

  analyzeJobDescription(jobId: number, forceRefresh = true): Observable<JobAiAnalysis> {
    this._pendingAiAnalysisJobIds.update(current => {
      const next = new Set(current);
      next.add(jobId);
      return next;
    });

    const body: AnalyzeJobDescriptionRequest = { forceRefresh };
    return this.http
      .post<JobAiAnalysis>(`${this.jobsBaseUrl}/${jobId}/intelligence/analyze-jd`, body)
      .pipe(
        tap(analysis => this.applyAnalyzedJobDescription(jobId, analysis)),
        finalize(() => {
          this._pendingAiAnalysisJobIds.update(current => {
            if (!current.has(jobId)) {
              return current;
            }
            const next = new Set(current);
            next.delete(jobId);
            return next;
          });
        })
      );
  }

  getLatestJobAiAnalysis(jobId: number): Observable<JobAiAnalysis> {
    return this.http.get<JobAiAnalysis>(`${this.jobsBaseUrl}/${jobId}/intelligence/analyze-jd`);
  }

  getJobAiAnalysisHistory(jobId: number): Observable<readonly JobAiAnalysis[]> {
    return this.http.get<readonly JobAiAnalysis[]>(
      `${this.jobsBaseUrl}/${jobId}/intelligence/analyze-jd/history`
    );
  }

  calculateJobMatch(jobId: number): Observable<JobMatchBreakdown> {
    this._pendingMatchJobIds.update(current => {
      const next = new Set(current);
      next.add(jobId);
      return next;
    });

    return this.http
      .get<JobMatchBreakdown>(`${this.jobsBaseUrl}/${jobId}/intelligence/match-score`)
      .pipe(
        tap(breakdown => this.applyCalculatedMatchBreakdown(jobId, breakdown)),
        finalize(() => {
          this._pendingMatchJobIds.update(current => {
            if (!current.has(jobId)) {
              return current;
            }
            const next = new Set(current);
            next.delete(jobId);
            return next;
          });
        })
      );
  }

  evaluateAiJobMatch(jobId: number, forceRefresh = true): Observable<JobMatchBreakdown> {
    this._pendingAiMatchJobIds.update(current => {
      const next = new Set(current);
      next.add(jobId);
      return next;
    });

    const body: EvaluateAiMatchRequest = { forceRefresh };
    return this.http
      .post<JobMatchBreakdown>(`${this.jobsBaseUrl}/${jobId}/intelligence/ai-match`, body)
      .pipe(
        tap(breakdown => this.applyCalculatedMatchBreakdown(jobId, breakdown)),
        finalize(() => {
          this._pendingAiMatchJobIds.update(current => {
            if (!current.has(jobId)) {
              return current;
            }
            const next = new Set(current);
            next.delete(jobId);
            return next;
          });
        })
      );
  }

  recalculateAllMatches(): Observable<BatchMatchRecalculationResponse> {
    this._activeBatchRecalculations.update(count => count + 1);
    return this.http
      .post<BatchMatchRecalculationResponse>(`${this.matchingBaseUrl}/recalculate`, {})
      .pipe(
        finalize(() =>
          this._activeBatchRecalculations.update(count => Math.max(0, count - 1))
        )
      );
  }

  isSafeExternalUrl(rawUrl: string | null | undefined): boolean {
    if (!rawUrl || !rawUrl.trim()) {
      return false;
    }
    try {
      const parsed = new URL(rawUrl.trim());
      return (
        (parsed.protocol === 'https:' || parsed.protocol === 'http:') &&
        parsed.hostname.trim().length > 0
      );
    } catch {
      return false;
    }
  }

  private trackPendingInteraction<T>(jobId: number, obs$: Observable<T>): Observable<T> {
    this._pendingInteractionJobIds.update(current => {
      const next = new Set(current);
      next.add(jobId);
      return next;
    });

    return obs$.pipe(
      finalize(() => {
        this._pendingInteractionJobIds.update(current => {
          if (!current.has(jobId)) {
            return current;
          }
          const next = new Set(current);
          next.delete(jobId);
          return next;
        });
      })
    );
  }

  private applyInteractionState(interaction: JobInteractionResponse): void {
    this._searchResult.update(page => {
      if (!page) {
        return page;
      }
      let touched = false;
      const content = page.content.map(item => {
        if (item.id !== interaction.jobId) {
          return item;
        }
        touched = true;
        return {
          ...item,
          isSaved: interaction.isSaved,
          isInterested: interaction.isInterested,
          isHidden: interaction.isHidden
        };
      });
      return touched ? { ...page, content } : page;
    });

    this._selectedJobDetail.update(detail => {
      if (!detail || detail.id !== interaction.jobId) {
        return detail;
      }
      return {
        ...detail,
        isSaved: interaction.isSaved,
        savedAt: interaction.savedAt,
        isInterested: interaction.isInterested,
        interestedAt: interaction.interestedAt,
        isHidden: interaction.isHidden,
        hiddenAt: interaction.hiddenAt,
        hasApplied: interaction.hasApplied,
        appliedAt: interaction.appliedAt,
        redirectClickedAt: interaction.redirectClickedAt
      };
    });
  }

  private syncDetailFlagsIntoSearchResult(detail: JobDetail): void {
    this._searchResult.update(page => {
      if (!page) {
        return page;
      }
      let touched = false;
      const content = page.content.map(item => {
        if (item.id !== detail.id) {
          return item;
        }
        touched = true;
        return {
          ...item,
          isSaved: detail.isSaved ?? item.isSaved,
          isInterested: detail.isInterested ?? item.isInterested,
          isHidden: detail.isHidden ?? item.isHidden,
          matchPercentage: detail.matchPercentage ?? item.matchPercentage
        };
      });
      return touched ? { ...page, content } : page;
    });
  }

  private applyCalculatedMatchBreakdown(jobId: number, breakdown: JobMatchBreakdown): void {
    this._selectedJobDetail.update(detail => {
      if (!detail || detail.id !== jobId) {
        return detail;
      }
      return {
        ...detail,
        matchPercentage: breakdown.matchPercentage ?? detail.matchPercentage ?? null,
        matchBreakdown: breakdown
      };
    });

    this._searchResult.update(page => {
      if (!page) {
        return page;
      }
      let touched = false;
      const content = page.content.map(item => {
        if (item.id !== jobId) {
          return item;
        }
        touched = true;
        return {
          ...item,
          matchPercentage: breakdown.matchPercentage ?? item.matchPercentage
        };
      });
      return touched ? { ...page, content } : page;
    });
  }

  private applyAnalyzedJobDescription(jobId: number, analysis: JobAiAnalysis): void {
    this._selectedJobDetail.update(detail => {
      if (!detail || detail.id !== jobId) {
        return detail;
      }
      return {
        ...detail,
        aiAnalysis: analysis
      };
    });
  }
}

