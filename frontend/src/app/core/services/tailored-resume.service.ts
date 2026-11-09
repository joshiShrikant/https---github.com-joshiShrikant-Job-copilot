import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, from, map, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api.models';
import {
  GenerateTailoredResumeRequest,
  ResumeExportFormat,
  ResumeTemplate,
  TailoredResumeExportFile,
  TailoredResumeResponse,
  TailoredResumeSummaryDto,
  UpdateTailoredResumeRequest
} from '../models/tailored-resume.models';

@Injectable({
  providedIn: 'root'
})
export class TailoredResumeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/tailored-documents`;

  private readonly _resumes = signal<readonly TailoredResumeSummaryDto[]>([]);
  private readonly _selectedResume = signal<TailoredResumeResponse | null>(null);
  private readonly _isLoadingList = signal<boolean>(false);
  private readonly _isLoadingDetail = signal<boolean>(false);
  private readonly _isGenerating = signal<boolean>(false);
  private readonly _isSaving = signal<boolean>(false);
  private readonly _exportingFormat = signal<ResumeExportFormat | null>(null);
  private readonly _deletingUuid = signal<string | null>(null);
  private readonly _lastFilterJobId = signal<number | null>(null);

  readonly resumes = this._resumes.asReadonly();
  readonly selectedResume = this._selectedResume.asReadonly();
  readonly isLoadingList = this._isLoadingList.asReadonly();
  readonly isLoadingDetail = this._isLoadingDetail.asReadonly();
  readonly isGenerating = this._isGenerating.asReadonly();
  readonly isSaving = this._isSaving.asReadonly();
  readonly exportingFormat = this._exportingFormat.asReadonly();
  readonly isExporting = computed(() => this._exportingFormat() !== null);
  readonly deletingUuid = this._deletingUuid.asReadonly();

  loadTailoredResumes(jobId?: number | null): Observable<TailoredResumeSummaryDto[]> {
    let params = new HttpParams();
    if (jobId !== null && jobId !== undefined && jobId > 0) {
      params = params.set('jobId', String(jobId));
      this._lastFilterJobId.set(jobId);
    } else {
      this._lastFilterJobId.set(null);
    }

    this._isLoadingList.set(true);
    return this.http.get<TailoredResumeSummaryDto[]>(`${this.baseUrl}/resumes`, { params }).pipe(
      tap(list => this._resumes.set(list)),
      finalize(() => this._isLoadingList.set(false))
    );
  }

  getTailoredResume(uuid: string): Observable<TailoredResumeResponse> {
    this._isLoadingDetail.set(true);
    return this.http.get<TailoredResumeResponse>(`${this.baseUrl}/resume/${uuid}`).pipe(
      tap(detail => {
        this._selectedResume.set(detail);
        this.upsertSummaryFromResponse(detail);
      }),
      finalize(() => this._isLoadingDetail.set(false))
    );
  }

  generateTailoredResume(request: GenerateTailoredResumeRequest): Observable<TailoredResumeResponse> {
    this._isGenerating.set(true);
    return this.http
      .post<TailoredResumeResponse>(`${this.baseUrl}/resume/generate`, request)
      .pipe(
        tap(generated => {
          this._selectedResume.set(generated);
          const filterJobId = this._lastFilterJobId();
          if (filterJobId === null || filterJobId === generated.jobId) {
            this.upsertSummaryFromResponse(generated, true);
          }
        }),
        finalize(() => this._isGenerating.set(false))
      );
  }

  updateTailoredResume(
    uuid: string,
    request: UpdateTailoredResumeRequest
  ): Observable<TailoredResumeResponse> {
    this._isSaving.set(true);
    return this.http
      .put<TailoredResumeResponse>(`${this.baseUrl}/resume/${uuid}`, request)
      .pipe(
        tap(updated => {
          this._selectedResume.set(updated);
          this.upsertSummaryFromResponse(updated);
        }),
        finalize(() => this._isSaving.set(false))
      );
  }

  deleteTailoredResume(uuid: string): Observable<ApiResponse<void>> {
    this._deletingUuid.set(uuid);
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/resume/${uuid}`).pipe(
      tap(() => {
        this._resumes.update(items => items.filter(item => item.uuid !== uuid));
        if (this._selectedResume()?.uuid === uuid) {
          this._selectedResume.set(null);
        }
      }),
      finalize(() => {
        if (this._deletingUuid() === uuid) {
          this._deletingUuid.set(null);
        }
      })
    );
  }

  exportTailoredResume(
    uuid: string,
    format: ResumeExportFormat,
    templateOverride?: ResumeTemplate | null,
    regenerate = false,
    fallbackFileName?: string
  ): Observable<TailoredResumeExportFile> {
    let params = new HttpParams().set('format', format);
    if (templateOverride) {
      params = params.set('template', templateOverride);
    }
    if (regenerate) {
      params = params.set('regenerate', 'true');
    }

    this._exportingFormat.set(format);
    return this.http
      .get(`${this.baseUrl}/resume/${uuid}/export`, {
        params,
        responseType: 'blob',
        observe: 'response'
      })
      .pipe(
        tap(() => this.markExportCachedIfSavedTemplate(uuid, format, templateOverride)),
        map((res: HttpResponse<Blob>) => ({
          blob: res.body ?? new Blob(),
          fileName:
            this.extractFilenameFromHeader(res.headers.get('content-disposition')) ??
            fallbackFileName ??
            `tailored-resume-${uuid}.${format}`,
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

  clearSelectedResume(): void {
    this._selectedResume.set(null);
  }

  private markExportCachedIfSavedTemplate(
    uuid: string,
    format: ResumeExportFormat,
    templateOverride?: ResumeTemplate | null
  ): void {
    this._selectedResume.update(cur => {
      if (!cur || cur.uuid !== uuid) {
        return cur;
      }
      if (templateOverride && templateOverride !== cur.templateName) {
        return cur;
      }
      return {
        ...cur,
        hasPdfExport: format === 'pdf' ? true : cur.hasPdfExport,
        hasDocxExport: format === 'docx' ? true : cur.hasDocxExport
      };
    });

    this._resumes.update(items =>
      items.map(item => {
        if (item.uuid !== uuid) {
          return item;
        }
        if (templateOverride && templateOverride !== item.templateName) {
          return item;
        }
        return {
          ...item,
          hasPdfExport: format === 'pdf' ? true : item.hasPdfExport,
          hasDocxExport: format === 'docx' ? true : item.hasDocxExport
        };
      })
    );
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
    response: TailoredResumeResponse,
    prependIfNew = false
  ): void {
    const summary: TailoredResumeSummaryDto = {
      uuid: response.uuid,
      masterResumeUuid: response.masterResumeUuid,
      masterResumeTitle: response.masterResumeTitle,
      jobId: response.jobId,
      jobTitle: response.jobTitle,
      companyName: response.companyName,
      jobCountry: response.jobCountry,
      title: response.title,
      versionNumber: response.versionNumber,
      templateName: response.templateName,
      matchScore: response.matchScore,
      atsKeywordCoverage: response.atsKeywordCoverage,
      aiProvider: response.aiProvider,
      aiModelUsed: response.aiModelUsed,
      editedByUser: response.editedByUser,
      hasPdfExport: response.hasPdfExport,
      hasDocxExport: response.hasDocxExport,
      generatedAt: response.generatedAt,
      createdAt: response.createdAt,
      updatedAt: response.updatedAt
    };

    this._resumes.update(items => {
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

