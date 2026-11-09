import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal
} from '@angular/core';
import {
  EUROPEAN_COUNTRY_OPTIONS,
  EmploymentType,
  ExperienceLevel,
  JOB_EMPLOYMENT_TYPE_OPTIONS,
  JOB_EXPERIENCE_LEVEL_OPTIONS,
  JOB_MIN_MATCH_OPTIONS,
  JOB_SORT_OPTIONS,
  JOB_SPONSOR_STATUS_OPTIONS,
  JOB_WORK_ARRANGEMENT_OPTIONS,
  JobSearchFilters,
  JobSortOption,
  JobSponsorStatus,
  JobWorkArrangement,
  countActiveJobFilters,
  createDefaultJobSearchFilters,
  formatEmploymentTypeLabel,
  formatExperienceLevelLabel,
  formatSponsorStatusLabel,
  formatWorkArrangementLabel
} from '../../../core/models/job.models';

interface ActiveFilterChip {
  readonly key: keyof JobSearchFilters;
  readonly label: string;
}

@Component({
  selector: 'app-job-filter-bar',
  standalone: true,
  templateUrl: './job-filter-bar.component.html',
  styleUrl: './job-filter-bar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JobFilterBarComponent {
  private readonly destroyRef = inject(DestroyRef);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private secondaryTimer: ReturnType<typeof setTimeout> | null = null;

  readonly filters = input.required<JobSearchFilters>();
  readonly totalCount = input<number | null>(null);
  readonly countryOptions = input<readonly string[]>(EUROPEAN_COUNTRY_OPTIONS);
  readonly debounceMs = input<number>(250);
  readonly disabled = input<boolean>(false);

  readonly filtersChange = output<JobSearchFilters>();
  readonly resetRequested = output<void>();

  readonly queryDraft = signal<string>('');
  readonly cityDraft = signal<string>('');
  readonly minSalaryDraft = signal<string>('');

  readonly sponsorOptions = JOB_SPONSOR_STATUS_OPTIONS;
  readonly arrangementOptions = JOB_WORK_ARRANGEMENT_OPTIONS;
  readonly employmentOptions = JOB_EMPLOYMENT_TYPE_OPTIONS;
  readonly experienceOptions = JOB_EXPERIENCE_LEVEL_OPTIONS;
  readonly minMatchOptions = JOB_MIN_MATCH_OPTIONS;
  readonly sortOptions = JOB_SORT_OPTIONS;

  readonly activeCount = computed<number>(() => countActiveJobFilters(this.filters()));

  readonly activeChips = computed<readonly ActiveFilterChip[]>(() => {
    const f = this.filters();
    const chips: ActiveFilterChip[] = [];
    if (f.query.trim().length > 0) {
      chips.push({ key: 'query', label: `Search: "${f.query.trim()}"` });
    }
    if (f.country) {
      chips.push({ key: 'country', label: `Country: ${f.country}` });
    }
    if (f.city.trim().length > 0) {
      chips.push({ key: 'city', label: `City: ${f.city.trim()}` });
    }
    if (f.workArrangement) {
      chips.push({
        key: 'workArrangement',
        label: `Mode: ${formatWorkArrangementLabel(f.workArrangement)}`
      });
    }
    if (f.sponsorStatus) {
      chips.push({
        key: 'sponsorStatus',
        label: formatSponsorStatusLabel(f.sponsorStatus)
      });
    }
    if (f.employmentType) {
      chips.push({
        key: 'employmentType',
        label: formatEmploymentTypeLabel(f.employmentType)
      });
    }
    if (f.experienceLevel) {
      chips.push({
        key: 'experienceLevel',
        label: `Level: ${formatExperienceLevelLabel(f.experienceLevel)}`
      });
    }
    if (f.minMatchScore !== null && f.minMatchScore > 0) {
      chips.push({ key: 'minMatchScore', label: `${f.minMatchScore}%+ match` });
    }
    if (f.minSalary !== null && f.minSalary > 0) {
      chips.push({ key: 'minSalary', label: `Min €${f.minSalary.toLocaleString('en-US')}` });
    }
    if (f.savedOnly) {
      chips.push({ key: 'savedOnly', label: 'Saved Only' });
    }
    if (f.interestedOnly) {
      chips.push({ key: 'interestedOnly', label: 'Interested Only' });
    }
    if (f.includeHidden) {
      chips.push({ key: 'includeHidden', label: 'Including Hidden' });
    }
    return chips;
  });

  constructor() {
    effect(() => {
      const f = this.filters();
      if (this.queryDraft() !== f.query) {
        this.queryDraft.set(f.query);
      }
      if (this.cityDraft() !== f.city) {
        this.cityDraft.set(f.city);
      }
      const nextSalaryText = f.minSalary !== null && f.minSalary > 0 ? String(f.minSalary) : '';
      if (this.minSalaryDraft() !== nextSalaryText) {
        this.minSalaryDraft.set(nextSalaryText);
      }
    });

    this.destroyRef.onDestroy(() => {
      this.clearPendingSearchTimer();
      this.clearPendingSecondaryTimer();
    });
  }

  onQueryInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.queryDraft.set(value);
    this.clearPendingSearchTimer();

    const waitMs = Math.max(0, this.debounceMs());
    if (waitMs === 0) {
      this.emitPatch({ query: value });
      return;
    }

    this.searchTimer = setTimeout(() => {
      this.searchTimer = null;
      this.emitPatch({ query: value });
    }, waitMs);
  }

  onSearchSubmit(): void {
    this.clearPendingSearchTimer();
    this.emitPatch({ query: this.queryDraft() });
  }

  onClearQuery(): void {
    this.clearPendingSearchTimer();
    this.queryDraft.set('');
    this.emitPatch({ query: '' });
  }

  onCityInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.cityDraft.set(value);
    this.scheduleSecondaryEmit({ city: value });
  }

  onCitySubmit(): void {
    this.clearPendingSecondaryTimer();
    this.emitPatch({ city: this.cityDraft() });
  }

  onMinSalaryInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    this.minSalaryDraft.set(raw);
    this.scheduleSecondaryEmit({ minSalary: this.parseMinSalary(raw) });
  }

  onMinSalarySubmit(): void {
    this.clearPendingSecondaryTimer();
    this.emitPatch({ minSalary: this.parseMinSalary(this.minSalaryDraft()) });
  }

  onCountryChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.emitPatch({ country: value });
  }

  onSponsorChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as JobSponsorStatus | '';
    this.emitPatch({ sponsorStatus: value });
  }

  onEmploymentChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as EmploymentType | '';
    this.emitPatch({ employmentType: value });
  }

  onExperienceChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as ExperienceLevel | '';
    this.emitPatch({ experienceLevel: value });
  }

  onMinMatchChange(event: Event): void {
    const raw = (event.target as HTMLSelectElement).value;
    const parsed = raw ? Number(raw) : null;
    this.emitPatch({ minMatchScore: parsed !== null && Number.isFinite(parsed) ? parsed : null });
  }

  onSortChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as JobSortOption;
    this.emitPatch({ sort: value });
  }

  onSelectWorkArrangement(value: Exclude<JobWorkArrangement, 'ANY'> | ''): void {
    this.emitPatch({ workArrangement: value });
  }

  onToggleBooleanFlag(flag: 'savedOnly' | 'interestedOnly' | 'includeHidden'): void {
    const current = this.filters();
    this.emitPatch({ [flag]: !current[flag] } as Partial<JobSearchFilters>);
  }

  onRemoveChip(key: keyof JobSearchFilters): void {
    this.clearPendingSearchTimer();
    this.clearPendingSecondaryTimer();
    switch (key) {
      case 'query':
        this.queryDraft.set('');
        this.emitPatch({ query: '' });
        break;
      case 'country':
        this.emitPatch({ country: '' });
        break;
      case 'city':
        this.cityDraft.set('');
        this.emitPatch({ city: '' });
        break;
      case 'workArrangement':
        this.emitPatch({ workArrangement: '' });
        break;
      case 'sponsorStatus':
        this.emitPatch({ sponsorStatus: '' });
        break;
      case 'employmentType':
        this.emitPatch({ employmentType: '' });
        break;
      case 'experienceLevel':
        this.emitPatch({ experienceLevel: '' });
        break;
      case 'minMatchScore':
        this.emitPatch({ minMatchScore: null });
        break;
      case 'minSalary':
        this.minSalaryDraft.set('');
        this.emitPatch({ minSalary: null });
        break;
      case 'savedOnly':
        this.emitPatch({ savedOnly: false });
        break;
      case 'interestedOnly':
        this.emitPatch({ interestedOnly: false });
        break;
      case 'includeHidden':
        this.emitPatch({ includeHidden: false });
        break;
      default:
        break;
    }
  }

  onResetAll(): void {
    this.clearPendingSearchTimer();
    this.clearPendingSecondaryTimer();
    this.queryDraft.set('');
    this.cityDraft.set('');
    this.minSalaryDraft.set('');
    this.filtersChange.emit(createDefaultJobSearchFilters());
    this.resetRequested.emit();
  }

  private scheduleSecondaryEmit(patch: Partial<JobSearchFilters>): void {
    this.clearPendingSecondaryTimer();
    const waitMs = Math.max(0, this.debounceMs());
    if (waitMs === 0) {
      this.emitPatch(patch);
      return;
    }
    this.secondaryTimer = setTimeout(() => {
      this.secondaryTimer = null;
      this.emitPatch(patch);
    }, waitMs);
  }

  private parseMinSalary(raw: string): number | null {
    const trimmed = raw.trim();
    if (!trimmed) {
      return null;
    }
    const numeric = Number(trimmed);
    return Number.isFinite(numeric) && numeric > 0 ? Math.round(numeric) : null;
  }

  private emitPatch(patch: Partial<JobSearchFilters>): void {
    this.filtersChange.emit({
      ...this.filters(),
      ...patch
    });
  }

  private clearPendingSearchTimer(): void {
    if (this.searchTimer !== null) {
      clearTimeout(this.searchTimer);
      this.searchTimer = null;
    }
  }

  private clearPendingSecondaryTimer(): void {
    if (this.secondaryTimer !== null) {
      clearTimeout(this.secondaryTimer);
      this.secondaryTimer = null;
    }
  }
}
