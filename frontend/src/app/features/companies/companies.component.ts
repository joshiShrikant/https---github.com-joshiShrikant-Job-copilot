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
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, finalize, forkJoin, of, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { extractApiErrorMessage } from '../../core/models/api.models';
import {
  COMPANY_PRIORITY_TIER_META,
  COMPANY_PRIORITY_TIER_OPTIONS,
  COMPANY_SORT_OPTIONS,
  CompanyDetail,
  CompanyPriorityTier,
  CompanySearchFilters,
  CompanySortOption,
  CompanySummary,
  PagedCompaniesResponse,
  countActiveCompanyFilters,
  createDefaultCompanySearchFilters,
  formatCompanyPriorityTierLabel,
  isSafeHttpUrl
} from '../../core/models/company.models';
import {
  EUROPEAN_COUNTRY_OPTIONS,
  SponsorshipRecordStatus,
  formatSponsorshipRecordStatusLabel
} from '../../core/models/job.models';
import { CompanyService } from '../../core/services/company.service';
import { MatchScoreGaugeComponent } from '../../shared/components/match-score-gauge/match-score-gauge.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { SalaryRangePipe } from '../../shared/pipes/salary-range.pipe';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';

interface DirectorySearchTrigger {
  readonly page: number;
  readonly preferredCompanyId: number | null;
}

