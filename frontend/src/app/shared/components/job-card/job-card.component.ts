import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import {
  JobSummary,
  formatEmploymentTypeLabel,
  formatExperienceLevelLabel
} from '../../../core/models/job.models';
import { SalaryRangePipe } from '../../pipes/salary-range.pipe';
import { TimeAgoPipe } from '../../pipes/time-ago.pipe';
import { MatchScoreGaugeComponent } from '../match-score-gauge/match-score-gauge.component';
import { StatusBadgeComponent } from '../status-badge/status-badge.component';

@Component({
  selector: 'app-job-card',
  standalone: true,
  imports: [
    StatusBadgeComponent,
    MatchScoreGaugeComponent,
    SalaryRangePipe,
    TimeAgoPipe
  ],
  templateUrl: './job-card.component.html',
  styleUrl: './job-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JobCardComponent {
  readonly job = input.required<JobSummary>();
  readonly selected = input<boolean>(false);
  readonly compact = input<boolean>(false);
  readonly busy = input<boolean>(false);
  readonly showMatchGauge = input<boolean>(true);
  readonly maxVisibleSkills = input<number>(5);

  readonly inspectRequested = output<JobSummary>();
  readonly saveToggle = output<JobSummary>();
  readonly interestedToggle = output<JobSummary>();
  readonly hideRequested = output<JobSummary>();
  readonly unhideRequested = output<JobSummary>();
  readonly applyRequested = output<JobSummary>();

  readonly companyMonogram = computed<string>(() => {
    const words = this.job().companyName
      .trim()
      .split(/\s+/)
      .filter(Boolean);
    if (words.length === 0) return 'EJ';
    if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
    return `${words[0][0]}${words[1][0]}`.toUpperCase();
  });

  readonly formattedLocation = computed<string>(() => {
    const current = this.job();
    if (current.city && current.country) {
      return `${current.city}, ${current.country}`;
    }
    if (current.location && current.location.trim().length > 0) {
      return current.location;
    }
    return current.country;
  });

  readonly employmentSummary = computed<string>(() => {
    const current = this.job();
    const parts: string[] = [];
    if (current.employmentType) {
      parts.push(formatEmploymentTypeLabel(current.employmentType));
    }
    if (current.experienceLevel) {
      parts.push(formatExperienceLevelLabel(current.experienceLevel));
    }
    return parts.join(' · ');
  });

  readonly hasSalaryRange = computed<boolean>(() => {
    const current = this.job();
    return (
      (current.salaryMin !== null && current.salaryMin > 0) ||
      (current.salaryMax !== null && current.salaryMax > 0)
    );
  });

  readonly visibleSkills = computed<readonly string[]>(() => {
    const skills = this.job().skills ?? [];
    const cap = Math.max(0, this.maxVisibleSkills());
    return skills.slice(0, cap);
  });

  readonly hiddenSkillsCount = computed<number>(() => {
    const total = this.job().skills?.length ?? 0;
    const cap = Math.max(0, this.maxVisibleSkills());
    return Math.max(0, total - cap);
  });

  onInspect(): void {
    this.inspectRequested.emit(this.job());
  }

  onSaveToggle(): void {
    this.saveToggle.emit(this.job());
  }

  onInterestedToggle(): void {
    this.interestedToggle.emit(this.job());
  }

  onHideRequested(): void {
    this.hideRequested.emit(this.job());
  }

  onUnhideRequested(): void {
    this.unhideRequested.emit(this.job());
  }

  onApplyRequested(): void {
    this.applyRequested.emit(this.job());
  }
}

