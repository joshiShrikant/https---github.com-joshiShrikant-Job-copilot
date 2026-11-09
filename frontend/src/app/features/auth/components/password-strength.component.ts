import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { evaluatePasswordRules } from '../utils/auth-validators';

@Component({
  selector: 'app-password-strength',
  standalone: true,
  templateUrl: './password-strength.component.html',
  styleUrl: './password-strength.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PasswordStrengthComponent {
  readonly password = input.required<string>();

  readonly analysis = computed(() => evaluatePasswordRules(this.password()));

  readonly segments = computed(() => {
    const score = this.analysis().passedCount;
    const filledCount = score === 0 ? 0 : Math.min(4, Math.max(1, score - 1));
    return [1, 2, 3, 4].map(index => index <= filledCount);
  });
}

