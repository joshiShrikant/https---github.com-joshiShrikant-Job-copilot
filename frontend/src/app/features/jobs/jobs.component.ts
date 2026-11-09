import { DOCUMENT } from '@angular/common';
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
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, finalize, of, switchMap, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { extractApiErrorMessage } from '../../core/models/api.models';
import {
  EmploymentType,
  ExperienceLevel,
  JobAiAnalysis,
  JobSearchFilters,
  JobSortOption,
  JobSponsorStatus,
  JobSummary,
  JobSummaryPageResponse,
  JobWorkArrangement,
  countActiveJobFilters,
  createDefaultJobSearchFilters
} from '../../core/models/job.models';
import { JobService } from '../../core/services/job.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { JobCardComponent } from '../../shared/components/job-card/job-card.component';
import { JobFilterBarComponent } from '../../shared/components/job-filter-bar/job-filter-bar.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { JobDetailInspectorComponent } from './components/job-detail-inspector/job-detail-inspector.component';

interface SearchTrigger {
  readonly page: number;
  readonly size: number;
}

interface PendingAppliedConfirmation {
  readonly id: number;
  readonly title: string;
  readonly targetUrl: string;
}

@Component({
  selector: 'app-jobs',
  standalone: true,
  imports: [
    RouterLink,
    PageHeaderComponent,
    JobFilterBarComponent,
    JobCardComponent,
    JobDetailInspectorComponent,
    ConfirmDialogComponent
  ],
  templateUrl: './jobs.component.html',
  styleUrl: './jobs.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JobsComponent implements OnInit {
  private readonly jobService = inject(JobService);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);

  private readonly searchTrigger$ = new Subject<SearchTrigger>();
  private readonly detailTrigger$ = new Subject<number | null>();
  private hasPendingDeepLinkJobId = false;

  readonly searchResult = this.jobService.searchResult;
  readonly jobs = this.jobService.jobs;
  readonly selectedJobDetail = this.jobService.selectedJobDetail;
  readonly isSearching = this.jobService.isSearching;
  readonly isLoadingDetail = this.jobService.isLoadingDetail;
  readonly pendingInteractionJobIds = this.jobService.pendingInteractionJobIds;
  readonly pendingMatchJobIds = this.jobService.pendingMatchJobIds;
  readonly pendingAiMatchJobIds = this.jobService.pendingAiMatchJobIds;
  readonly pendingAiAnalysisJobIds = this.jobService.pendingAiAnalysisJobIds;
  readonly isRecalculatingMatches = this.jobService.isRecalculatingMatches;

  readonly filters = signal<JobSearchFilters>(createDefaultJobSearchFilters());
  readonly page = signal<number>(0);
  readonly pageSize = signal<number>(environment.defaultPageSize);
  readonly compactCards = signal<boolean>(false);
  readonly selectedJobId = signal<number | null>(null);

  readonly searchErrorMessage = signal<string | null>(null);
  readonly detailErrorMessage = signal<string | null>(null);
  readonly actionErrorMessage = signal<string | null>(null);
  readonly interactionNotice = signal<string>('');

  readonly aiAnalysisHistory = signal<readonly JobAiAnalysis[]>([]);
  readonly aiHistoryLoading = signal<boolean>(false);
  readonly aiHistoryOpen = signal<boolean>(false);

  readonly pendingHideJob = signal<JobSummary | null>(null);
  readonly hideDialogBusy = signal<boolean>(false);
  readonly pendingAppliedPrompt = signal<PendingAppliedConfirmation | null>(null);

  readonly activeFilterCount = computed<number>(() => countActiveJobFilters(this.filters()));

  readonly hideDialogMessage = computed<string>(() => {
    const title = this.pendingHideJob()?.title ?? 'this job';
    return `You are about to hide ${title} from default discovery results. Toggle "Include hidden" anytime to restore it.`;
  });

  readonly headerBadge = computed<string>(() => {
    const res = this.searchResult();
    if (!res) {
      return 'Live Discovery';
    }
    return `${res.totalElements} ${res.totalElements === 1 ? 'Role' : 'Roles'} Indexed`;
  });

  readonly selectedSummaryJob = computed<JobSummary | null>(() => {
    const list = this.jobs();
    const id = this.selectedJobId();
    if (id !== null) {
      const fromList = list.find(item => item.id === id);
      if (fromList) {
        return fromList;
      }
      const detail = this.selectedJobDetail();
      if (detail && detail.id === id) {
        return {
          id: detail.id,
          title: detail.title,
          companyName: detail.companyName,
          location: detail.location,
          city: detail.city ?? null,
          country: detail.country,
          workArrangement: detail.workArrangement,
          employmentType: detail.employmentType,
          experienceLevel: detail.experienceLevel ?? null,
          salaryMin: detail.salaryMin,
          salaryMax: detail.salaryMax,
          salaryCurrency: detail.salaryCurrency,
          salaryPeriod: detail.salaryPeriod ?? null,
          sponsorStatus: detail.sponsorStatus,
          matchPercentage: detail.matchPercentage ?? null,
          sourceProvider: detail.sourceProvider ?? 'INDEXED',
          postedAt: detail.postedAt,
          isSaved: Boolean(detail.isSaved),
          isInterested: Boolean(detail.isInterested),
          isHidden: Boolean(detail.isHidden),
          skills: detail.skills ?? [],
          descriptionSnippet: detail.descriptionSnippet ?? null,
          originalUrl: detail.originalUrl ?? null
        };
      }
    }
    return list[0] ?? null;
  });

  readonly showingRangeText = computed<string>(() => {
    const res = this.searchResult();
    if (!res || res.totalElements === 0) {
      return 'Showing 0 roles';
    }
    const start = res.pageNumber * res.pageSize + 1;
    const end = Math.min(res.totalElements, start + res.content.length - 1);
    return `Showing ${start}–${end} of ${res.totalElements} roles`;
  });

  readonly visiblePageNumbers = computed<readonly number[]>(() => {
    const res = this.searchResult();
    if (!res || res.totalPages <= 1) {
      return [];
    }
    const total = res.totalPages;
    const current = res.pageNumber;
    const maxButtons = 5;
    let start = Math.max(0, current - 2);
    const end = Math.min(total - 1, start + maxButtons - 1);
    start = Math.max(0, end - maxButtons + 1);

    const pages: number[] = [];
    for (let p = start; p <= end; p++) {
      pages.push(p);
    }
    return pages;
  });

  ngOnInit(): void {
    this.searchTrigger$
      .pipe(
        tap(() => this.searchErrorMessage.set(null)),
        switchMap(trigger =>
          this.jobService.searchJobs(this.filters(), trigger.page, trigger.size).pipe(
            catchError((err: unknown) => {
              this.searchErrorMessage.set(
                extractApiErrorMessage(err, 'Unable to load job listings right now.')
              );
              return of<JobSummaryPageResponse | null>(null);
            })
          )
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(response => {
        if (!response) {
          return;
        }
        this.reconcileSelectionAfterSearch(response);
      });

    this.detailTrigger$
      .pipe(
        tap(() => {
          this.detailErrorMessage.set(null);
          this.aiAnalysisHistory.set([]);
          this.aiHistoryOpen.set(false);
          this.aiHistoryLoading.set(false);
        }),
        switchMap(jobId => {
          if (jobId === null) {
            this.jobService.clearSelectedDetail();
            return of(null);
          }
          return this.jobService.getJobDetail(jobId).pipe(
            catchError((err: unknown) => {
              this.detailErrorMessage.set(
                extractApiErrorMessage(err, 'Unable to load full details for this job.')
              );
              return of(null);
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();

    this.applyInitialQueryParams();
    this.triggerSearch();
  }

  triggerSearch(): void {
    this.searchTrigger$.next({
      page: this.page(),
      size: this.pageSize()
    });
  }

  onFiltersChange(nextFilters: JobSearchFilters): void {
    this.filters.set(nextFilters);
    this.page.set(0);
    this.actionErrorMessage.set(null);
    this.triggerSearch();
  }

  onResetFilters(): void {
    this.filters.set(createDefaultJobSearchFilters());
    this.page.set(0);
    this.actionErrorMessage.set(null);
    this.triggerSearch();
  }

  toggleCompactCards(): void {
    this.compactCards.update(current => !current);
  }

  onPageChange(nextPage: number): void {
    const res = this.searchResult();
    if (nextPage < 0 || (res && nextPage >= res.totalPages)) {
      return;
    }
    this.page.set(nextPage);
    this.triggerSearch();
  }

  onPageSizeChange(event: Event): void {
    const parsed = Number((event.target as HTMLSelectElement).value);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return;
    }
    this.pageSize.set(parsed);
    this.page.set(0);
    this.triggerSearch();
  }

  onInspectJob(job: JobSummary): void {
    this.selectedJobId.set(job.id);
    this.detailErrorMessage.set(null);
    this.actionErrorMessage.set(null);
    this.interactionNotice.set('');
    this.detailTrigger$.next(job.id);
  }

  retrySelectedDetail(): void {
    const currentId = this.selectedSummaryJob()?.id ?? this.selectedJobId();
    if (currentId !== null) {
      this.detailTrigger$.next(currentId);
    }
  }

  onSaveToggle(job: JobSummary): void {
    const nextSave = !job.isSaved;
    this.actionErrorMessage.set(null);

    this.jobService
      .setSaved(job.id, nextSave)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.interactionNotice.set(
            nextSave
              ? `Saved "${job.title}" to your bookmarks.`
              : `Removed "${job.title}" from saved jobs.`
          );
          if (this.filters().savedOnly && !nextSave) {
            this.refreshAfterListItemRemoval();
          }
        },
        error: (err: unknown) => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to update saved bookmark status.')
          );
        }
      });
  }

  onInterestedToggle(job: JobSummary): void {
    const nextInterested = !job.isInterested;
    this.actionErrorMessage.set(null);

    this.jobService
      .setInterested(job.id, nextInterested)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.interactionNotice.set(
            nextInterested
              ? `Marked "${job.title}" as strong fit.`
              : `Cleared interested flag for "${job.title}".`
          );
          if (this.filters().interestedOnly && !nextInterested) {
            this.refreshAfterListItemRemoval();
          }
        },
        error: (err: unknown) => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to update interested status.')
          );
        }
      });
  }

  onHideRequested(job: JobSummary): void {
    this.pendingHideJob.set(job);
  }

  confirmHideJob(): void {
    const target = this.pendingHideJob();
    if (!target || this.hideDialogBusy()) {
      return;
    }

    this.hideDialogBusy.set(true);
    this.actionErrorMessage.set(null);

    this.jobService
      .setHidden(target.id, true)
      .pipe(
        finalize(() => {
          this.hideDialogBusy.set(false);
          this.pendingHideJob.set(null);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.interactionNotice.set(`Hid "${target.title}" from your discovery feed.`);
          if (!this.filters().includeHidden) {
            this.refreshAfterListItemRemoval();
          }
        },
        error: (err: unknown) => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to hide this job listing.')
          );
        }
      });
  }

  cancelHideJob(): void {
    this.pendingHideJob.set(null);
  }

  onUnhideRequested(job: JobSummary): void {
    this.actionErrorMessage.set(null);

    this.jobService
      .setHidden(job.id, false)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.interactionNotice.set(`Restored "${job.title}" to your default job feed.`);
        },
        error: (err: unknown) => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to restore hidden job listing.')
          );
        }
      });
  }

  onInspectorHideToggle(): void {
    const summary = this.selectedSummaryJob();
    if (!summary) {
      return;
    }
    const currentlyHidden = this.selectedJobDetail()?.isHidden ?? summary.isHidden ?? false;
    if (currentlyHidden) {
      this.onUnhideRequested(summary);
    } else {
      this.onHideRequested(summary);
    }
  }

  onApplyRequested(job: JobSummary): void {
    this.actionErrorMessage.set(null);

    this.jobService
      .trackApplyRedirect(job.id, { markApplied: false })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: res => {
          if (this.jobService.isSafeExternalUrl(res.targetUrl)) {
            this.document.defaultView?.open(res.targetUrl, '_blank', 'noopener,noreferrer');
          }
          if (!res.hasApplied) {
            this.pendingAppliedPrompt.set({
              id: job.id,
              title: job.title,
              targetUrl: res.targetUrl
            });
          } else {
            this.interactionNotice.set(`Opened application link for "${job.title}".`);
          }
        },
        error: (err: unknown) => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to open external application link for this job.')
          );
        }
      });
  }

  confirmMarkApplied(): void {
    const pending = this.pendingAppliedPrompt();
    if (!pending) {
      return;
    }

    this.actionErrorMessage.set(null);
    this.jobService
      .trackApplyRedirect(pending.id, { markApplied: true })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.pendingAppliedPrompt.set(null);
          this.interactionNotice.set(`Marked "${pending.title}" as applied.`);
        },
        error: (err: unknown) => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to mark application as submitted.')
          );
        }
      });
  }

  dismissAppliedPrompt(): void {
    this.pendingAppliedPrompt.set(null);
  }

  isCardBusy(jobId: number): boolean {
    return this.pendingInteractionJobIds().has(jobId);
  }

  isMatchBusy(jobId: number): boolean {
    return this.pendingMatchJobIds().has(jobId);
  }

  isAiMatchBusy(jobId: number): boolean {
    return this.pendingAiMatchJobIds().has(jobId);
  }

  isAiAnalysisBusy(jobId: number): boolean {
    return this.pendingAiAnalysisJobIds().has(jobId);
  }

  onAnalyzeSelectedJobJd(): void {
    const activeId = this.selectedJobDetail()?.id ?? this.selectedSummaryJob()?.id;
    if (!activeId || this.isAiAnalysisBusy(activeId)) {
      return;
    }
    this.actionErrorMessage.set(null);

    this.jobService
      .analyzeJobDescription(activeId, true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: analysis => {
          const version = analysis.analysisVersion ?? 1;
          this.interactionNotice.set(
            `AI extracted structured role requirements (v${version}).`
          );
          if (this.aiHistoryOpen() || this.aiAnalysisHistory().length > 0) {
            this.aiAnalysisHistory.update(prev => [
              analysis,
              ...prev.filter(item => item.analysisVersion !== analysis.analysisVersion)
            ]);
          }
        },
        error: (err: unknown) => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to analyze job description with AI right now.')
          );
        }
      });
  }

  onToggleAiHistory(): void {
    const activeId = this.selectedJobDetail()?.id ?? this.selectedSummaryJob()?.id;
    if (!activeId) {
      return;
    }
    const nextOpen = !this.aiHistoryOpen();
    this.aiHistoryOpen.set(nextOpen);
    if (!nextOpen || this.aiHistoryLoading()) {
      return;
    }

    this.actionErrorMessage.set(null);
    this.aiHistoryLoading.set(true);
    this.jobService
      .getJobAiAnalysisHistory(activeId)
      .pipe(
        finalize(() => this.aiHistoryLoading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: history => {
          this.aiAnalysisHistory.set(history);
        },
        error: (err: unknown) => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to load AI analysis version history.')
          );
        }
      });
  }

  onCalculateSelectedJobMatch(): void {
    const activeId = this.selectedJobDetail()?.id ?? this.selectedSummaryJob()?.id;
    if (!activeId) {
      return;
    }
    this.actionErrorMessage.set(null);

    this.jobService
      .calculateJobMatch(activeId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: breakdown => {
          this.interactionNotice.set(
            `Computed ${breakdown.matchPercentage ?? 0}% deterministic match for this role.`
          );
        },
        error: (err: unknown) => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to compute match score for this job.')
          );
        }
      });
  }

  onEvaluateSelectedJobAiMatch(): void {
    const activeId = this.selectedJobDetail()?.id ?? this.selectedSummaryJob()?.id;
    if (!activeId || this.isAiMatchBusy(activeId)) {
      return;
    }
    this.actionErrorMessage.set(null);

    this.jobService
      .evaluateAiJobMatch(activeId, true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: breakdown => {
          const aiScore = breakdown.aiScore ?? breakdown.matchPercentage ?? 0;
          const overallScore = breakdown.matchPercentage ?? 0;
          this.interactionNotice.set(
            `Evaluated ${aiScore}% AI semantic match (overall ${overallScore}%).`
          );
        },
        error: (err: unknown) => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to evaluate AI semantic job match right now.')
          );
        }
      });
  }

  onRecalculateAllMatches(): void {
    if (this.isRecalculatingMatches()) {
      return;
    }
    this.actionErrorMessage.set(null);

    this.jobService
      .recalculateAllMatches()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: summary => {
          this.interactionNotice.set(
            `Scored ${summary.evaluatedJobsCount} active roles (${summary.highMatchJobsCount} at ≥ ${summary.minMatchThreshold}% match).`
          );
          this.triggerSearch();
          const currentDetailId = this.selectedJobDetail()?.id ?? this.selectedSummaryJob()?.id;
          if (currentDetailId !== null && currentDetailId !== undefined) {
            this.detailTrigger$.next(currentDetailId);
          }
        },
        error: (err: unknown) => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to recalculate job match scores right now.')
          );
        }
      });
  }

  private reconcileSelectionAfterSearch(response: JobSummaryPageResponse): void {
    const existingId = this.selectedJobId();
    if (this.hasPendingDeepLinkJobId && existingId !== null) {
      this.hasPendingDeepLinkJobId = false;
      const loadedDetail = this.selectedJobDetail();
      if (!loadedDetail || loadedDetail.id !== existingId) {
        this.detailTrigger$.next(existingId);
      }
      return;
    }

    if (response.content.length === 0) {
      this.selectedJobId.set(null);
      this.detailTrigger$.next(null);
      return;
    }

    const stillVisible = existingId !== null && response.content.some(item => item.id === existingId);
    const nextId = stillVisible ? existingId : response.content[0].id;
    this.selectedJobId.set(nextId);

    const loadedDetail = this.selectedJobDetail();
    if (!loadedDetail || loadedDetail.id !== nextId) {
      this.detailTrigger$.next(nextId);
    }
  }

  private refreshAfterListItemRemoval(): void {
    const currentCount = this.jobs().length;
    if (currentCount <= 1 && this.page() > 0) {
      this.page.update(p => Math.max(0, p - 1));
    }
    this.triggerSearch();
  }

  private applyInitialQueryParams(): void {
    const params = this.route?.snapshot?.queryParamMap;
    if (!params) {
      return;
    }

    const deepJobIdRaw = params.get('jobId');
    const parsedJobId = deepJobIdRaw ? Number(deepJobIdRaw) : null;
    if (parsedJobId !== null && Number.isFinite(parsedJobId) && parsedJobId > 0) {
      this.selectedJobId.set(Math.trunc(parsedJobId));
      this.hasPendingDeepLinkJobId = true;
    }

    const base = createDefaultJobSearchFilters();
    const minMatchRaw = params.get('minMatchScore');
    const minSalaryRaw = params.get('minSalary');
    const parsedMatch = minMatchRaw ? Number(minMatchRaw) : null;
    const parsedSalary = minSalaryRaw ? Number(minSalaryRaw) : null;
    const rawSort = params.get('sort') as JobSortOption | null;
    const validSort: JobSortOption =
      rawSort === 'postedAt,desc' ||
      rawSort === 'postedAt,asc' ||
      rawSort === 'matchScore,desc' ||
      rawSort === 'salaryMax,desc'
        ? rawSort
        : base.sort;

    const workArrangement = params.get('workArrangement');
    const validArrangement: JobSearchFilters['workArrangement'] =
      workArrangement === 'REMOTE' ||
      workArrangement === 'HYBRID' ||
      workArrangement === 'ONSITE'
        ? (workArrangement as Exclude<JobWorkArrangement, 'ANY'>)
        : '';

    this.filters.set({
      ...base,
      query: params.get('query') ?? base.query,
      country: params.get('country') ?? base.country,
      city: params.get('city') ?? base.city,
      workArrangement: validArrangement,
      employmentType: (params.get('employmentType') as EmploymentType | null) ?? base.employmentType,
      experienceLevel: (params.get('experienceLevel') as ExperienceLevel | null) ?? base.experienceLevel,
      sponsorStatus: (params.get('sponsorStatus') as JobSponsorStatus | null) ?? base.sponsorStatus,
      minMatchScore: parsedMatch !== null && Number.isFinite(parsedMatch) ? parsedMatch : null,
      minSalary: parsedSalary !== null && Number.isFinite(parsedSalary) ? parsedSalary : null,
      savedOnly: params.get('savedOnly') === 'true',
      interestedOnly: params.get('interestedOnly') === 'true',
      includeHidden: params.get('includeHidden') === 'true',
      sort: validSort
    });
  }
}
