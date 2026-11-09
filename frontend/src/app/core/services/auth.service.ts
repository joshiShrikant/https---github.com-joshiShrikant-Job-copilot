import { HttpClient } from '@angular/common/http';
import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api.models';
import {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  TokenRefreshResponse,
  UserSummary
} from '../models/auth.models';

export const SESSION_HINT_STORAGE_KEY = 'eurojob-session-active';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);

  private readonly authBaseUrl = `${environment.apiUrl}/auth`;

  private readonly _currentUser = signal<UserSummary | null>(null);
  private readonly _accessToken = signal<string | null>(null);
  private readonly _tokenExpiresAt = signal<number | null>(null);
  private readonly _sessionInitialized = signal<boolean>(false);

  readonly currentUser = this._currentUser.asReadonly();
  readonly accessToken = this._accessToken.asReadonly();
  readonly sessionInitialized = this._sessionInitialized.asReadonly();

  readonly isAuthenticated = computed(
    () => this._currentUser() !== null && this._accessToken() !== null
  );
  readonly isAdmin = computed(() => this._currentUser()?.role === 'ROLE_ADMIN');
  readonly userFullName = computed(() => {
    const user = this._currentUser();
    if (!user) {
      return 'Workspace Guest';
    }
    const fullName = `${user.firstName} ${user.lastName}`.trim();
    return fullName || user.email;
  });

  private inFlightRefresh$: Observable<string> | null = null;
  private inFlightInit$: Observable<boolean> | null = null;

  register(payload: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.authBaseUrl}/register`, payload, { withCredentials: true })
      .pipe(tap(response => this.applyAuthResponse(response, true)));
  }

  login(payload: LoginRequest, rememberSession = false): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.authBaseUrl}/login`, payload, { withCredentials: true })
      .pipe(tap(response => this.applyAuthResponse(response, rememberSession)));
  }

  fetchCurrentUser(): Observable<UserSummary> {
    return this.http
      .get<UserSummary>(`${this.authBaseUrl}/me`)
      .pipe(tap(user => this._currentUser.set(user)));
  }

  refreshAccessToken(): Observable<string> {
    if (this.inFlightRefresh$) {
      return this.inFlightRefresh$;
    }

    const request$ = this.http
      .post<TokenRefreshResponse>(`${this.authBaseUrl}/refresh`, {}, { withCredentials: true })
      .pipe(
        tap(response => this.applyTokenRefresh(response)),
        map(response => response.accessToken),
        catchError(error => {
          this.clearSession();
          return throwError(() => error);
        }),
        finalize(() => {
          this.inFlightRefresh$ = null;
        }),
        shareReplay(1)
      );

    this.inFlightRefresh$ = request$;
    return request$;
  }

  ensureSessionInitialized(): Observable<boolean> {
    if (this._sessionInitialized()) {
      return of(this.isAuthenticated());
    }

    if (this.inFlightInit$) {
      return this.inFlightInit$;
    }

    if (!this.hasSessionHint()) {
      this._sessionInitialized.set(true);
      return of(false);
    }

    const restore$ = this.refreshAccessToken().pipe(
      switchMap(() => this.fetchCurrentUser()),
      map(() => true),
      catchError(() => {
        this.clearSession();
        return of(false);
      }),
      finalize(() => {
        this._sessionInitialized.set(true);
        this.inFlightInit$ = null;
      }),
      shareReplay(1)
    );

    this.inFlightInit$ = restore$;
    return restore$;
  }

  logout(revokeAllSessions = false): Observable<void> {
    const token = this._accessToken();
    if (!token) {
      this.clearSession();
      return of(void 0);
    }

    const body = revokeAllSessions ? { revokeAllSessions: true } : {};
    return this.http
      .post<ApiResponse<void>>(`${this.authBaseUrl}/logout`, body, { withCredentials: true })
      .pipe(
        map(() => void 0),
        catchError(() => of(void 0)),
        tap(() => this.clearSession()),
        finalize(() => this.clearSession())
      );
  }

  clearSession(): void {
    this._currentUser.set(null);
    this._accessToken.set(null);
    this._tokenExpiresAt.set(null);
    this._sessionInitialized.set(true);
    this.clearSessionHint();
  }

  syncCurrentUserName(firstName: string, lastName: string): void {
    const existing = this._currentUser();
    if (!existing) {
      return;
    }
    const nextFirst = firstName.trim();
    const nextLast = lastName.trim();
    if (!nextFirst && !nextLast) {
      return;
    }
    this._currentUser.set({
      ...existing,
      firstName: nextFirst || existing.firstName,
      lastName: nextLast || existing.lastName
    });
  }

  isAccessTokenExpiringSoon(bufferSeconds = 10): boolean {
    const expiresAt = this._tokenExpiresAt();
    if (expiresAt === null) {
      return false;
    }
    return Date.now() >= expiresAt - bufferSeconds * 1000;
  }

  hasSessionHint(): boolean {
    try {
      const win = this.document.defaultView;
      return (
        win?.localStorage?.getItem(SESSION_HINT_STORAGE_KEY) === 'true' ||
        win?.sessionStorage?.getItem(SESSION_HINT_STORAGE_KEY) === 'true'
      );
    } catch {
      return false;
    }
  }

  isSafeInternalReturnUrl(url: string | null | undefined): boolean {
    if (!url || !url.startsWith('/') || url.startsWith('//')) {
      return false;
    }
    return !url.startsWith('/auth');
  }

  private applyAuthResponse(response: AuthResponse, persistent: boolean): void {
    this._accessToken.set(response.accessToken);
    this._tokenExpiresAt.set(this.computeExpiryMillis(response.expiresInSeconds));
    this._currentUser.set(response.user);
    this._sessionInitialized.set(true);
    this.writeSessionHint(persistent);
  }

  private applyTokenRefresh(response: TokenRefreshResponse): void {
    this._accessToken.set(response.accessToken);
    this._tokenExpiresAt.set(this.computeExpiryMillis(response.expiresInSeconds));
  }

  private computeExpiryMillis(expiresInSeconds: number | undefined): number | null {
    if (!expiresInSeconds || expiresInSeconds <= 0) {
      return null;
    }
    return Date.now() + expiresInSeconds * 1000;
  }

  private writeSessionHint(persistent: boolean): void {
    try {
      const win = this.document.defaultView;
      if (persistent) {
        win?.localStorage?.setItem(SESSION_HINT_STORAGE_KEY, 'true');
        win?.sessionStorage?.removeItem(SESSION_HINT_STORAGE_KEY);
      } else {
        win?.sessionStorage?.setItem(SESSION_HINT_STORAGE_KEY, 'true');
        win?.localStorage?.removeItem(SESSION_HINT_STORAGE_KEY);
      }
    } catch {
      // Ignore storage access restrictions
    }
  }

  private clearSessionHint(): void {
    try {
      const win = this.document.defaultView;
      win?.localStorage?.removeItem(SESSION_HINT_STORAGE_KEY);
      win?.sessionStorage?.removeItem(SESSION_HINT_STORAGE_KEY);
    } catch {
      // Ignore storage access restrictions
    }
  }
}

