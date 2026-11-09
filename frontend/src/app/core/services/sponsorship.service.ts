import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, finalize, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CountrySponsorshipProfile,
  IndSyncResult,
  PagedIndRegistryResponse,
  PagedSponsorshipHistoryResponse,
  RegistrySyncStatusInfo,
  SponsorshipVerificationResponse,
  VerifySponsorshipParams
} from '../models/sponsorship.models';

@Injectable({
  providedIn: 'root'
})
export class SponsorshipService {
  private readonly http = inject(HttpClient);

  private readonly sponsorshipUrl = `${environment.apiUrl}/sponsorship`;
  private readonly adminSponsorshipUrl = `${environment.apiUrl}/admin/sponsorship`;

  private readonly _countries = signal<readonly CountrySponsorshipProfile[]>([]);
  private readonly _lastVerification = signal<SponsorshipVerificationResponse | null>(null);
  private readonly _historyPage = signal<PagedSponsorshipHistoryResponse | null>(null);
  private readonly _indRegistryPage = signal<PagedIndRegistryResponse | null>(null);
  private readonly _adminSyncStatus = signal<RegistrySyncStatusInfo | null>(null);

  private readonly _isLoadingCountries = signal<boolean>(false);
  private readonly _isVerifying = signal<boolean>(false);
  private readonly _isLoadingHistory = signal<boolean>(false);
  private readonly _isLoadingIndRegistry = signal<boolean>(false);
  private readonly _isSyncingInd = signal<boolean>(false);

  readonly countries = this._countries.asReadonly();
  readonly lastVerification = this._lastVerification.asReadonly();
  readonly historyPage = this._historyPage.asReadonly();
  readonly indRegistryPage = this._indRegistryPage.asReadonly();
  readonly adminSyncStatus = this._adminSyncStatus.asReadonly();

  readonly isLoadingCountries = this._isLoadingCountries.asReadonly();
  readonly isVerifying = this._isVerifying.asReadonly();
  readonly isLoadingHistory = this._isLoadingHistory.asReadonly();
  readonly isLoadingIndRegistry = this._isLoadingIndRegistry.asReadonly();
  readonly isSyncingInd = this._isSyncingInd.asReadonly();

  loadCountries(): Observable<readonly CountrySponsorshipProfile[]> {
    this._isLoadingCountries.set(true);
    return this.http.get<readonly CountrySponsorshipProfile[]>(`${this.sponsorshipUrl}/countries`).pipe(
      tap(items => this._countries.set(items ?? [])),
      finalize(() => this._isLoadingCountries.set(false))
    );
  }

  verifyCompany(params: VerifySponsorshipParams): Observable<SponsorshipVerificationResponse> {
    let httpParams = new HttpParams()
      .set('companyName', params.companyName.trim())
      .set('country', params.country.trim());

    if (params.jobId !== undefined && params.jobId !== null && params.jobId > 0) {
      httpParams = httpParams.set('jobId', String(params.jobId));
    }
    if (params.postingText && params.postingText.trim().length > 0) {
      httpParams = httpParams.set('postingText', params.postingText.trim());
    }

    this._isVerifying.set(true);
    return this.http
      .get<SponsorshipVerificationResponse>(`${this.sponsorshipUrl}/verify`, { params: httpParams })
      .pipe(
        tap(result => this._lastVerification.set(result)),
        finalize(() => this._isVerifying.set(false))
      );
  }

  loadHistory(
    options: {
      readonly companyName?: string;
      readonly country?: string;
      readonly page?: number;
      readonly size?: number;
    } = {}
  ): Observable<PagedSponsorshipHistoryResponse> {
    let httpParams = new HttpParams()
      .set('page', String(options.page ?? 0))
      .set('size', String(options.size ?? 15));

    if (options.companyName && options.companyName.trim().length > 0) {
      httpParams = httpParams.set('companyName', options.companyName.trim());
    }
    if (options.country && options.country.trim().length > 0) {
      httpParams = httpParams.set('country', options.country.trim());
    }

    this._isLoadingHistory.set(true);
    return this.http
      .get<PagedSponsorshipHistoryResponse>(`${this.sponsorshipUrl}/history`, { params: httpParams })
      .pipe(
        tap(page => this._historyPage.set(page)),
        finalize(() => this._isLoadingHistory.set(false))
      );
  }

  searchIndRegistry(
    options: {
      readonly query?: string;
      readonly page?: number;
      readonly size?: number;
    } = {}
  ): Observable<PagedIndRegistryResponse> {
    let httpParams = new HttpParams()
      .set('page', String(options.page ?? 0))
      .set('size', String(options.size ?? 15));

    if (options.query && options.query.trim().length > 0) {
      httpParams = httpParams.set('query', options.query.trim());
    }

    this._isLoadingIndRegistry.set(true);
    return this.http
      .get<PagedIndRegistryResponse>(`${this.sponsorshipUrl}/registry/ind`, { params: httpParams })
      .pipe(
        tap(page => this._indRegistryPage.set(page)),
        finalize(() => this._isLoadingIndRegistry.set(false))
      );
  }

  loadAdminIndStatus(): Observable<RegistrySyncStatusInfo> {
    return this.http.get<RegistrySyncStatusInfo>(`${this.adminSponsorshipUrl}/ind/status`).pipe(
      tap(status => this._adminSyncStatus.set(status))
    );
  }

  triggerAdminIndSync(): Observable<IndSyncResult> {
    this._isSyncingInd.set(true);
    return this.http.post<IndSyncResult>(`${this.adminSponsorshipUrl}/ind/sync`, {}).pipe(
      finalize(() => this._isSyncingInd.set(false))
    );
  }
}

