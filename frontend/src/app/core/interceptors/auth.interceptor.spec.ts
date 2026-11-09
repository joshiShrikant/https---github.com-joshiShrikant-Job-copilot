import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { SESSION_HINT_STORAGE_KEY, AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let router: Router;

  const mockUser = {
    uuid: 'u-1',
    email: 'alex@eurojobcopilot.com',
    firstName: 'Alex',
    lastName: 'Mueller',
    role: 'ROLE_USER',
    accountStatus: 'ACTIVE',
    emailVerified: true
  };

  beforeEach(() => {
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'auth/login', children: [] }]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);
  });

  function signInMockUser(token = 'initial-jwt'): void {
    authService.login({ email: mockUser.email, password: 'Password1!' }).subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush({
      accessToken: token,
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: mockUser
    });
  }

  it('should attach Bearer token to authenticated API requests and skip public login/register endpoints', () => {
    signInMockUser('token-abc');

    http.get('/api/v1/profile').subscribe();
    const profileReq = httpMock.expectOne('/api/v1/profile');
    expect(profileReq.request.headers.get('Authorization')).toBe('Bearer token-abc');
    profileReq.flush({});

    http.post('/api/v1/auth/login', { email: 'a@b.com', password: 'x' }).subscribe();
    const loginReq = httpMock.expectOne('/api/v1/auth/login');
    expect(loginReq.request.headers.has('Authorization')).toBeFalse();
    loginReq.flush({});

    http.get('https://external.example.com/public').subscribe();
    const extReq = httpMock.expectOne('https://external.example.com/public');
    expect(extReq.request.headers.has('Authorization')).toBeFalse();
    extReq.flush({});
  });

  it('should silently refresh once on 401 and retry concurrent API requests with updated token', () => {
    signInMockUser('expired-jwt');

    let profileDone = false;
    let resumesDone = false;

    http.get('/api/v1/profile').subscribe(() => (profileDone = true));
    http.get('/api/v1/resumes').subscribe(() => (resumesDone = true));

    const req1 = httpMock.expectOne('/api/v1/profile');
    const req2 = httpMock.expectOne('/api/v1/resumes');

    req1.flush({ message: 'Expired' }, { status: 401, statusText: 'Unauthorized' });
    req2.flush({ message: 'Expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReqs = httpMock.match('/api/v1/auth/refresh');
    expect(refreshReqs.length).toBe(1);
    refreshReqs[0].flush({
      accessToken: 'fresh-jwt',
      tokenType: 'Bearer',
      expiresInSeconds: 900
    });

    const retried1 = httpMock.expectOne('/api/v1/profile');
    const retried2 = httpMock.expectOne('/api/v1/resumes');
    expect(retried1.request.headers.get('Authorization')).toBe('Bearer fresh-jwt');
    expect(retried2.request.headers.get('Authorization')).toBe('Bearer fresh-jwt');

    retried1.flush({ ok: true });
    retried2.flush([]);

    expect(profileDone).toBeTrue();
    expect(resumesDone).toBeTrue();
  });

  it('should clear session and navigate to login if token refresh fails', () => {
    signInMockUser('expired-jwt');
    const navSpy = spyOn(router, 'navigate').and.resolveTo(true);

    let failedStatus = 0;
    http.get('/api/v1/profile').subscribe({
      error: err => {
        failedStatus = err.status;
      }
    });

    httpMock
      .expectOne('/api/v1/profile')
      .flush({ message: 'Expired' }, { status: 401, statusText: 'Unauthorized' });

    httpMock
      .expectOne('/api/v1/auth/refresh')
      .flush({ message: 'Refresh revoked' }, { status: 401, statusText: 'Unauthorized' });

    expect(failedStatus).toBe(401);
    expect(authService.isAuthenticated()).toBeFalse();
    expect(navSpy).toHaveBeenCalledWith(['/auth/login'], jasmine.any(Object));
  });
});

