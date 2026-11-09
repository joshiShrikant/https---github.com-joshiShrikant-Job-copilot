import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractApiErrorMessage } from '../../../core/models/api.models';
import { AuthService } from '../../../core/services/auth.service';
import { PasswordStrengthComponent } from '../components/password-strength.component';
import { PASSWORD_COMPLEXITY_REGEX, passwordsMatchValidator } from '../utils/auth-validators';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, PasswordStrengthComponent],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly hidePassword = signal<boolean>(true);
  readonly hideConfirmPassword = signal<boolean>(true);
  readonly isSubmitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group(
    {
      firstName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      lastName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
      password: ['', [Validators.required, Validators.pattern(PASSWORD_COMPLEXITY_REGEX)]],
      confirmPassword: ['', [Validators.required]]
    },
    {
      validators: [passwordsMatchValidator('password', 'confirmPassword')]
    }
  );

  readonly passwordValue = toSignal(this.form.controls.password.valueChanges, {
    initialValue: ''
  });

  togglePasswordVisibility(): void {
    this.hidePassword.update(hidden => !hidden);
  }

  toggleConfirmPasswordVisibility(): void {
    this.hideConfirmPassword.update(hidden => !hidden);
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
      firstName: this.form.controls.firstName.value.trim(),
      lastName: this.form.controls.lastName.value.trim(),
      email: this.form.controls.email.value.trim(),
      password: this.form.controls.password.value
    };

    this.authService
      .register(payload)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false))
      )
      .subscribe({
        next: () => {
          void this.router.navigateByUrl('/dashboard');
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to create account. Please review your details.')
          );
        }
      });
  }
}
