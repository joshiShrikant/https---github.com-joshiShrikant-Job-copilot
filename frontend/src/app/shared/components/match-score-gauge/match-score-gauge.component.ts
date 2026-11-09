import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type MatchGaugeTier = 'high' | 'good' | 'fair' | 'low' | 'unscored';

const RING_RADIUS = 18;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

@Component({
  selector: 'app-match-score-gauge',
  standalone: true,
  templateUrl: './match-score-gauge.component.html',
  styleUrl: './match-score-gauge.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MatchScoreGaugeComponent {
  readonly score = input<number | null | undefined>(null);
  readonly label = input<string>('Match');
  readonly showLabel = input<boolean>(false);
  readonly size = input<'sm' | 'md' | 'lg'>('md');

  readonly circumference = RING_CIRCUMFERENCE;

  readonly normalizedScore = computed<number | null>(() => {
    const raw = this.score();
    if (raw === null || raw === undefined || !Number.isFinite(raw)) {
      return null;
    }
    return Math.max(0, Math.min(100, Math.round(raw)));
  });

  readonly tier = computed<MatchGaugeTier>(() => {
    const val = this.normalizedScore();
    if (val === null) return 'unscored';
    if (val >= 80) return 'high';
    if (val >= 60) return 'good';
    if (val >= 40) return 'fair';
    return 'low';
  });

  readonly tierDescription = computed<string>(() => {
    switch (this.tier()) {
      case 'high':
        return 'Strong match';
      case 'good':
        return 'Good match';
      case 'fair':
        return 'Partial match';
      case 'low':
        return 'Low match';
      case 'unscored':
      default:
        return 'Score pending';
    }
  });

  readonly dashOffset = computed<number>(() => {
    const val = this.normalizedScore();
    if (val === null || val <= 0) {
      return RING_CIRCUMFERENCE;
    }
    return RING_CIRCUMFERENCE * (1 - val / 100);
  });

  readonly ariaLabelText = computed<string>(() => {
    const val = this.normalizedScore();
    if (val === null) {
      return `${this.label()}: score unavailable`;
    }
    return `${this.label()}: ${val}% (${this.tierDescription()})`;
  });
}