@Component({
  selector: 'app-companies',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    PageHeaderComponent,
    StatusBadgeComponent,
    MatchScoreGaugeComponent,
    SalaryRangePipe,
    TimeAgoPipe
  ],
  templateUrl: './companies.component.html',
  styleUrl: './companies.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CompaniesComponent implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly destroyRef = inject(DestroyRef);

  private readonly searchTrigger$ = new Subject<DirectorySearchTrigger>();
  private readonly detailTrigger$ = new Subject<{ readonly id: number; readonly jobsPage: number }>();
  private readonly jobsPageTrigger$ = new Subject<{ readonly id: number; readonly page: number }>();

  readonly searchResult = this.companyService.searchResult;
  readonly companies = this.companyService.companies;
  readonly selectedCompany = this.companyService.selectedCompany;
  readonly companyJobsPage = this.companyService.companyJobsPage;
  readonly isSearching = this.companyService.isSearching;
  readonly isLoadingDetail = this.companyService.isLoadingDetail;
  readonly isLoadingJobs = this.companyService.isLoadingJobs;
  readonly pendingSaveCompanyIds = this.companyService.pendingSaveCompanyIds;

  readonly filters = signal<CompanySearchFilters>(createDefaultCompanySearchFilters());
  readonly page = signal<number>(0);
  readonly pageSize = signal<number>(environment.defaultPageSize);
  readonly selectedCompanyId = signal<number | null>(null);
  readonly jobsPage = signal<number>(0);

  readonly bookmarkTierDraft = signal<CompanyPriorityTier>('TARGET');
  readonly bookmarkNotesDraft = signal<string>('');
  readonly searchErrorMessage = signal<string | null>(null);
  readonly detailErrorMessage = signal<string | null>(null);
  readonly actionNotice = signal<string>('');
  readonly actionErrorMessage = signal<string | null>(null);

  readonly countryOptions = EUROPEAN_COUNTRY_OPTIONS;
  readonly sortOptions = COMPANY_SORT_OPTIONS;
  readonly priorityTierOptions = COMPANY_PRIORITY_TIER_OPTIONS;

  readonly activeFilterCount = computed<number>(() => countActiveCompanyFilters(this.filters()));

  readonly headerBadge = computed<string>(() => {
    const res = this.searchResult();
    if (!res) {
      return 'Employer Intelligence';
    }
    return `${res.verifiedSponsorsTotal} Verified Sponsors`;
  });

  readonly selectedSummary = computed<CompanySummary | null>(() => {
    const id = this.selectedCompanyId();
    const list = this.companies();
    if (id === null) {
      return list[0] ?? null;
    }
    return list.find(item => item.id === id) ?? null;
  });

  readonly activeDossierPendingSave = computed<boolean>(() => {
    const id = this.selectedCompany()?.id ?? this.selectedSummary()?.id;
    return id !== undefined && id !== null && this.pendingSaveCompanyIds().has(id);
  });

  readonly hasExplicitDeepLinkCompanyId = signal<number | null>(null);

  constructor() {
    this.setupReactiveStreams();
  }

  ngOnInit(): void {
    const initial = this.applyQueryParamFilters();
    this.searchTrigger$.next({
      page: 0,
      preferredCompanyId: initial.preferredCompanyId
    });
  }

  onSearchSubmit(): void {
    this.page.set(0);
    this.searchTrigger$.next({ page: 0, preferredCompanyId: null });
  }

  onFilterFieldChange<K extends keyof CompanySearchFilters>(
    field: K,
    value: CompanySearchFilters[K],
    autoTrigger = false
  ): void {
    this.filters.update(current => ({ ...current, [field]: value }));
    if (autoTrigger) {
      this.page.set(0);
      this.searchTrigger$.next({ page: 0, preferredCompanyId: null });
    }
  }

  onToggleFilter(
    field: 'verifiedSponsorOnly' | 'savedOnly' | 'withOpenJobsOnly',
    nextValue?: boolean
  ): void {
    const resolved = nextValue ?? !this.filters()[field];
    this.filters.update(current => ({ ...current, [field]: resolved }));
    this.page.set(0);
    this.searchTrigger$.next({ page: 0, preferredCompanyId: null });
  }

  applyTechChipFilter(tech: string): void {
    this.filters.update(current => ({ ...current, tech }));
    this.page.set(0);
    this.searchTrigger$.next({ page: 0, preferredCompanyId: null });
  }

  resetFilters(): void {
    this.filters.set(createDefaultCompanySearchFilters());
    this.page.set(0);
    this.searchTrigger$.next({ page: 0, preferredCompanyId: null });
  }

  goToPage(nextPage: number): void {
    if (nextPage < 0) {
      return;
    }
    this.page.set(nextPage);
    this.searchTrigger$.next({ page: nextPage, preferredCompanyId: null });
  }

  selectCompany(companyId: number): void {
    if (this.selectedCompanyId() === companyId && this.selectedCompany()?.id === companyId) {
      return;
    }
    this.selectedCompanyId.set(companyId);
    this.jobsPage.set(0);
    this.actionNotice.set('');
    this.actionErrorMessage.set(null);
    this.detailTrigger$.next({ id: companyId, jobsPage: 0 });
  }

  goToCompanyJobsPage(nextPage: number): void {
    const id = this.selectedCompanyId() ?? this.selectedCompany()?.id;
    if (!id || nextPage < 0) {
      return;
    }
    this.jobsPage.set(nextPage);
    this.jobsPageTrigger$.next({ id, page: nextPage });
  }

  retrySelectedDossier(): void {
    const id = this.selectedCompanyId() ?? this.selectedCompany()?.id;
    if (!id) {
      return;
    }
    this.detailTrigger$.next({ id, jobsPage: this.jobsPage() });
  }

  toggleCardBookmark(company: CompanySummary, event: Event): void {
    event.stopPropagation();
    if (this.companyService.isSavePending(company.id)) {
      return;
    }
    this.actionErrorMessage.set(null);
    const request$ = company.isSaved
      ? this.companyService.unsaveCompany(company.id)
      : this.companyService.saveCompany(company.id, {
          priorityTier: company.priorityTier ?? 'TARGET'
        });

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: res => {
        if (this.selectedCompany()?.id === res.companyId) {
          this.bookmarkTierDraft.set(res.priorityTier ?? 'TARGET');
          this.bookmarkNotesDraft.set(res.notes ?? '');
        }
        this.actionNotice.set(
          res.isSaved
            ? `Bookmarked ${res.companyName} (${formatCompanyPriorityTierLabel(res.priorityTier)}).`
            : `Removed ${res.companyName} from target companies.`
        );
      },
      error: err => {
        this.actionErrorMessage.set(extractApiErrorMessage(err, 'Unable to update company bookmark.'));
      }
    });
  }

  saveBookmarkDetails(): void {
    const dossier = this.selectedCompany();
    if (!dossier || this.companyService.isSavePending(dossier.id)) {
      return;
    }
    this.actionErrorMessage.set(null);
    const notes = this.bookmarkNotesDraft().trim();
    this.companyService
      .saveCompany(dossier.id, {
        priorityTier: this.bookmarkTierDraft(),
        notes
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: res => {
          this.bookmarkTierDraft.set(res.priorityTier ?? 'TARGET');
          this.bookmarkNotesDraft.set(res.notes ?? '');
          this.actionNotice.set(
            `Saved target profile for ${res.companyName} as ${formatCompanyPriorityTierLabel(res.priorityTier)}.`
          );
        },
        error: err => {
          this.actionErrorMessage.set(extractApiErrorMessage(err, 'Unable to save target bookmark.'));
        }
      });
  }

  removeDossierBookmark(): void {
    const dossier = this.selectedCompany();
    if (!dossier || !dossier.isSaved || this.companyService.isSavePending(dossier.id)) {
      return;
    }
    this.actionErrorMessage.set(null);
    this.companyService
      .unsaveCompany(dossier.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: res => {
          this.bookmarkTierDraft.set('TARGET');
          this.bookmarkNotesDraft.set('');
          this.actionNotice.set(`Removed ${res.companyName} from saved target companies.`);
          if (this.filters().savedOnly) {
            this.searchTrigger$.next({ page: this.page(), preferredCompanyId: null });
          }
        },
        error: err => {
          this.actionErrorMessage.set(extractApiErrorMessage(err, 'Unable to remove bookmark.'));
        }
      });
  }

  getPriorityTierMeta(tier: CompanyPriorityTier | null | undefined) {
    if (!tier) {
      return null;
    }
    return COMPANY_PRIORITY_TIER_META[tier] ?? null;
  }

  formatRegistryStatus(status: SponsorshipRecordStatus): string {
    return formatSponsorshipRecordStatusLabel(status);
  }

  isSafeUrl(rawUrl: string | null | undefined): boolean {
    return isSafeHttpUrl(rawUrl);
  }

  private setupReactiveStreams(): void {
    this.searchTrigger$
      .pipe(
        switchMap(trigger => {
          this.searchErrorMessage.set(null);
          return this.companyService.searchCompanies(this.filters(), trigger.page, this.pageSize()).pipe(
            catchError(err => {
              this.searchErrorMessage.set(
                extractApiErrorMessage(err, 'Failed to load employer directory.')
              );
              return of<PagedCompaniesResponse | null>(null);
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(response => {
        if (!response) {
          return;
        }
        const preferredId = this.hasExplicitDeepLinkCompanyId();
        this.hasExplicitDeepLinkCompanyId.set(null);

        if (preferredId !== null && preferredId > 0) {
          this.selectedCompanyId.set(preferredId);
          this.jobsPage.set(0);
          this.detailTrigger$.next({ id: preferredId, jobsPage: 0 });
          return;
        }

        const items = response.content;
        if (items.length === 0) {
          this.selectedCompanyId.set(null);
          this.companyService.clearSelection();
          return;
        }

        const currentSelected = this.selectedCompanyId();
        const stillInPage = items.some(item => item.id === currentSelected);
        const nextId = stillInPage ? (currentSelected as number) : items[0].id;
        this.selectedCompanyId.set(nextId);

        if (this.selectedCompany()?.id !== nextId) {
          this.jobsPage.set(0);
          this.detailTrigger$.next({ id: nextId, jobsPage: 0 });
        }
      });

    this.detailTrigger$
      .pipe(
        switchMap(({ id, jobsPage }) => {
          this.detailErrorMessage.set(null);
          return forkJoin({
            detail: this.companyService.getCompanyDetail(id),
            jobs: this.companyService.getCompanyJobs(id, {
              page: jobsPage,
              size: 8,
              sort: 'postedAt,desc'
            })
          }).pipe(
            catchError(err => {
              this.detailErrorMessage.set(
                extractApiErrorMessage(err, 'Unable to load employer intelligence dossier.')
              );
              return of(null);
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(result => {
        if (!result?.detail) {
          return;
        }
        this.syncBookmarkDraftFromDetail(result.detail);
      });

    this.jobsPageTrigger$
      .pipe(
        switchMap(({ id, page }) => {
          return this.companyService
            .getCompanyJobs(id, { page, size: 8, sort: 'postedAt,desc' })
            .pipe(
              catchError(err => {
                this.actionErrorMessage.set(
                  extractApiErrorMessage(err, 'Unable to load company open roles.')
                );
                return of(null);
              }),
              finalize(() => {
                // Signals already managed inside CompanyService
              })
            );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();
  }

  private syncBookmarkDraftFromDetail(detail: CompanyDetail): void {
    this.bookmarkTierDraft.set(detail.priorityTier ?? 'TARGET');
    this.bookmarkNotesDraft.set(detail.userNotes ?? '');
  }

  private applyQueryParamFilters(): { readonly preferredCompanyId: number | null } {
    const paramMap = this.route?.snapshot?.queryParamMap;
    if (!paramMap) {
      return { preferredCompanyId: null };
    }

    const query = paramMap.get('query')?.trim() ?? '';
    const country = paramMap.get('country')?.trim() ?? '';
    const industry = paramMap.get('industry')?.trim() ?? '';
    const tech = paramMap.get('tech')?.trim() ?? '';
    const verifiedSponsorOnly = paramMap.get('verifiedSponsorOnly') === 'true';
    const savedOnly = paramMap.get('savedOnly') === 'true';
    const withOpenJobsOnly = paramMap.get('withOpenJobsOnly') === 'true';
    const rawSort = paramMap.get('sort') as CompanySortOption | null;

    const companyIdRaw = paramMap.get('companyId');
    const parsedId = companyIdRaw ? Number(companyIdRaw) : NaN;
    const preferredCompanyId = Number.isInteger(parsedId) && parsedId > 0 ? parsedId : null;
    if (preferredCompanyId !== null) {
      this.hasExplicitDeepLinkCompanyId.set(preferredCompanyId);
    }

    const sortOptionsSet = new Set(this.sortOptions.map(o => o.value));
    const sort: CompanySortOption =
      rawSort && sortOptionsSet.has(rawSort) ? rawSort : 'activeJobs,desc';

    this.filters.set({
      query,
      country,
      industry,
      tech,
      verifiedSponsorOnly,
      savedOnly,
      withOpenJobsOnly,
      sort
    });

    return { preferredCompanyId };
  }
}

