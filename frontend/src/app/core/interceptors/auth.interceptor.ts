import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';

export const AUTH_RETRY_ATTEMPTED = new HttpContextToken<boolean>(() => false);

function isBackendApiRequest(url: string): boolean {
  return url.startsWith(environment.apiUrl) || url.startsWith('/api/');
}

function isPublicAuthEndpoint(url: string): boolean {
  return (
    url.includes('/auth/login') ||
    url.includes('/auth/register') ||
    url.includes('/auth/refresh')
  );
}

function isLogoutEndpoint(url: string): boolean {
  return url.includes('/auth/logout');
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isBackendApiRequest(req.url) || isPublicAuthEndpoint(req.url)) {
    return next(req);
  }

  const authService = inject(AuthService);
  const router = inject(Router);
  const accessToken = authService.accessToken();

  const authReq = accessToken
    ? req.clone({
        setHeaders: {
          Authorization: `Bearer ${accessToken}`
        }
      })
    : req;

  return next(authReq).pipe(
    catchError((error: unknown) => {
      if (
        !(error instanceof HttpErrorResponse) ||
        error.status !== 401 ||
        req.context.get(AUTH_RETRY_ATTEMPTED) ||
        isLogoutEndpoint(req.url)
      ) {
        return throwError(() => error);
      }

      if (!authService.accessToken() && !authService.hasSessionHint()) {
        return throwError(() => error);
      }

      return authService.refreshAccessToken().pipe(
        switchMap(newToken => {
          const retriedReq = req.clone({
            setHeaders: {
              Authorization: `Bearer ${newToken}`
            },
            context: req.context.set(AUTH_RETRY_ATTEMPTED, true)
          });
          return next(retriedReq);
        }),
        catchError(refreshError => {
          authService.clearSession();
          if (!router.url.startsWith('/auth')) {
            const returnUrl = authService.isSafeInternalReturnUrl(router.url)
              ? router.url
              : undefined;
            void router.navigate(['/auth/login'], {
              queryParams: returnUrl ? { returnUrl } : undefined
            });
          }
          return throwError(() => refreshError);
        })
      );
    })
  );
};

