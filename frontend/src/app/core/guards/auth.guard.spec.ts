import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { SESSION_HINT_STORAGE_KEY, AuthService } from '../services/auth.service';
import { adminGuard, authGuard, guestGuard } from './auth.guard';

describe('Auth Route Guards', () => {
  let authService: AuthService;
  let httpMock: HttpTestingController;
  let router: Router;

  const mockRoute = {} as ActivatedRouteSnapshot;

  function mockState(url: string): RouterStateSnapshot {
    return { url } as RouterStateSnapshot;
  }

  function resolveGuardResult(result: unknown): Promise<boolean | UrlTree> {
    if (result instanceof Observable) {
      return firstValueFrom(result as Observable<boolean | UrlTree>);
    }
    return Promise.resolve(result as boolean | UrlTree);
  }

  beforeEach(() => {
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);

    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });

    authService = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);
  });

  function authenticateAs(role = 'ROLE_USER'): void {
    authService.login({ email: 'alex@eurojobcopilot.com', password: 'Password1!' }).subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush({
      accessToken: 'jwt-1',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: {
        uuid: 'u-1',
        email: 'alex@eurojobcopilot.com',
        firstName: 'Alex',
        lastName: 'Mueller',
        role,
        accountStatus: 'ACTIVE',
        emailVerified: true
      }
    });
  }

  it('authGuard should redirect unauthenticated users to /auth/login with returnUrl and allow authenticated users', async () => {
    const blocked = await TestBed.runInInjectionContext(() =>
      resolveGuardResult(authGuard(mockRoute, mockState('/sponsorship')))
    );
    expect(blocked instanceof UrlTree).toBeTrue();
    expect(router.serializeUrl(blocked as UrlTree)).toBe('/auth/login?returnUrl=%2Fsponsorship');

    authenticateAs('ROLE_USER');
    const allowed = await TestBed.runInInjectionContext(() =>
      resolveGuardResult(authGuard(mockRoute, mockState('/sponsorship')))
    );
    expect(allowed).toBeTrue();
  });

  it('guestGuard should allow guests and redirect authenticated users to /dashboard', async () => {
    const guestAllowed = await TestBed.runInInjectionContext(() =>
      resolveGuardResult(guestGuard(mockRoute, mockState('/auth/login')))
    );
    expect(guestAllowed).toBeTrue();

    authenticateAs('ROLE_USER');
    const redirected = await TestBed.runInInjectionContext(() =>
      resolveGuardResult(guestGuard(mockRoute, mockState('/auth/login')))
    );
    expect(redirected instanceof UrlTree).toBeTrue();
    expect(router.serializeUrl(redirected as UrlTree)).toBe('/dashboard');
  });

  it('adminGuard should deny non-admin users cleanly and allow ROLE_ADMIN', async () => {
    authenticateAs('ROLE_USER');
    const nonAdmin = await TestBed.runInInjectionContext(() =>
      resolveGuardResult(adminGuard(mockRoute, mockState('/admin')))
    );
    expect(router.serializeUrl(nonAdmin as UrlTree)).toBe('/dashboard');

    authService.clearSession();
    authenticateAs('ROLE_ADMIN');
    const adminAllowed = await TestBed.runInInjectionContext(() =>
      resolveGuardResult(adminGuard(mockRoute, mockState('/admin')))
    );
    expect(adminAllowed).toBeTrue();
  });
});

