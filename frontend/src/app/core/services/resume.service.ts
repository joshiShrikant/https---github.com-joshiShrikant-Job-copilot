import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, finalize, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ALLOWED_RESUME_EXTENSIONS,
  ALLOWED_RESUME_MIME_TYPES,
  ApproveMasterResumeRequest,
  MAX_RESUME_FILE_SIZE_BYTES,
  ResumeParseResponse,
  ResumeResponse
} from '../models/resume.models';

@Injectable({
  providedIn: 'root'
})
export class ResumeService {
  private readonly http = inject(HttpClient);
  private readonly resumesBaseUrl = `${environment.apiUrl}/resumes`;

  private readonly _resumes = signal<readonly ResumeResponse[]>([]);
  private readonly _isLoading = signal<boolean>(false);

  readonly resumes = this._resumes.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();

  loadResumes(): Observable<ResumeResponse[]> {
    this._isLoading.set(true);
    return this.http.get<ResumeResponse[]>(this.resumesBaseUrl).pipe(
      tap(list => this._resumes.set(list)),
      finalize(() => this._isLoading.set(false))
    );
  }

  uploadMasterResume(file: File, title: string): Observable<ResumeResponse> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    formData.append('title', title.trim());

    return this.http
      .post<ResumeResponse>(`${this.resumesBaseUrl}/upload`, formData)
      .pipe(tap(created => this._resumes.update(items => [created, ...items])));
  }

  parseMasterResume(uuid: string, forceRefresh = true): Observable<ResumeParseResponse> {
    const params = new HttpParams().set('forceRefresh', String(forceRefresh));
    return this.http
      .post<ResumeParseResponse>(`${this.resumesBaseUrl}/${uuid}/parse`, null, { params })
      .pipe(
        tap(parseResult => {
          this._resumes.update(items =>
            items.map(item =>
              item.uuid === uuid
                ? {
                    ...item,
                    parseStatus: parseResult.parseStatus,
                    isMasterApproved: parseResult.isMasterApproved,
                    parsedAt: parseResult.parsedAt,
                    approvedAt: parseResult.approvedAt,
                    aiProvider: parseResult.aiProvider,
                    aiModelUsed: parseResult.aiModelUsed,
                    hasParsedContent: parseResult.parsedData != null
                  }
                : item
            )
          );
        })
      );
  }

  getParsedResume(uuid: string): Observable<ResumeParseResponse> {
    return this.http.get<ResumeParseResponse>(`${this.resumesBaseUrl}/${uuid}/parsed`);
  }

  approveMasterResume(request: ApproveMasterResumeRequest): Observable<ResumeResponse> {
    return this.http
      .post<ResumeResponse>(`${this.resumesBaseUrl}/master/approve`, request)
      .pipe(
        tap(approved => {
          this._resumes.update(items =>
            items.map(item => {
              if (item.uuid === approved.uuid) {
                return approved;
              }
              if (item.resumeType === 'MASTER') {
                return {
                  ...item,
                  isMasterApproved: false,
                  parseStatus: item.parseStatus === 'APPROVED' ? 'PARSED' : item.parseStatus,
                  approvedAt: null
                };
              }
              return item;
            })
          );
        })
      );
  }

  downloadResume(uuid: string, fallbackFileName?: string): Observable<{ blob: Blob; fileName: string }> {
    return this.http
      .get(`${this.resumesBaseUrl}/${uuid}/download`, {
        responseType: 'blob',
        observe: 'response'
      })
      .pipe(
        map((res: HttpResponse<Blob>) => ({
          blob: res.body ?? new Blob(),
          fileName:
            this.extractFilenameFromHeader(res.headers.get('content-disposition')) ??
            fallbackFileName ??
            `resume-${uuid}.pdf`
        }))
      );
  }

  deleteResume(uuid: string): Observable<void> {
    return this.http
      .delete<void>(`${this.resumesBaseUrl}/${uuid}`)
      .pipe(tap(() => this._resumes.update(items => items.filter(r => r.uuid !== uuid))));
  }

  validateResumeFile(file: File | null | undefined): string | null {
    if (!file) {
      return 'Please select a PDF or DOCX resume file.';
    }

    if (file.size <= 0) {
      return 'Selected resume file is empty.';
    }

    if (file.size > MAX_RESUME_FILE_SIZE_BYTES) {
      return 'Resume file exceeds the maximum 10 MB upload limit.';
    }

    const lowerName = file.name.toLowerCase();
    const hasValidExtension = ALLOWED_RESUME_EXTENSIONS.some(ext => lowerName.endsWith(ext));
    if (!hasValidExtension) {
      return 'Only .pdf and .docx resume files are supported.';
    }

    if (file.type && !ALLOWED_RESUME_MIME_TYPES.includes(file.type)) {
      return 'File MIME type must match PDF or Word (.docx) format.';
    }

    return null;
  }

  formatFileSize(bytes: number): string {
    if (!Number.isFinite(bytes) || bytes <= 0) {
      return '0 KB';
    }
    if (bytes < 1024 * 1024) {
      return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
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
}
