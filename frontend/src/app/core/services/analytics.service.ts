import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MarketInsightsResponse } from '../models/analytics.models';

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/analytics`;

  getMarketInsights(
    countries?: readonly string[],
    days = 30
  ): Observable<MarketInsightsResponse> {
    const clampedDays = Math.max(7, Math.min(180, Math.trunc(days)));
    let params = new HttpParams().set('days', String(clampedDays));

    if (countries && countries.length > 0) {
      for (const country of countries) {
        const trimmed = country.trim();
        if (trimmed) {
          params = params.append('countries', trimmed);
        }
      }
    }

    return this.http.get<MarketInsightsResponse>(`${this.baseUrl}/market-insights`, { params });
  }
}

