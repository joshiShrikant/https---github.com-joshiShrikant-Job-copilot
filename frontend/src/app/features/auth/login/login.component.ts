import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractApiErrorMessage } from '../../../core/models/api.models';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  // Bound from ?returnUrl=... via withComponentInputBinding()
  readonly returnUrl = input<string>('');

  readonly hidePassword = signal<boolean>(true);
  readonly rememberSession = signal<boolean>(false);
  readonly isSubmitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    password: ['', [Validators.required]]
  });

  togglePasswordVisibility(): void {
    this.hidePassword.update(hidden => !hidden);
  }

  toggleRememberSession(): void {
    this.rememberSession.update(val => !val);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage.set(null);
      return;
    }

    if (this.isSubmitting()) {
      return;
    }

    this.errorMessage.set(null);
    this.isSubmitting.set(true);

    const payload = {
      email: this.form.controls.email.value.trim(),
      password: this.form.controls.password.value
    };

    this.authService
      .login(payload, this.rememberSession())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false))
      )
      .subscribe({
        next: () => {
          const requestedReturnUrl = this.returnUrl()?.trim();
          const target = this.authService.isSafeInternalReturnUrl(requestedReturnUrl)
            ? requestedReturnUrl
            : '/dashboard';
          void this.router.navigateByUrl(target);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Invalid email or password. Please try again.')
          );
        }
      });
  }
}
