import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.ensureSessionInitialized().pipe(
    map(isAuthenticated => {
      if (isAuthenticated) {
        return true;
      }

      const queryParams =
        state.url && state.url !== '/' && authService.isSafeInternalReturnUrl(state.url)
          ? { returnUrl: state.url }
          : undefined;

      return router.createUrlTree(['/auth/login'], { queryParams });
    })
  );
};

export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.ensureSessionInitialized().pipe(
    map(isAuthenticated => (isAuthenticated ? router.createUrlTree(['/dashboard']) : true))
  );
};

export const adminGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.ensureSessionInitialized().pipe(
    map(isAuthenticated => {
      if (!isAuthenticated) {
        const queryParams =
          state.url && state.url !== '/' && authService.isSafeInternalReturnUrl(state.url)
            ? { returnUrl: state.url }
            : undefined;
        return router.createUrlTree(['/auth/login'], { queryParams });
      }

      return authService.isAdmin() ? true : router.createUrlTree(['/dashboard']);
    })
  );
};

