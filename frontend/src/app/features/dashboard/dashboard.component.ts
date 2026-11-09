import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { extractApiErrorMessage } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PipelineFunnelCardComponent } from './components/pipeline-funnel-card/pipeline-funnel-card.component';
import { ReadinessChecklistCardComponent } from './components/readiness-checklist-card/readiness-checklist-card.component';
import { RecentActivityCardComponent } from './components/recent-activity-card/recent-activity-card.component';

interface KpiCard {
  readonly id: string;
  readonly label: string;
  readonly value: number;
  readonly subtext: string;
  readonly icon: string;
  readonly route: string;
  readonly accent?: 'brand' | 'success' | 'neutral';
}

interface QuickShortcut {
  readonly label: string;
  readonly description: string;
  readonly icon: string;
  readonly route: string;
}

const QUICK_SHORTCUTS: readonly QuickShortcut[] = [
  {
    label: 'Discover Jobs',
    description: 'Browse indexed European roles',
    icon: 'travel_explore',
    route: '/jobs'
  },
  {
    label: 'Visa Sponsorship',
    description: 'Verify sponsor employers',
    icon: 'verified_user',
    route: '/sponsorship'
  },
  {
    label: 'Candidate Profile',
    description: 'Skills & EU work authorization',
    icon: 'person',
    route: '/profile'
  },
  {
    label: 'Master Resumes',
    description: 'Upload & approve base CVs',
    icon: 'description',
    route: '/resumes'
  }
];

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    RouterLink,
    PageHeaderComponent,
    ReadinessChecklistCardComponent,
    PipelineFunnelCardComponent,
    RecentActivityCardComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly summary = this.dashboardService.summary;
  readonly isLoading = this.dashboardService.isLoading;
  readonly loadErrorMessage = signal<string | null>(null);
  readonly shortcuts = QUICK_SHORTCUTS;

  readonly subtitleText = computed(() => {
    const user = this.authService.currentUser();
    if (user) {
      const name = `${user.firstName} ${user.lastName}`.trim() || user.email;
      return `Welcome back, ${name} — track job discovery, visa sponsorship readiness, and active applications.`;
    }
    return 'Monitor European job discovery, sponsorship opportunities, candidate readiness, and active pipeline metrics.';
  });

  readonly kpiCards = computed<readonly KpiCard[]>(() => {
    const s = this.summary();
    if (!s) {
      return [];
    }
    return [
      {
        id: 'discovered',
        label: 'Jobs Discovered',
        value: s.totalJobsDiscovered,
        subtext: `+${s.newJobsToday} posted today`,
        icon: 'travel_explore',
        route: '/jobs',
        accent: 'brand'
      },
      {
        id: 'high-match',
        label: 'High-Match Roles',
        value: s.highMatchJobsCount,
        subtext: `≥ ${s.readiness.minMatchScore}% match threshold`,
        icon: 'auto_awesome',
        route: '/recommended-jobs',
        accent: 'success'
      },
      {
        id: 'sponsorship',
        label: 'Sponsor Opportunities',
        value: s.sponsorshipOpportunitiesCount,
        subtext: `${s.verifiedSponsorsCount} verified sponsors`,
        icon: 'verified_user',
        route: '/sponsorship',
        accent: 'brand'
      },
      {
        id: 'applications',
        label: 'Active Applications',
        value: s.activeApplicationsCount,
        subtext: `${s.savedJobsCount} saved · ${s.unreadNotificationsCount} unread alerts`,
        icon: 'view_kanban',
        route: '/applications',
        accent: 'neutral'
      }
    ];
  });

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loadErrorMessage.set(null);

    this.dashboardService
      .loadSummary()
      .pipe(
        catchError((err: unknown) => {
          this.loadErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to load executive dashboard telemetry.')
          );
          return of(null);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();
  }
}
