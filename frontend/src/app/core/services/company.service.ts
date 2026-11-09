import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CompanyDetail,
  CompanyJobsPageResponse,
  CompanySaveResponse,
  CompanySearchFilters,
  PagedCompaniesResponse,
  SaveCompanyRequest
} from '../models/company.models';
import { JobSortOption } from '../models/job.models';

@Injectable({
  providedIn: 'root'
})
export class CompanyService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/companies`;

  private readonly _searchResult = signal<PagedCompaniesResponse | null>(null);
  private readonly _selectedCompany = signal<CompanyDetail | null>(null);
  private readonly _companyJobsPage = signal<CompanyJobsPageResponse | null>(null);

  private readonly _activeSearchRequests = signal<number>(0);
  private readonly _activeDetailRequests = signal<number>(0);
  private readonly _activeJobsRequests = signal<number>(0);
  private readonly _pendingSaveCompanyIds = signal<ReadonlySet<number>>(new Set());

  readonly searchResult = this._searchResult.asReadonly();
  readonly companies = computed(() => this._searchResult()?.content ?? []);
  readonly selectedCompany = this._selectedCompany.asReadonly();
  readonly companyJobsPage = this._companyJobsPage.asReadonly();

  readonly isSearching = computed(() => this._activeSearchRequests() > 0);
  readonly isLoadingDetail = computed(() => this._activeDetailRequests() > 0);
  readonly isLoadingJobs = computed(() => this._activeJobsRequests() > 0);
  readonly pendingSaveCompanyIds = this._pendingSaveCompanyIds.asReadonly();

  searchCompanies(
    filters?: Partial<CompanySearchFilters>,
    page = 0,
    size = environment.defaultPageSize
  ): Observable<PagedCompaniesResponse> {
    let params = new HttpParams()
      .set('page', String(Math.max(0, page)))
      .set('size', String(Math.max(1, Math.min(100, size))));

    if (filters?.query?.trim()) {
      params = params.set('query', filters.query.trim());
    }
    if (filters?.country?.trim()) {
      params = params.set('country', filters.country.trim());
    }
    if (filters?.industry?.trim()) {
      params = params.set('industry', filters.industry.trim());
    }
    if (filters?.tech?.trim()) {
      params = params.set('tech', filters.tech.trim());
    }
    if (filters?.verifiedSponsorOnly) {
      params = params.set('verifiedSponsorOnly', 'true');
    }
    if (filters?.savedOnly) {
      params = params.set('savedOnly', 'true');
    }
    if (filters?.withOpenJobsOnly) {
      params = params.set('withOpenJobsOnly', 'true');
    }
    if (filters?.sort) {
      params = params.set('sort', filters.sort);
    }

    this._activeSearchRequests.update(count => count + 1);
    return this.http.get<PagedCompaniesResponse>(this.baseUrl, { params }).pipe(
      tap(response => this._searchResult.set(response)),
      finalize(() => this._activeSearchRequests.update(count => Math.max(0, count - 1)))
    );
  }

  getCompanyDetail(companyId: number): Observable<CompanyDetail> {
    this._activeDetailRequests.update(count => count + 1);
    return this.http.get<CompanyDetail>(`${this.baseUrl}/${companyId}`).pipe(
      tap(detail => {
        this._selectedCompany.set(detail);
        this.syncDetailIntoSearchResult(detail);
      }),
      finalize(() => this._activeDetailRequests.update(count => Math.max(0, count - 1)))
    );
  }

  getCompanyJobs(
    companyId: number,
    options: {
      readonly page?: number;
      readonly size?: number;
      readonly sort?: JobSortOption;
      readonly includeHidden?: boolean;
    } = {}
  ): Observable<CompanyJobsPageResponse> {
    let params = new HttpParams()
      .set('page', String(Math.max(0, options.page ?? 0)))
      .set('size', String(Math.max(1, Math.min(100, options.size ?? 10))));

    if (options.sort) {
      params = params.set('sort', options.sort);
    }
    if (options.includeHidden) {
      params = params.set('includeHidden', 'true');
    }

    this._activeJobsRequests.update(count => count + 1);
    return this.http.get<CompanyJobsPageResponse>(`${this.baseUrl}/${companyId}/jobs`, { params }).pipe(
      tap(response => this._companyJobsPage.set(response)),
      finalize(() => this._activeJobsRequests.update(count => Math.max(0, count - 1)))
    );
  }

  saveCompany(companyId: number, request?: SaveCompanyRequest): Observable<CompanySaveResponse> {
    const url = `${this.baseUrl}/${companyId}/save`;
    return this.trackSavePending(
      companyId,
      this.http.post<CompanySaveResponse>(url, request ?? {})
    ).pipe(tap(response => this.applySaveState(response)));
  }

  unsaveCompany(companyId: number): Observable<CompanySaveResponse> {
    const url = `${this.baseUrl}/${companyId}/save`;
    return this.trackSavePending(
      companyId,
      this.http.delete<CompanySaveResponse>(url)
    ).pipe(tap(response => this.applySaveState(response)));
  }

  clearSelection(): void {
    this._selectedCompany.set(null);
    this._companyJobsPage.set(null);
  }

  isSavePending(companyId: number): boolean {
    return this._pendingSaveCompanyIds().has(companyId);
  }

  private trackSavePending<T>(companyId: number, obs$: Observable<T>): Observable<T> {
    this._pendingSaveCompanyIds.update(current => {
      const next = new Set(current);
      next.add(companyId);
      return next;
    });

    return obs$.pipe(
      finalize(() => {
        this._pendingSaveCompanyIds.update(current => {
          if (!current.has(companyId)) {
            return current;
          }
          const next = new Set(current);
          next.delete(companyId);
          return next;
        });
      })
    );
  }

  private applySaveState(response: CompanySaveResponse): void {
    const previousDetailSaved =
      this._selectedCompany()?.id === response.companyId
        ? this._selectedCompany()?.isSaved
        : undefined;
    const previousListSaved = this._searchResult()?.content.find(
      item => item.id === response.companyId
    )?.isSaved;
    const wasSaved = previousDetailSaved ?? previousListSaved;
    const savedDelta =
      wasSaved === undefined || wasSaved === response.isSaved ? 0 : response.isSaved ? 1 : -1;

    this._searchResult.update(page => {
      if (!page) {
        return page;
      }
      let touched = false;
      const content = page.content.map(item => {
        if (item.id !== response.companyId) {
          return item;
        }
        touched = true;
        return {
          ...item,
          isSaved: response.isSaved,
          savedAt: response.savedAt,
          priorityTier: response.priorityTier
        };
      });
      if (!touched && savedDelta === 0) {
        return page;
      }
      return {
        ...page,
        content,
        savedCompaniesTotal: Math.max(0, page.savedCompaniesTotal + savedDelta)
      };
    });

    this._selectedCompany.update(detail => {
      if (!detail || detail.id !== response.companyId) {
        return detail;
      }
      return {
        ...detail,
        isSaved: response.isSaved,
        savedAt: response.savedAt,
        priorityTier: response.priorityTier,
        userNotes: response.notes
      };
    });
  }

  private syncDetailIntoSearchResult(detail: CompanyDetail): void {
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
          isSaved: detail.isSaved,
          savedAt: detail.savedAt,
          priorityTier: detail.priorityTier,
          activeJobsCount: detail.activeJobsCount,
          sponsorshipJobsCount: detail.sponsorshipJobsCount,
          remoteJobsCount: detail.remoteJobsCount,
          isVerifiedSponsor: detail.isVerifiedSponsor
        };
      });
      return touched ? { ...page, content } : page;
    });
  }
}

