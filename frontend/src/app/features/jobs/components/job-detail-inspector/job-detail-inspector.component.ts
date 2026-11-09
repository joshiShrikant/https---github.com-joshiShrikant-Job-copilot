import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  JobAiAnalysis,
  JobDetail,
  JobSummary,
  formatEmploymentTypeLabel,
  formatExperienceLevelLabel,
  formatSponsorshipRecordStatusLabel,
  formatWorkArrangementLabel
} from '../../../../core/models/job.models';
import { MatchScoreGaugeComponent } from '../../../../shared/components/match-score-gauge/match-score-gauge.component';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';
import { SalaryRangePipe } from '../../../../shared/pipes/salary-range.pipe';
import { TimeAgoPipe } from '../../../../shared/pipes/time-ago.pipe';
import { JobAssistantPanelComponent } from '../job-assistant-panel/job-assistant-panel.component';

interface ScoreDimensionBar {
  readonly label: string;
  readonly value: number;
}

@Component({
  selector: 'app-job-detail-inspector',
  standalone: true,
  imports: [
    RouterLink,
    StatusBadgeComponent,
    MatchScoreGaugeComponent,
    SalaryRangePipe,
    TimeAgoPipe,
    JobAssistantPanelComponent
  ],
  templateUrl: './job-detail-inspector.component.html',
  styleUrl: './job-detail-inspector.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JobDetailInspectorComponent {
  readonly summaryJob = input<JobSummary | null>(null);
  readonly detail = input<JobDetail | null>(null);
  readonly loading = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);
  readonly actionBusy = input<boolean>(false);
  readonly matchBusy = input<boolean>(false);
  readonly aiMatchBusy = input<boolean>(false);
  readonly aiAnalysisBusy = input<boolean>(false);
  readonly aiAnalysisHistory = input<readonly JobAiAnalysis[]>([]);
  readonly aiHistoryLoading = input<boolean>(false);
  readonly aiHistoryOpen = input<boolean>(false);

  readonly retryDetail = output<void>();
  readonly saveToggle = output<void>();
  readonly interestedToggle = output<void>();
  readonly hideToggle = output<void>();
  readonly applyRequested = output<void>();
  readonly calculateMatchRequested = output<void>();
  readonly evaluateAiMatchRequested = output<void>();
  readonly analyzeJdRequested = output<void>();
  readonly toggleAiHistoryRequested = output<void>();

  readonly effectiveTitle = computed(() => this.detail()?.title ?? this.summaryJob()?.title ?? '');
  readonly effectiveCompany = computed(
    () => this.detail()?.companyName ?? this.summaryJob()?.companyName ?? ''
  );
  readonly effectiveLocation = computed(() => {
    const d = this.detail();
    const s = this.summaryJob();
    const city = d?.city ?? s?.city;
    const country = d?.country ?? s?.country ?? '';
    if (city && country) {
      return `${city}, ${country}`;
    }
    return d?.location ?? s?.location ?? country;
  });

  readonly effectiveSaved = computed(
    () => this.detail()?.isSaved ?? this.summaryJob()?.isSaved ?? false
  );
  readonly effectiveInterested = computed(
    () => this.detail()?.isInterested ?? this.summaryJob()?.isInterested ?? false
  );
  readonly effectiveHidden = computed(
    () => this.detail()?.isHidden ?? this.summaryJob()?.isHidden ?? false
  );
  readonly effectiveHasApplied = computed(() => Boolean(this.detail()?.hasApplied));

  readonly employmentSummary = computed(() => {
    const emp = this.detail()?.employmentType ?? this.summaryJob()?.employmentType;
    const exp = this.detail()?.experienceLevel ?? this.summaryJob()?.experienceLevel;
    const parts: string[] = [];
    if (emp) {
      parts.push(formatEmploymentTypeLabel(emp));
    }
    if (exp) {
      parts.push(formatExperienceLevelLabel(exp));
    }
    return parts.join(' · ');
  });

  readonly safeOriginalUrl = computed<string | null>(() => {
    const raw = this.detail()?.originalUrl ?? this.summaryJob()?.originalUrl;
    return this.isSafeHttpUrl(raw) ? raw!.trim() : null;
  });

  readonly matchDimensionBars = computed<readonly ScoreDimensionBar[]>(() => {
    const mb = this.detail()?.matchBreakdown;
    if (!mb) {
      return [];
    }
    const entries: Array<[string, number | null | undefined]> = [
      ['Technical Skills (40%)', mb.skillScore],
      ['Experience (20%)', mb.experienceScore],
      ['Job Title (15%)', mb.titleScore],
      ['Location (10%)', mb.locationScore],
      ['Visa Sponsorship (10%)', mb.sponsorshipScore],
      ['Salary Fit (5%)', mb.salaryScore]
    ];
    return entries
      .filter((entry): entry is [string, number] => typeof entry[1] === 'number' && Number.isFinite(entry[1]))
      .map(([label, value]) => ({
        label,
        value: Math.max(0, Math.min(100, Math.round(value)))
      }));
  });

  readonly displaySkills = computed<readonly string[]>(() => {
    const d = this.detail();
    if (d?.skills?.length) {
      return d.skills;
    }
    return this.summaryJob()?.skills ?? [];
  });

  readonly hasAiSemanticMatch = computed<boolean>(() => {
    const mb = this.detail()?.matchBreakdown;
    if (!mb) {
      return false;
    }
    return Boolean(
      mb.aiEvaluatedAt ||
        (mb.aiScore !== null && mb.aiScore !== undefined) ||
        mb.aiRationale ||
        (mb.aiStrongMatches?.length ?? 0) > 0 ||
        (mb.aiMissingSkills?.length ?? 0) > 0 ||
        (mb.aiExperienceGaps?.length ?? 0) > 0 ||
        (mb.aiPotentialConcerns?.length ?? 0) > 0 ||
        (mb.aiImprovementSuggestions?.length ?? 0) > 0
    );
  });

  formatRegistryStatus(status: Parameters<typeof formatSponsorshipRecordStatusLabel>[0]): string {
    return formatSponsorshipRecordStatusLabel(status);
  }

  formatAiLocation(ai: JobAiAnalysis): string {
    if (ai.extractedLocation && ai.extractedCountry) {
      const lowerLoc = ai.extractedLocation.toLowerCase();
      const lowerCountry = ai.extractedCountry.toLowerCase();
      if (lowerLoc.includes(lowerCountry)) {
        return ai.extractedLocation;
      }
      return `${ai.extractedLocation}, ${ai.extractedCountry}`;
    }
    return ai.extractedLocation ?? ai.extractedCountry ?? '';
  }

  formatAiExperience(ai: JobAiAnalysis): string {
    if (ai.requiredExperienceSummary) {
      return ai.requiredExperienceSummary;
    }
    const parts: string[] = [];
    if (ai.requiredExperienceYears !== null && ai.requiredExperienceYears !== undefined) {
      parts.push(`${ai.requiredExperienceYears}+ yrs`);
    }
    if (ai.requiredExperienceLevel) {
      const level = formatExperienceLevelLabel(ai.requiredExperienceLevel);
      if (level) {
        parts.push(level);
      }
    }
    return parts.join(' · ');
  }

  formatAiWorkAndContract(ai: JobAiAnalysis): string {
    const parts: string[] = [];
    if (ai.workArrangement) {
      parts.push(formatWorkArrangementLabel(ai.workArrangement));
    }
    if (ai.employmentType) {
      parts.push(formatEmploymentTypeLabel(ai.employmentType));
    }
    return parts.join(' · ');
  }

  hasAiCompensation(ai: JobAiAnalysis): boolean {
    return (
      (ai.salaryMin !== null && ai.salaryMin !== undefined) ||
      (ai.salaryMax !== null && ai.salaryMax !== undefined)
    );
  }

  formatTriStateBadge(
    offered: boolean | null | undefined,
    yesText: string,
    noText: string,
    unknownText: string
  ): string {
    if (offered === true) {
      return yesText;
    }
    if (offered === false) {
      return noText;
    }
    return unknownText;
  }

  isSafeHttpUrl(raw: string | null | undefined): boolean {
    if (!raw || !raw.trim()) {
      return false;
    }
    try {
      const parsed = new URL(raw.trim());
      return (
        (parsed.protocol === 'https:' || parsed.protocol === 'http:') &&
        parsed.hostname.trim().length > 0
      );
    } catch {
      return false;
    }
  }
}

