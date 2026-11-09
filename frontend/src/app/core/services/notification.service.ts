import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api.models';
import {
  MarkAllReadResponse,
  NotificationFilterParams,
  NotificationItem,
  PagedNotificationsResponse,
  UnreadCountResponse
} from '../models/notification.models';

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/notifications`;

  private readonly _unreadCount = signal<number>(0);
  readonly unreadCount = this._unreadCount.asReadonly();

  loadNotifications(
    filters?: NotificationFilterParams
  ): Observable<PagedNotificationsResponse> {
    const page = Math.max(0, Math.trunc(filters?.page ?? 0));
    const size = Math.max(1, Math.min(100, Math.trunc(filters?.size ?? 20)));

    let params = new HttpParams()
      .set('unreadOnly', String(Boolean(filters?.unreadOnly)))
      .set('page', String(page))
      .set('size', String(size));

    if (filters?.type) {
      params = params.set('type', filters.type);
    }

    return this.http.get<PagedNotificationsResponse>(this.baseUrl, { params }).pipe(
      tap(response => {
        this._unreadCount.set(Math.max(0, response.unreadCount ?? 0));
      })
    );
  }

  loadUnreadCount(): Observable<UnreadCountResponse> {
    return this.http.get<UnreadCountResponse>(`${this.baseUrl}/unread-count`).pipe(
      tap(response => {
        this._unreadCount.set(Math.max(0, response.unreadCount ?? 0));
      })
    );
  }

  markRead(id: number): Observable<NotificationItem> {
    return this.http.patch<NotificationItem>(`${this.baseUrl}/${id}/read`, {}).pipe(
      tap(() => {
        this._unreadCount.update(count => Math.max(0, count - 1));
      })
    );
  }

  markAllRead(): Observable<MarkAllReadResponse> {
    return this.http.post<MarkAllReadResponse>(`${this.baseUrl}/mark-all-read`, {}).pipe(
      tap(response => {
        this._unreadCount.set(Math.max(0, response.unreadCount ?? 0));
      })
    );
  }

  deleteNotification(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/${id}`);
  }

  setUnreadCount(count: number): void {
    this._unreadCount.set(Math.max(0, count));
  }

  resetState(): void {
    this._unreadCount.set(0);
  }
}

