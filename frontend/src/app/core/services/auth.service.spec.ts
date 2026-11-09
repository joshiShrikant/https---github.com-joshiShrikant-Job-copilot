import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SESSION_HINT_STORAGE_KEY, AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const mockUser = {
    uuid: 'user-uuid-123',
    email: 'alex@eurojobcopilot.com',
    firstName: 'Alex',
    lastName: 'Mueller',
    role: 'ROLE_USER',
    accountStatus: 'ACTIVE',
    emailVerified: false
  };

  beforeEach(() => {
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);
  });

  it('should authenticate via login, store signal state, and persist persistent session hint when rememberSession=true', () => {
    service.login({ email: mockUser.email, password: 'Password1!' }, true).subscribe(res => {
      expect(res.accessToken).toBe('jwt-access-1');
    });

    const req = httpMock.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({
      accessToken: 'jwt-access-1',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: mockUser
    });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.accessToken()).toBe('jwt-access-1');
    expect(service.currentUser()).toEqual(mockUser);
    expect(service.userFullName()).toBe('Alex Mueller');
    expect(localStorage.getItem(SESSION_HINT_STORAGE_KEY)).toBe('true');
  });

  it('should register new account via POST /api/v1/auth/register with credentials and authenticate immediately', () => {
    service
      .register({
        firstName: 'Alex',
        lastName: 'Mueller',
        email: mockUser.email,
        password: 'Password1!'
      })
      .subscribe();

    const req = httpMock.expectOne('/api/v1/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({
      accessToken: 'jwt-register-1',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: mockUser
    });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.accessToken()).toBe('jwt-register-1');
  });

  it('should deduplicate concurrent refreshAccessToken calls into one single-flight HTTP request', () => {
    const tokens: string[] = [];

    service.refreshAccessToken().subscribe(t => tokens.push(t));
    service.refreshAccessToken().subscribe(t => tokens.push(t));

    const req = httpMock.expectOne('/api/v1/auth/refresh');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({
      accessToken: 'jwt-rotated-99',
      tokenType: 'Bearer',
      expiresInSeconds: 900
    });

    expect(tokens).toEqual(['jwt-rotated-99', 'jwt-rotated-99']);
    expect(service.accessToken()).toBe('jwt-rotated-99');
  });

  it('should skip refresh when no session hint exists and restore user via refresh+me when hint exists', () => {
    let initResult: boolean | undefined;
    service.ensureSessionInitialized().subscribe(res => (initResult = res));
    httpMock.expectNone('/api/v1/auth/refresh');
    expect(initResult).toBeFalse();

    // Reset fresh service instance with stored session hint
    TestBed.resetTestingModule();
    localStorage.setItem(SESSION_HINT_STORAGE_KEY, 'true');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    const freshService = TestBed.inject(AuthService);
    const freshHttpMock = TestBed.inject(HttpTestingController);

    let restored: boolean | undefined;
    freshService.ensureSessionInitialized().subscribe(res => (restored = res));

    const refreshReq = freshHttpMock.expectOne('/api/v1/auth/refresh');
    refreshReq.flush({ accessToken: 'restored-jwt', tokenType: 'Bearer', expiresInSeconds: 900 });

    const meReq = freshHttpMock.expectOne('/api/v1/auth/me');
    meReq.flush(mockUser);

    expect(restored).toBeTrue();
    expect(freshService.isAuthenticated()).toBeTrue();
    expect(freshService.currentUser()).toEqual(mockUser);
    freshHttpMock.verify();
  });

  it('should clear session signals and hints on logout even when backend returns an error', () => {
    service.login({ email: mockUser.email, password: 'Password1!' }, true).subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush({
      accessToken: 'jwt-1',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: mockUser
    });

    let completed = false;
    let authenticatedDuringCallback: boolean | undefined;
    service.logout().subscribe(() => {
      completed = true;
      authenticatedDuringCallback = service.isAuthenticated();
    });

    const logoutReq = httpMock.expectOne('/api/v1/auth/logout');
    expect(logoutReq.request.withCredentials).toBeTrue();
    logoutReq.flush({ message: 'Expired' }, { status: 401, statusText: 'Unauthorized' });

    expect(completed).toBeTrue();
    expect(authenticatedDuringCallback).toBeFalse();
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.accessToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(localStorage.getItem(SESSION_HINT_STORAGE_KEY)).toBeNull();
  });

  it('should validate internal return URLs safely', () => {
    expect(service.isSafeInternalReturnUrl('/dashboard')).toBeTrue();
    expect(service.isSafeInternalReturnUrl('/sponsorship?country=DE')).toBeTrue();
    expect(service.isSafeInternalReturnUrl('https://evil.example.com')).toBeFalse();
    expect(service.isSafeInternalReturnUrl('//evil.example.com')).toBeFalse();
    expect(service.isSafeInternalReturnUrl('/auth/login')).toBeFalse();
    expect(service.isSafeInternalReturnUrl('')).toBeFalse();
  });
});

