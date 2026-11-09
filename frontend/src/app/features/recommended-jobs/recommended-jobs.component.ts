import { DatePipe } from '@angular/common';
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
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, of } from 'rxjs';
import { extractApiErrorMessage } from '../../core/models/api.models';
import {
  AutopilotStatusResponse,
  PagedAutopilotHistoryResponse,
  PagedRecommendedJobsResponse,
  RecommendedJobItem
} from '../../core/models/autopilot.models';
import { AutopilotService } from '../../core/services/autopilot.service';
import { JobService } from '../../core/services/job.service';
import { NotificationService } from '../../core/services/notification.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { SalaryRangePipe } from '../../shared/pipes/salary-range.pipe';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'app-recommended-jobs',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    PageHeaderComponent,
    StatusBadgeComponent,
    SalaryRangePipe,
    TimeAgoPipe
  ],
  templateUrl: './recommended-jobs.component.html',
  styleUrl: './recommended-jobs.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RecommendedJobsComponent implements OnInit {
  private readonly autopilotService = inject(AutopilotService);
  private readonly jobService = inject(JobService);
  private readonly notificationService = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly status = signal<AutopilotStatusResponse | null>(null);
  readonly recommendedPage = signal<PagedRecommendedJobsResponse | null>(null);
  readonly historyPage = signal<PagedAutopilotHistoryResponse | null>(null);

  readonly loading = signal<boolean>(false);
  readonly scanning = signal<boolean>(false);
  readonly togglingPause = signal<boolean>(false);
  readonly savingJobIds = signal<ReadonlySet<number>>(new Set());
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);

  // Scan options & list filters
  readonly refreshExternalJobs = signal<boolean>(false);
  readonly analyzeTopMatchesWithAi = signal<boolean>(false);
  readonly minMatchScoreFilter = signal<string>('');
  readonly verifiedSponsorsOnly = signal<boolean>(false);
  readonly excludeApplied = signal<boolean>(false);
  readonly page = signal<number>(0);

  readonly headerBadge = computed<string>(() => {
    const s = this.status();
    if (!s) {
      return 'Autopilot Feed';
    }
    if (s.autopilotPaused) {
      return `Paused · ${s.recommendedJobsCount} Matches`;
    }
    return s.autopilotEnabled
      ? `Autopilot Active · ≥ ${s.minMatchScore}%`
      : `Manual Mode · ≥ ${s.minMatchScore}%`;
  });

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      status: this.autopilotService.getStatus(),
      recommendations: this.autopilotService.getRecommendedJobs(this.buildFilterParams()),
      history: this.autopilotService.getHistory(0, 8)
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: res => {
          this.status.set(res.status);
          this.recommendedPage.set(res.recommendations);
          this.historyPage.set(res.history);
          this.notificationService.setUnreadCount(res.status.unreadNotificationsCount);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to load autopilot recommendations right now.')
          );
        }
      });
  }

  loadRecommendations(nextPage = this.page()): void {
    this.page.set(Math.max(0, nextPage));
    this.loading.set(true);
    this.errorMessage.set(null);

    this.autopilotService
      .getRecommendedJobs(this.buildFilterParams())
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: res => {
          this.recommendedPage.set(res);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to filter recommended roles right now.')
          );
        }
      });
  }

  runAutopilotScan(): void {
    if (this.scanning()) {
      return;
    }
    this.scanning.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);

    this.autopilotService
      .runScan({
        refreshExternalJobs: this.refreshExternalJobs(),
        analyzeTopMatchesWithAi: this.analyzeTopMatchesWithAi()
      })
      .pipe(
        finalize(() => this.scanning.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: runLog => {
          this.noticeMessage.set(
            runLog.summaryMessage ||
              `Autopilot scan finished: evaluated ${runLog.jobsEvaluated} roles, created ${runLog.notificationsCreated} alert(s).`
          );
          this.page.set(0);
          this.loadAll();
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to execute manual autopilot scan.')
          );
        }
      });
  }

  togglePause(): void {
    const s = this.status();
    if (!s || this.togglingPause()) {
      return;
    }
    this.togglingPause.set(true);
    this.errorMessage.set(null);

    const req$ = s.autopilotPaused
      ? this.autopilotService.resume()
      : this.autopilotService.pause();

    req$
      .pipe(
        finalize(() => this.togglingPause.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: updated => {
          this.status.set(updated);
          this.noticeMessage.set(
            updated.autopilotPaused
              ? 'Scheduled autopilot scans paused. Your saved preferences remain intact.'
              : 'Scheduled autopilot scans resumed.'
          );
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to update autopilot pause state.')
          );
        }
      });
  }

  toggleSave(item: RecommendedJobItem): void {
    const job = item.job;
    if (this.savingJobIds().has(job.id)) {
      return;
    }

    this.savingJobIds.update(set => new Set(set).add(job.id));
    this.jobService
      .setSaved(job.id, !job.isSaved)
      .pipe(
        catchError((err: unknown) => {
          this.errorMessage.set(extractApiErrorMessage(err, 'Unable to toggle job bookmark.'));
          return of(null);
        }),
        finalize(() => {
          this.savingJobIds.update(set => {
            const next = new Set(set);
            next.delete(job.id);
            return next;
          });
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(res => {
        if (!res) {
          return;
        }
        this.recommendedPage.update(current => {
          if (!current) {
            return current;
          }
          return {
            ...current,
            content: current.content.map(entry =>
              entry.job.id === job.id
                ? { ...entry, job: { ...entry.job, isSaved: res.isSaved } }
                : entry
            )
          };
        });
      });
  }

  onFiltersChanged(): void {
    this.loadRecommendations(0);
  }

  private buildFilterParams() {
    const rawScore = String(this.minMatchScoreFilter() ?? '').trim();
    const parsedScore = rawScore ? Number(rawScore) : null;
    return {
      minMatchScore:
        parsedScore !== null && Number.isFinite(parsedScore)
          ? Math.max(0, Math.min(100, Math.trunc(parsedScore)))
          : null,
      verifiedSponsorsOnly: this.verifiedSponsorsOnly(),
      excludeApplied: this.excludeApplied(),
      page: this.page(),
      size: 20
    };
  }
}

