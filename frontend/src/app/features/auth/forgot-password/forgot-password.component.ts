import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly submittedEmail = signal<string | null>(null);

  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]]
  });

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submittedEmail.set(this.form.controls.email.value.trim());
  }

  resetFormState(): void {
    this.submittedEmail.set(null);
  }
}

