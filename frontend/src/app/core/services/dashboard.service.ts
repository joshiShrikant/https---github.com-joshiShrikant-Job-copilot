import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, finalize, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardSummaryResponse } from '../models/dashboard.models';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly summaryUrl = `${environment.apiUrl}/dashboard/summary`;

  private readonly _summary = signal<DashboardSummaryResponse | null>(null);
  private readonly _isLoading = signal<boolean>(false);

  readonly summary = this._summary.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();

  loadSummary(): Observable<DashboardSummaryResponse> {
    this._isLoading.set(true);
    return this.http.get<DashboardSummaryResponse>(this.summaryUrl).pipe(
      tap(response => this._summary.set(response)),
      finalize(() => this._isLoading.set(false))
    );
  }
}

