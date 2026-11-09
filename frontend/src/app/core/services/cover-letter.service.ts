import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, from, map, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api.models';
import {
  CoverLetterExportFile,
  CoverLetterExportFormat,
  CoverLetterResponse,
  CoverLetterSummaryDto,
  GenerateCoverLetterRequest,
  UpdateCoverLetterRequest
} from '../models/cover-letter.models';

@Injectable({
  providedIn: 'root'
})
export class CoverLetterService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/tailored-documents`;

  private readonly _coverLetters = signal<readonly CoverLetterSummaryDto[]>([]);
  private readonly _selectedCoverLetter = signal<CoverLetterResponse | null>(null);
  private readonly _isLoadingList = signal<boolean>(false);
  private readonly _isLoadingDetail = signal<boolean>(false);
  private readonly _isGenerating = signal<boolean>(false);
  private readonly _isSaving = signal<boolean>(false);
  private readonly _exportingFormat = signal<CoverLetterExportFormat | null>(null);
  private readonly _deletingUuid = signal<string | null>(null);
  private readonly _lastFilterJobId = signal<number | null>(null);

  readonly coverLetters = this._coverLetters.asReadonly();
  readonly selectedCoverLetter = this._selectedCoverLetter.asReadonly();
  readonly isLoadingList = this._isLoadingList.asReadonly();
  readonly isLoadingDetail = this._isLoadingDetail.asReadonly();
  readonly isGenerating = this._isGenerating.asReadonly();
  readonly isSaving = this._isSaving.asReadonly();
  readonly exportingFormat = this._exportingFormat.asReadonly();
  readonly isExporting = computed(() => this._exportingFormat() !== null);
  readonly deletingUuid = this._deletingUuid.asReadonly();

  loadCoverLetters(jobId?: number | null): Observable<CoverLetterSummaryDto[]> {
    let params = new HttpParams();
    if (jobId !== null && jobId !== undefined && jobId > 0) {
      params = params.set('jobId', String(jobId));
      this._lastFilterJobId.set(jobId);
    } else {
      this._lastFilterJobId.set(null);
    }

    this._isLoadingList.set(true);
    return this.http.get<CoverLetterSummaryDto[]>(`${this.baseUrl}/cover-letters`, { params }).pipe(
      tap(list => this._coverLetters.set(list)),
      finalize(() => this._isLoadingList.set(false))
    );
  }

  getCoverLetter(uuid: string): Observable<CoverLetterResponse> {
    this._isLoadingDetail.set(true);
    return this.http.get<CoverLetterResponse>(`${this.baseUrl}/cover-letter/${uuid}`).pipe(
      tap(detail => {
        this._selectedCoverLetter.set(detail);
        this.upsertSummaryFromResponse(detail);
      }),
      finalize(() => this._isLoadingDetail.set(false))
    );
  }

  generateCoverLetter(request: GenerateCoverLetterRequest): Observable<CoverLetterResponse> {
    this._isGenerating.set(true);
    return this.http
      .post<CoverLetterResponse>(`${this.baseUrl}/cover-letter/generate`, request)
      .pipe(
        tap(generated => {
          this._selectedCoverLetter.set(generated);
          const filterJobId = this._lastFilterJobId();
          if (filterJobId === null || filterJobId === generated.jobId) {
            this.upsertSummaryFromResponse(generated, true);
          }
        }),
        finalize(() => this._isGenerating.set(false))
      );
  }

  updateCoverLetter(
    uuid: string,
    request: UpdateCoverLetterRequest
  ): Observable<CoverLetterResponse> {
    this._isSaving.set(true);
    return this.http
      .put<CoverLetterResponse>(`${this.baseUrl}/cover-letter/${uuid}`, request)
      .pipe(
        tap(updated => {
          this._selectedCoverLetter.set(updated);
          this.upsertSummaryFromResponse(updated);
        }),
        finalize(() => this._isSaving.set(false))
      );
  }

  deleteCoverLetter(uuid: string): Observable<ApiResponse<void>> {
    this._deletingUuid.set(uuid);
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/cover-letter/${uuid}`).pipe(
      tap(() => {
        this._coverLetters.update(items => items.filter(item => item.uuid !== uuid));
        if (this._selectedCoverLetter()?.uuid === uuid) {
          this._selectedCoverLetter.set(null);
        }
      }),
      finalize(() => {
        if (this._deletingUuid() === uuid) {
          this._deletingUuid.set(null);
        }
      })
    );
  }

  exportCoverLetter(
    uuid: string,
    format: CoverLetterExportFormat,
    fallbackFileName?: string
  ): Observable<CoverLetterExportFile> {
    const params = new HttpParams().set('format', format);

    this._exportingFormat.set(format);
    return this.http
      .get(`${this.baseUrl}/cover-letter/${uuid}/export`, {
        params,
        responseType: 'blob',
        observe: 'response'
      })
      .pipe(
        map((res: HttpResponse<Blob>) => ({
          blob: res.body ?? new Blob(),
          fileName:
            this.extractFilenameFromHeader(res.headers.get('content-disposition')) ??
            fallbackFileName ??
            `cover-letter-${uuid}.${format}`,
          format
        })),
        catchError((err: unknown) => {
          if (err instanceof HttpErrorResponse && err.error instanceof Blob && err.error.type.includes('json')) {
            return from(err.error.text()).pipe(
              switchMap(text => {
                let parsed: unknown = text;
                try {
                  parsed = JSON.parse(text);
                } catch {
                  // Keep raw error text fallback
                }
                return throwError(
                  () =>
                    new HttpErrorResponse({
                      error: parsed,
                      headers: err.headers,
                      status: err.status,
                      statusText: err.statusText,
                      url: err.url ?? undefined
                    })
                );
              })
            );
          }
          return throwError(() => err);
        }),
        finalize(() => this._exportingFormat.set(null))
      );
  }

  clearSelectedCoverLetter(): void {
    this._selectedCoverLetter.set(null);
  }

  private extractFilenameFromHeader(header: string | null): string | null {
    if (!header) {
      return null;
    }
    const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(header);
    if (utf8Match?.[1]) {
      try {
        return decodeURIComponent(utf8Match[1].replace(/['"]/g, '').trim());
      } catch {
        return utf8Match[1].replace(/['"]/g, '').trim();
      }
    }
    const plainMatch = /filename="?([^";]+)"?/i.exec(header);
    return plainMatch?.[1]?.trim() || null;
  }

  private upsertSummaryFromResponse(
    response: CoverLetterResponse,
    prependIfNew = false
  ): void {
    const summary: CoverLetterSummaryDto = {
      uuid: response.uuid,
      jobId: response.jobId,
      jobTitle: response.jobTitle,
      companyName: response.companyName,
      jobCountry: response.jobCountry,
      tailoredResumeUuid: response.tailoredResumeUuid,
      tailoredResumeTitle: response.tailoredResumeTitle,
      title: response.title,
      tone: response.tone,
      versionNumber: response.versionNumber,
      wordCount: response.wordCount,
      aiProvider: response.aiProvider,
      aiModelUsed: response.aiModelUsed,
      editedByUser: response.editedByUser,
      generatedAt: response.generatedAt,
      createdAt: response.createdAt,
      updatedAt: response.updatedAt
    };

    this._coverLetters.update(items => {
      const existingIdx = items.findIndex(item => item.uuid === summary.uuid);
      if (existingIdx >= 0) {
        const next = [...items];
        next[existingIdx] = summary;
        return next;
      }
      return prependIfNew ? [summary, ...items] : [...items, summary];
    });
  }
}

