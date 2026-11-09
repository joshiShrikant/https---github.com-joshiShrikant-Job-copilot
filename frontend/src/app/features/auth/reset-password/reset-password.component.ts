import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PasswordStrengthComponent } from '../components/password-strength.component';
import { PASSWORD_COMPLEXITY_REGEX, passwordsMatchValidator } from '../utils/auth-validators';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, PasswordStrengthComponent],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResetPasswordComponent {
  private readonly fb = inject(FormBuilder).nonNullable;

  // Automatically bound from ?token=... via withComponentInputBinding()
  readonly token = input<string>('');

  readonly hidePassword = signal<boolean>(true);
  readonly hideConfirmPassword = signal<boolean>(true);
  readonly resetCompleteNotice = signal<string | null>(null);

  readonly form = this.fb.group(
    {
      token: ['', [Validators.required, Validators.minLength(6)]],
      newPassword: ['', [Validators.required, Validators.pattern(PASSWORD_COMPLEXITY_REGEX)]],
      confirmPassword: ['', [Validators.required]]
    },
    {
      validators: [passwordsMatchValidator('newPassword', 'confirmPassword')]
    }
  );

  readonly newPasswordValue = toSignal(this.form.controls.newPassword.valueChanges, {
    initialValue: ''
  });

  constructor() {
    effect(() => {
      const queryToken = this.token()?.trim() ?? '';
      if (queryToken && !this.form.controls.token.dirty) {
        this.form.controls.token.setValue(queryToken);
      }
    });
  }

  togglePasswordVisibility(): void {
    this.hidePassword.update(hidden => !hidden);
  }

  toggleConfirmPasswordVisibility(): void {
    this.hideConfirmPassword.update(hidden => !hidden);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.resetCompleteNotice.set(null);
      return;
    }

    this.resetCompleteNotice.set(
      'Password validation passed. Backend password reset confirmation is not yet enabled in this environment.'
    );
  }
}

