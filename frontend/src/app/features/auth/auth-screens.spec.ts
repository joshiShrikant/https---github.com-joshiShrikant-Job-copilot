import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { SESSION_HINT_STORAGE_KEY, AuthService } from '../../core/services/auth.service';
import { ForgotPasswordComponent } from './forgot-password/forgot-password.component';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';
import { ResetPasswordComponent } from './reset-password/reset-password.component';

describe('Auth UI Screens', () => {
  const mockUser = {
    uuid: 'u-1',
    email: 'alex@eurojobcopilot.com',
    firstName: 'Alex',
    lastName: 'Mueller',
    role: 'ROLE_USER',
    accountStatus: 'ACTIVE',
    emailVerified: false
  };

  afterEach(() => {
    localStorage.removeItem(SESSION_HINT_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_HINT_STORAGE_KEY);
  });

  describe('LoginComponent', () => {
    let httpMock: HttpTestingController;
    let authService: AuthService;
    let router: Router;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [LoginComponent],
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
      }).compileComponents();

      httpMock = TestBed.inject(HttpTestingController);
      authService = TestBed.inject(AuthService);
      router = TestBed.inject(Router);
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('should validate required email & password without issuing HTTP requests', () => {
      const fixture = TestBed.createComponent(LoginComponent);
      fixture.detectChanges();

      const host = fixture.nativeElement as HTMLElement;
      host.querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
      fixture.detectChanges();

      httpMock.expectNone('/api/v1/auth/login');
      expect(host.textContent).toContain('Email is required.');
      expect(host.textContent).toContain('Password is required.');
    });

    it('should submit valid login credentials, authenticate session, and redirect to safe returnUrl', () => {
      const navSpy = spyOn(router, 'navigateByUrl').and.resolveTo(true);

      const fixture = TestBed.createComponent(LoginComponent);
      fixture.componentRef.setInput('returnUrl', '/sponsorship');
      fixture.detectChanges();

      const component = fixture.componentInstance;
      const host = fixture.nativeElement as HTMLElement;
      const passwordInput = host.querySelector<HTMLInputElement>('#login-password');
      host.querySelector<HTMLButtonElement>('.visibility-toggle')?.click();
      fixture.detectChanges();
      expect(passwordInput?.type).toBe('text');

      component.form.setValue({
        email: 'alex@eurojobcopilot.com',
        password: 'SecureP@ssw0rd2026!'
      });
      host.querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
      fixture.detectChanges();

      const req = httpMock.expectOne('/api/v1/auth/login');
      expect(req.request.method).toBe('POST');
      expect(req.request.withCredentials).toBeTrue();
      req.flush({
        accessToken: 'jwt-login-ok',
        tokenType: 'Bearer',
        expiresInSeconds: 900,
        user: mockUser
      });

      expect(authService.isAuthenticated()).toBeTrue();
      expect(navSpy).toHaveBeenCalledWith('/sponsorship');
    });

    it('should display backend API error message on 401 authentication failure', () => {
      const fixture = TestBed.createComponent(LoginComponent);
      fixture.detectChanges();

      fixture.componentInstance.form.setValue({
        email: 'alex@eurojobcopilot.com',
        password: 'WrongPassword!'
      });
      fixture.componentInstance.onSubmit();

      httpMock.expectOne('/api/v1/auth/login').flush(
        {
          status: 401,
          error: 'Unauthorized',
          message: 'Invalid email or password',
          path: '/api/v1/auth/login'
        },
        { status: 401, statusText: 'Unauthorized' }
      );
      fixture.detectChanges();

      const host = fixture.nativeElement as HTMLElement;
      expect(host.querySelector('.status-banner.error')?.textContent).toContain(
        'Invalid email or password'
      );
      expect(fixture.componentInstance.isSubmitting()).toBeFalse();
    });
  });

  describe('RegisterComponent', () => {
    let httpMock: HttpTestingController;
    let authService: AuthService;
    let router: Router;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [RegisterComponent],
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
      }).compileComponents();

      httpMock = TestBed.inject(HttpTestingController);
      authService = TestBed.inject(AuthService);
      router = TestBed.inject(Router);
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('should enforce backend RegisterRequest rules, submit registration, and redirect to /dashboard', () => {
      const navSpy = spyOn(router, 'navigateByUrl').and.resolveTo(true);

      const fixture = TestBed.createComponent(RegisterComponent);
      fixture.detectChanges();

      const component = fixture.componentInstance;
      const host = fixture.nativeElement as HTMLElement;

      component.form.setValue({
        firstName: 'A',
        lastName: 'B',
        email: 'bad-email',
        password: 'weak',
        confirmPassword: 'other'
      });
      component.onSubmit();
      fixture.detectChanges();

      httpMock.expectNone('/api/v1/auth/register');
      expect(component.form.invalid).toBeTrue();
      expect(component.form.hasError('passwordMismatch')).toBeTrue();
      expect(host.querySelector('.strength-badge')?.textContent?.trim()).toBe('Weak');

      component.form.setValue({
        firstName: 'Alex',
        lastName: 'Mueller',
        email: 'alex@eurojobcopilot.com',
        password: 'SecureP@ssw0rd2026!',
        confirmPassword: 'SecureP@ssw0rd2026!'
      });
      fixture.detectChanges();
      expect(host.querySelector('.strength-badge')?.textContent?.trim()).toBe('Strong');

      component.onSubmit();

      const req = httpMock.expectOne('/api/v1/auth/register');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        firstName: 'Alex',
        lastName: 'Mueller',
        email: 'alex@eurojobcopilot.com',
        password: 'SecureP@ssw0rd2026!'
      });
      req.flush({
        accessToken: 'jwt-register-ok',
        tokenType: 'Bearer',
        expiresInSeconds: 900,
        user: mockUser
      });

      expect(authService.isAuthenticated()).toBeTrue();
      expect(navSpy).toHaveBeenCalledWith('/dashboard');
    });

    it('should surface 409 conflict email error cleanly from ApiErrorResponse', () => {
      const fixture = TestBed.createComponent(RegisterComponent);
      fixture.detectChanges();

      fixture.componentInstance.form.setValue({
        firstName: 'Alex',
        lastName: 'Mueller',
        email: 'alex@eurojobcopilot.com',
        password: 'SecureP@ssw0rd2026!',
        confirmPassword: 'SecureP@ssw0rd2026!'
      });
      fixture.componentInstance.onSubmit();

      httpMock.expectOne('/api/v1/auth/register').flush(
        {
          status: 409,
          error: 'Conflict',
          message: 'Email address is already registered',
          path: '/api/v1/auth/register'
        },
        { status: 409, statusText: 'Conflict' }
      );
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).querySelector('.status-banner.error')?.textContent).toContain(
        'Email address is already registered'
      );
    });
  });

  describe('ForgotPasswordComponent', () => {
    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [ForgotPasswordComponent],
        providers: [provideRouter([])]
      }).compileComponents();
    });

    it('should transition to confirmation card on valid email submit and allow resetting form state', () => {
      const fixture = TestBed.createComponent(ForgotPasswordComponent);
      fixture.detectChanges();

      const component = fixture.componentInstance;
      const host = fixture.nativeElement as HTMLElement;

      component.onSubmit();
      fixture.detectChanges();
      expect(component.submittedEmail()).toBeNull();
      expect(host.textContent).toContain('Email is required.');

      component.form.controls.email.setValue('candidate@eurojobcopilot.com');
      component.onSubmit();
      fixture.detectChanges();

      expect(component.submittedEmail()).toBe('candidate@eurojobcopilot.com');
      expect(host.querySelector('.confirmation-block')?.textContent).toContain(
        'candidate@eurojobcopilot.com'
      );

      host.querySelector<HTMLButtonElement>('.secondary-btn')?.click();
      fixture.detectChanges();
      expect(component.submittedEmail()).toBeNull();
      expect(host.querySelector('#forgot-email')).toBeTruthy();
    });
  });

  describe('ResetPasswordComponent & query param token binding', () => {
    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [ResetPasswordComponent],
        providers: [provideRouter([])]
      }).compileComponents();
    });

    it('should pre-fill token from input, validate matching strong passwords, and confirm submission', () => {
      const fixture = TestBed.createComponent(ResetPasswordComponent);
      fixture.componentRef.setInput('token', 'tok_reset_123456');
      fixture.detectChanges();

      expect(fixture.componentInstance.form.controls.token.value).toBe('tok_reset_123456');

      const component = fixture.componentInstance;
      component.form.controls.newPassword.setValue('NewStrongP@ss2026!');
      component.form.controls.confirmPassword.setValue('Mismatch!');
      component.onSubmit();
      fixture.detectChanges();

      expect(component.form.hasError('passwordMismatch')).toBeTrue();
      expect(component.resetCompleteNotice()).toBeNull();

      component.form.controls.confirmPassword.setValue('NewStrongP@ss2026!');
      component.onSubmit();
      fixture.detectChanges();

      expect(component.resetCompleteNotice()).toBeTruthy();
      expect((fixture.nativeElement as HTMLElement).querySelector('.status-banner.success')).toBeTruthy();
    });
  });
});
