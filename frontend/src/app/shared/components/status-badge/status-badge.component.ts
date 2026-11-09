import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  ApplicationStatus,
  JobSponsorStatus,
  JobWorkArrangement,
  SponsorConfidenceLevel,
  formatWorkArrangementLabel
} from '../../../core/models/job.models';
import {
  VISA_SPONSORSHIP_STATUS_META,
  VisaSponsorshipStatus
} from '../../../core/models/sponsorship.models';

export type StatusBadgeTone = 'success' | 'brand' | 'warning' | 'danger' | 'neutral';

interface ResolvedBadgeConfig {
  readonly label: string;
  readonly tone: StatusBadgeTone;
  readonly icon: string;
}

const SPONSOR_STATUS_CONFIG: Record<JobSponsorStatus, ResolvedBadgeConfig> = {
  VERIFIED_SPONSOR: {
    label: 'Verified Sponsor',
    tone: 'success',
    icon: 'verified_user'
  },
  SPONSORSHIP_MENTIONED: {
    label: 'Visa Mentioned',
    tone: 'brand',
    icon: 'fact_check'
  },
  RELOCATION_OFFERED: {
    label: 'Relocation Offered',
    tone: 'brand',
    icon: 'flight_takeoff'
  },
  UNCONFIRMED: {
    label: 'Unconfirmed Sponsor',
    tone: 'neutral',
    icon: 'help_outline'
  },
  NO_SPONSORSHIP: {
    label: 'No Sponsorship',
    tone: 'danger',
    icon: 'block'
  }
};

const CONFIDENCE_LEVEL_CONFIG: Record<SponsorConfidenceLevel, ResolvedBadgeConfig> = {
  VERIFIED: { label: 'Verified Registry', tone: 'success', icon: 'verified' },
  HIGH: { label: 'High Confidence', tone: 'success', icon: 'shield' },
  MEDIUM: { label: 'Medium Confidence', tone: 'brand', icon: 'policy' },
  LOW: { label: 'Low Confidence', tone: 'warning', icon: 'warning' },
  UNVERIFIED: { label: 'Unverified', tone: 'neutral', icon: 'help_outline' }
};

const APPLICATION_STATUS_CONFIG: Record<ApplicationStatus, ResolvedBadgeConfig> = {
  INTERESTED: { label: 'Interested', tone: 'brand', icon: 'favorite' },
  SAVED: { label: 'Saved', tone: 'neutral', icon: 'bookmark' },
  APPLIED: { label: 'Applied', tone: 'brand', icon: 'send' },
  ASSESSMENT: { label: 'Assessment', tone: 'warning', icon: 'assignment' },
  TECH_INTERVIEW: { label: 'Tech Interview', tone: 'warning', icon: 'code' },
  HR_INTERVIEW: { label: 'HR Interview', tone: 'warning', icon: 'forum' },
  FINAL_INTERVIEW: { label: 'Final Round', tone: 'brand', icon: 'groups' },
  OFFER: { label: 'Offer', tone: 'success', icon: 'workspace_premium' },
  REJECTED: { label: 'Rejected', tone: 'danger', icon: 'close' },
  WITHDRAWN: { label: 'Withdrawn', tone: 'neutral', icon: 'undo' }
};

const WORK_ARRANGEMENT_ICONS: Record<JobWorkArrangement, string> = {
  REMOTE: 'home_work',
  HYBRID: 'domain',
  ONSITE: 'apartment',
  ANY: 'public'
};

@Component({
  selector: 'app-status-badge',
  standalone: true,
  templateUrl: './status-badge.component.html',
  styleUrl: './status-badge.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StatusBadgeComponent {
  readonly sponsorStatus = input<JobSponsorStatus | null>(null);
  readonly visaStatus = input<VisaSponsorshipStatus | null>(null);
  readonly confidenceLevel = input<SponsorConfidenceLevel | null>(null);
  readonly applicationStatus = input<ApplicationStatus | null>(null);
  readonly workArrangement = input<JobWorkArrangement | null>(null);
  readonly label = input<string | null>(null);
  readonly tone = input<StatusBadgeTone | null>(null);
  readonly icon = input<string | null>(null);
  readonly size = input<'sm' | 'md'>('sm');
  readonly tooltip = input<string>('');

  readonly resolved = computed<ResolvedBadgeConfig>(() => {
    const detailedVisa = this.visaStatus();
    if (detailedVisa) {
      const meta = VISA_SPONSORSHIP_STATUS_META[detailedVisa];
      if (meta) {
        return this.applyOverrides({
          label: meta.label,
          tone: meta.tone,
          icon: meta.icon
        });
      }
    }

    const sponsor = this.sponsorStatus();
    if (sponsor) {
      const base = SPONSOR_STATUS_CONFIG[sponsor];
      return this.applyOverrides(base);
    }

    const confidence = this.confidenceLevel();
    if (confidence) {
      const base = CONFIDENCE_LEVEL_CONFIG[confidence];
      return this.applyOverrides(base);
    }

    const appStatus = this.applicationStatus();
    if (appStatus) {
      const base = APPLICATION_STATUS_CONFIG[appStatus];
      return this.applyOverrides(base);
    }

    const arrangement = this.workArrangement();
    if (arrangement) {
      const base: ResolvedBadgeConfig = {
        label: formatWorkArrangementLabel(arrangement),
        tone: arrangement === 'REMOTE' ? 'brand' : 'neutral',
        icon: WORK_ARRANGEMENT_ICONS[arrangement]
      };
      return this.applyOverrides(base);
    }

    return {
      label: this.label() ?? 'Status',
      tone: this.tone() ?? 'neutral',
      icon: this.icon() ?? ''
    };
  });

  private applyOverrides(base: ResolvedBadgeConfig): ResolvedBadgeConfig {
    return {
      label: this.label() ?? base.label,
      tone: this.tone() ?? base.tone,
      icon: this.icon() !== null ? (this.icon() as string) : base.icon
    };
  }
}

