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
import { extractApiErrorMessage } from '../../core/models/api.models';
import { formatSponsorshipRecordStatusLabel } from '../../core/models/job.models';
import {
  CountrySponsorshipProfile,
  RegistrySyncStatus,
  VISA_SPONSORSHIP_STATUS_OPTIONS,
  isSafeHttpUrl
} from '../../core/models/sponsorship.models';
import { AuthService } from '../../core/services/auth.service';
import { SponsorshipService } from '../../core/services/sponsorship.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';

export type SponsorshipWorkspaceTab = 'history' | 'countries' | 'ind-registry';

const FALLBACK_14_COUNTRIES: readonly string[] = [
  'Germany',
  'Netherlands',
  'Luxembourg',
  'Switzerland',
  'Ireland',
  'Austria',
  'Belgium',
  'Sweden',
  'Denmark',
  'Finland',
  'France',
  'Poland',
  'Portugal',
  'Spain'
];

@Component({
  selector: 'app-sponsorship',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    PageHeaderComponent,
    StatusBadgeComponent,
    TimeAgoPipe
  ],
  templateUrl: './sponsorship.component.html',
  styleUrl: './sponsorship.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SponsorshipComponent implements OnInit {
  private readonly sponsorshipService = inject(SponsorshipService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly destroyRef = inject(DestroyRef);

  readonly statusLegend = VISA_SPONSORSHIP_STATUS_OPTIONS;
  readonly isAdmin = this.authService.isAdmin;

  readonly countries = this.sponsorshipService.countries;
  readonly verificationResult = this.sponsorshipService.lastVerification;
  readonly historyPage = this.sponsorshipService.historyPage;
  readonly indRegistryPage = this.sponsorshipService.indRegistryPage;

  readonly isLoadingCountries = this.sponsorshipService.isLoadingCountries;
  readonly isVerifying = this.sponsorshipService.isVerifying;
  readonly isLoadingHistory = this.sponsorshipService.isLoadingHistory;
  readonly isLoadingIndRegistry = this.sponsorshipService.isLoadingIndRegistry;
  readonly isSyncingInd = this.sponsorshipService.isSyncingInd;

  readonly activeTab = signal<SponsorshipWorkspaceTab>('history');

  readonly companyInput = signal<string>('');
  readonly countryInput = signal<string>('Netherlands');
  readonly jobIdInput = signal<number | null>(null);
  readonly postingTextInput = signal<string>('');

  readonly historyCompanyFilter = signal<string>('');
  readonly historyCountryFilter = signal<string>('');
  readonly historyPageIndex = signal<number>(0);

  readonly indQueryFilter = signal<string>('');
  readonly indPageIndex = signal<number>(0);

  readonly errorMessage = signal<string | null>(null);
  readonly syncNotice = signal<string | null>(null);

  readonly countrySelectOptions = computed<readonly string[]>(() => {
    const loaded = this.countries();
    return loaded.length > 0 ? loaded.map(c => c.country) : FALLBACK_14_COUNTRIES;
  });

  readonly selectedCountryProfile = computed<CountrySponsorshipProfile | null>(() => {
    const selected = this.countryInput().trim().toLowerCase();
    return (
      this.countries().find(
        c => c.country.toLowerCase() === selected || c.isoCode.toLowerCase() === selected
      ) ?? null
    );
  });

  readonly indSyncStatus = computed(() => this.indRegistryPage()?.lastSyncStatus ?? null);

  ngOnInit(): void {
    const qp = this.route?.snapshot?.queryParamMap;
    const initialCompany = qp?.get('companyName')?.trim() ?? '';
    const initialCountry = qp?.get('country')?.trim() ?? '';
    const initialJobIdRaw = qp?.get('jobId');
    const parsedJobId = initialJobIdRaw ? Number(initialJobIdRaw) : NaN;

    if (initialCompany) {
      this.companyInput.set(initialCompany);
    }
    if (initialCountry) {
      this.countryInput.set(initialCountry);
    }
    if (Number.isInteger(parsedJobId) && parsedJobId > 0) {
      this.jobIdInput.set(parsedJobId);
    }

    this.loadCountries();
    this.fetchHistory(0);
    this.fetchIndRegistry(0);

    if (initialCompany && initialCountry) {
      this.verifyCurrentCompany();
    }
  }

  onCompanyInput(value: string): void {
    this.companyInput.set(value);
  }

  onCountrySelect(value: string): void {
    this.countryInput.set(value);
  }

  onPostingTextInput(value: string): void {
    this.postingTextInput.set(value);
  }

  clearLinkedJob(): void {
    this.jobIdInput.set(null);
  }

  verifyCurrentCompany(): void {
    const companyName = this.companyInput().trim();
    const country = this.countryInput().trim();
    if (!companyName || !country) {
      this.errorMessage.set('Please enter both an employer name and a target European country.');
      return;
    }

    this.errorMessage.set(null);
    this.sponsorshipService
      .verifyCompany({
        companyName,
        country,
        jobId: this.jobIdInput(),
        postingText: this.postingTextInput()
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.historyPageIndex.set(0);
          this.fetchHistory(0);
        },
        error: err => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to verify visa sponsorship status right now.')
          );
        }
      });
  }

  selectTab(tab: SponsorshipWorkspaceTab): void {
    this.activeTab.set(tab);
  }

  applyCountryQuickCheck(countryName: string): void {
    this.countryInput.set(countryName);
    window?.scrollTo?.({ top: 0, behavior: 'smooth' });
  }

  onHistoryFilterSubmit(): void {
    this.historyPageIndex.set(0);
    this.fetchHistory(0);
  }

  resetHistoryFilters(): void {
    this.historyCompanyFilter.set('');
    this.historyCountryFilter.set('');
    this.historyPageIndex.set(0);
    this.fetchHistory(0);
  }

  goToHistoryPage(nextPage: number): void {
    if (nextPage < 0) {
      return;
    }
    this.historyPageIndex.set(nextPage);
    this.fetchHistory(nextPage);
  }

  onIndSearchSubmit(): void {
    this.indPageIndex.set(0);
    this.fetchIndRegistry(0);
  }

  goToIndPage(nextPage: number): void {
    if (nextPage < 0) {
      return;
    }
    this.indPageIndex.set(nextPage);
    this.fetchIndRegistry(nextPage);
  }

  prefillFromIndEntry(officialName: string): void {
    this.companyInput.set(officialName);
    this.countryInput.set('Netherlands');
    this.verifyCurrentCompany();
  }

  triggerAdminIndSync(): void {
    this.errorMessage.set(null);
    this.syncNotice.set(null);
    this.sponsorshipService
      .triggerAdminIndSync()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          const outcomeText =
            result.message || `IND register sync finished with status ${result.status}.`;
          this.syncNotice.set(outcomeText);
          this.loadCountries();
          this.fetchIndRegistry(0);
        },
        error: err => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Failed to synchronize Netherlands IND register.')
          );
        }
      });
  }

  formatRecordStatus(
    status: Parameters<typeof formatSponsorshipRecordStatusLabel>[0]
  ): string {
    return formatSponsorshipRecordStatusLabel(status);
  }

  formatSyncStatusLabel(status: RegistrySyncStatus | null | undefined): string {
    switch (status) {
      case 'SUCCESS':
        return 'Synced';
      case 'BLOCKED':
        return 'Access Blocked';
      case 'FAILED':
        return 'Sync Failed';
      case 'UNAVAILABLE':
      default:
        return 'Not Synced / Unavailable';
    }
  }

  isSafeUrl(url: string | null | undefined): boolean {
    return isSafeHttpUrl(url);
  }

  private loadCountries(): void {
    this.sponsorshipService
      .loadCountries()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: () => {
          // Fallback 14-country list stays available if country profiles fail transiently
        }
      });
  }

  private fetchHistory(page: number): void {
    this.sponsorshipService
      .loadHistory({
        companyName: this.historyCompanyFilter(),
        country: this.historyCountryFilter(),
        page,
        size: 10
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: err => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to load sponsorship verification history.')
          );
        }
      });
  }

  private fetchIndRegistry(page: number): void {
    this.sponsorshipService
      .searchIndRegistry({
        query: this.indQueryFilter(),
        page,
        size: 12
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: () => {
          // Non-fatal in verifier workspace
        }
      });
  }
}

