import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, finalize, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api.models';
import {
  CreateRecruiterInteractionRequest,
  CreateRecruiterRequest,
  FollowUpRemindersResponse,
  GenerateOutreachRequest,
  OutreachDraftResponse,
  PagedRecruitersResponse,
  RecruiterInteractionDto,
  RecruiterResponse,
  UpdateRecruiterInteractionRequest,
  UpdateRecruiterRequest
} from '../models/recruiter.models';

@Injectable({
  providedIn: 'root'
})
export class RecruiterService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/recruiters`;

  private readonly _directoryPage = signal<PagedRecruitersResponse | null>(null);
  private readonly _recruiters = signal<readonly RecruiterResponse[]>([]);
  private readonly _selectedRecruiter = signal<RecruiterResponse | null>(null);
  private readonly _reminders = signal<FollowUpRemindersResponse | null>(null);
  private readonly _lastOutreachDraft = signal<OutreachDraftResponse | null>(null);
  private readonly _isLoadingList = signal<boolean>(false);
  private readonly _isLoadingDetail = signal<boolean>(false);
  private readonly _isLoadingReminders = signal<boolean>(false);
  private readonly _isSavingRecruiter = signal<boolean>(false);
  private readonly _isGeneratingOutreach = signal<boolean>(false);
  private readonly _isUpdatingInteraction = signal<boolean>(false);
  private readonly _deletingRecruiterId = signal<number | null>(null);
  private readonly _deletingInteractionId = signal<number | null>(null);

  readonly directoryPage = this._directoryPage.asReadonly();
  readonly recruiters = this._recruiters.asReadonly();
  readonly selectedRecruiter = this._selectedRecruiter.asReadonly();
  readonly reminders = this._reminders.asReadonly();
  readonly lastOutreachDraft = this._lastOutreachDraft.asReadonly();
  readonly isLoadingList = this._isLoadingList.asReadonly();
  readonly isLoadingDetail = this._isLoadingDetail.asReadonly();
  readonly isLoadingReminders = this._isLoadingReminders.asReadonly();
  readonly isSavingRecruiter = this._isSavingRecruiter.asReadonly();
  readonly isGeneratingOutreach = this._isGeneratingOutreach.asReadonly();
  readonly isUpdatingInteraction = this._isUpdatingInteraction.asReadonly();
  readonly deletingRecruiterId = this._deletingRecruiterId.asReadonly();
  readonly deletingInteractionId = this._deletingInteractionId.asReadonly();

  loadRecruiters(
    query?: string,
    companyId?: number | null,
    page = 0,
    size = 20
  ): Observable<PagedRecruitersResponse> {
    let params = new HttpParams()
      .set('page', String(Math.max(0, page)))
      .set('size', String(Math.max(1, Math.min(100, size))));

    if (query?.trim()) {
      params = params.set('query', query.trim());
    }
    if (companyId !== null && companyId !== undefined && companyId > 0) {
      params = params.set('companyId', String(companyId));
    }

    this._isLoadingList.set(true);
    return this.http.get<PagedRecruitersResponse>(this.baseUrl, { params }).pipe(
      tap(response => {
        this._directoryPage.set(response);
        this._recruiters.set(response.content);
      }),
      finalize(() => this._isLoadingList.set(false))
    );
  }

  getRecruiter(id: number): Observable<RecruiterResponse> {
    this._isLoadingDetail.set(true);
    return this.http.get<RecruiterResponse>(`${this.baseUrl}/${id}`).pipe(
      tap(detail => {
        this._selectedRecruiter.set(detail);
        this.upsertRecruiterSummary(detail);
      }),
      finalize(() => this._isLoadingDetail.set(false))
    );
  }

  createRecruiter(request: CreateRecruiterRequest): Observable<RecruiterResponse> {
    this._isSavingRecruiter.set(true);
    return this.http.post<RecruiterResponse>(this.baseUrl, request).pipe(
      tap(created => {
        this._selectedRecruiter.set(created);
        this.upsertRecruiterSummary(created, true);
      }),
      finalize(() => this._isSavingRecruiter.set(false))
    );
  }

  updateRecruiter(id: number, request: UpdateRecruiterRequest): Observable<RecruiterResponse> {
    this._isSavingRecruiter.set(true);
    return this.http.put<RecruiterResponse>(`${this.baseUrl}/${id}`, request).pipe(
      tap(updated => {
        this._selectedRecruiter.set(updated);
        this.upsertRecruiterSummary(updated);
      }),
      finalize(() => this._isSavingRecruiter.set(false))
    );
  }

  deleteRecruiter(id: number): Observable<ApiResponse<void>> {
    this._deletingRecruiterId.set(id);
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/${id}`).pipe(
      tap(() => {
        this._recruiters.update(list => list.filter(item => item.id !== id));
        if (this._selectedRecruiter()?.id === id) {
          this._selectedRecruiter.set(null);
          this._lastOutreachDraft.set(null);
        }
      }),
      finalize(() => {
        if (this._deletingRecruiterId() === id) {
          this._deletingRecruiterId.set(null);
        }
      })
    );
  }

  loadReminders(includeCompleted = false): Observable<FollowUpRemindersResponse> {
    const params = new HttpParams().set('includeCompleted', includeCompleted ? 'true' : 'false');
    this._isLoadingReminders.set(true);
    return this.http
      .get<FollowUpRemindersResponse>(`${this.baseUrl}/reminders`, { params })
      .pipe(
        tap(response => this._reminders.set(response)),
        finalize(() => this._isLoadingReminders.set(false))
      );
  }

  generateOutreach(
    recruiterId: number,
    request: GenerateOutreachRequest
  ): Observable<OutreachDraftResponse> {
    this._isGeneratingOutreach.set(true);
    return this.http
      .post<OutreachDraftResponse>(`${this.baseUrl}/${recruiterId}/outreach/generate`, request)
      .pipe(
        tap(draft => {
          this._lastOutreachDraft.set(draft);
          this.upsertInteractionIntoSelectedRecruiter(draft.interaction, true);
        }),
        finalize(() => this._isGeneratingOutreach.set(false))
      );
  }

  createInteraction(
    recruiterId: number,
    request: CreateRecruiterInteractionRequest
  ): Observable<RecruiterInteractionDto> {
    this._isUpdatingInteraction.set(true);
    return this.http
      .post<RecruiterInteractionDto>(`${this.baseUrl}/${recruiterId}/interactions`, request)
      .pipe(
        tap(created => {
          this.upsertInteractionIntoSelectedRecruiter(created, true);
        }),
        finalize(() => this._isUpdatingInteraction.set(false))
      );
  }

  updateInteraction(
    recruiterId: number,
    interactionId: number,
    request: UpdateRecruiterInteractionRequest
  ): Observable<RecruiterInteractionDto> {
    this._isUpdatingInteraction.set(true);
    return this.http
      .put<RecruiterInteractionDto>(
        `${this.baseUrl}/${recruiterId}/interactions/${interactionId}`,
        request
      )
      .pipe(
        tap(updated => {
          this.upsertInteractionIntoSelectedRecruiter(updated, false);
          const currentDraft = this._lastOutreachDraft();
          if (currentDraft && currentDraft.interaction.id === updated.id) {
            this._lastOutreachDraft.set({
              ...currentDraft,
              interaction: updated
            });
          }
        }),
        finalize(() => this._isUpdatingInteraction.set(false))
      );
  }

  deleteInteraction(recruiterId: number, interactionId: number): Observable<ApiResponse<void>> {
    this._deletingInteractionId.set(interactionId);
    return this.http
      .delete<ApiResponse<void>>(`${this.baseUrl}/${recruiterId}/interactions/${interactionId}`)
      .pipe(
        tap(() => {
          const current = this._selectedRecruiter();
          if (current && current.id === recruiterId && current.interactions) {
            const nextInteractions = current.interactions.filter(i => i.id !== interactionId);
            const updated: RecruiterResponse = {
              ...current,
              interactions: nextInteractions,
              interactionsCount: Math.max(0, nextInteractions.length)
            };
            this._selectedRecruiter.set(updated);
            this.upsertRecruiterSummary(updated);
          }
          if (this._lastOutreachDraft()?.interaction.id === interactionId) {
            this._lastOutreachDraft.set(null);
          }
        }),
        finalize(() => {
          if (this._deletingInteractionId() === interactionId) {
            this._deletingInteractionId.set(null);
          }
        })
      );
  }

  clearLastOutreachDraft(): void {
    this._lastOutreachDraft.set(null);
  }

  clearSelectedRecruiter(): void {
    this._selectedRecruiter.set(null);
    this._lastOutreachDraft.set(null);
  }

  private upsertRecruiterSummary(recruiter: RecruiterResponse, prepend = false): void {
    this._recruiters.update(list => {
      const idx = list.findIndex(item => item.id === recruiter.id);
      const summaryCopy: RecruiterResponse =
        idx >= 0
          ? {
              ...recruiter,
              interactions: recruiter.interactions ?? list[idx].interactions
            }
          : recruiter;
      if (idx >= 0) {
        const next = [...list];
        next[idx] = summaryCopy;
        return next;
      }
      return prepend ? [summaryCopy, ...list] : [...list, summaryCopy];
    });
  }

  private upsertInteractionIntoSelectedRecruiter(
    interaction: RecruiterInteractionDto,
    prepend: boolean
  ): void {
    const selected = this._selectedRecruiter();
    if (!selected || selected.id !== interaction.recruiterId) {
      return;
    }
    const existing = selected.interactions ?? [];
    const idx = existing.findIndex(item => item.id === interaction.id);
    let nextInteractions: RecruiterInteractionDto[];
    if (idx >= 0) {
      nextInteractions = [...existing];
      nextInteractions[idx] = interaction;
    } else {
      nextInteractions = prepend ? [interaction, ...existing] : [...existing, interaction];
    }

    const updatedSelected: RecruiterResponse = {
      ...selected,
      interactions: nextInteractions,
      interactionsCount: nextInteractions.length,
      lastInteractionAt: interaction.sentAt ?? interaction.createdAt
    };
    this._selectedRecruiter.set(updatedSelected);
    this.upsertRecruiterSummary(updatedSelected);
  }
}

