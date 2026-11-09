import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractApiErrorMessage } from '../../../../core/models/api.models';
import {
  UpdateJobPreferencesRequest,
  WORK_ARRANGEMENT_OPTIONS,
  WorkArrangement
} from '../../../../core/models/preferences.models';
import { COMMON_SALARY_CURRENCIES } from '../../../../core/models/profile.models';
import { PreferencesService } from '../../../../core/services/preferences.service';

export const QUICK_EU_COUNTRIES: readonly string[] = [
  'Germany',
  'Netherlands',
  'Ireland',
  'Sweden',
  'Switzerland',
  'Denmark'
];

@Component({
  selector: 'app-search-preferences-section',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './search-preferences-section.component.html',
  styleUrl: './search-preferences-section.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SearchPreferencesSectionComponent {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly preferencesService = inject(PreferencesService);
  private readonly destroyRef = inject(DestroyRef);

  readonly preferences = this.preferencesService.preferences;
  readonly workArrangementOptions = WORK_ARRANGEMENT_OPTIONS;
  readonly currencies = COMMON_SALARY_CURRENCIES;
  readonly quickCountries = QUICK_EU_COUNTRIES;

  readonly isSaving = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly preferredJobTitles = signal<readonly string[]>([]);
  readonly preferredCountries = signal<readonly string[]>([]);
  readonly preferredCities = signal<readonly string[]>([]);

  readonly titleDraft = signal<string>('');
  readonly countryDraft = signal<string>('');
  readonly cityDraft = signal<string>('');

  readonly form = this.fb.group({
    minSalary: [null as number | null, [Validators.min(0), Validators.max(99999999.99)]],
    salaryCurrency: ['EUR', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    workArrangement: ['ANY' as WorkArrangement, [Validators.required]],
    requiresSponsorship: [true],
    requiresRelocation: [true],
    englishSpeakingOnly: [true],
    minMatchScore: [60, [Validators.required, Validators.min(0), Validators.max(100)]],
    autopilotEnabled: [false],
    emailNotificationsEnabled: [false]
  });

  constructor() {
    effect(() => {
      const prefs = this.preferencesService.preferences();
      if (!prefs) {
        return;
      }

      this.preferredJobTitles.set([...(prefs.preferredJobTitles ?? [])]);
      this.preferredCountries.set([...(prefs.preferredCountries ?? [])]);
      this.preferredCities.set([...(prefs.preferredCities ?? [])]);

      this.form.patchValue(
        {
          minSalary: prefs.minSalary ?? null,
          salaryCurrency: prefs.salaryCurrency ?? 'EUR',
          workArrangement: prefs.workArrangement ?? 'ANY',
          requiresSponsorship: prefs.requiresSponsorship ?? true,
          requiresRelocation: prefs.requiresRelocation ?? true,
          englishSpeakingOnly: prefs.englishSpeakingOnly ?? true,
          minMatchScore: prefs.minMatchScore ?? 60,
          autopilotEnabled: prefs.autopilotEnabled ?? false,
          emailNotificationsEnabled: prefs.emailNotificationsEnabled ?? false
        },
        { emitEvent: false }
      );
      this.form.markAsPristine();
    });
  }

  onDraftInput(kind: 'title' | 'country' | 'city', event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    if (kind === 'title') {
      this.titleDraft.set(val);
    } else if (kind === 'country') {
      this.countryDraft.set(val);
    } else {
      this.cityDraft.set(val);
    }
  }

  onDraftKeydown(kind: 'title' | 'country' | 'city', event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.commitDraft(kind);
    }
  }

  commitDraft(kind: 'title' | 'country' | 'city'): void {
    if (kind === 'title') {
      this.addItemsToList(this.titleDraft(), this.preferredJobTitles, 25);
      this.titleDraft.set('');
    } else if (kind === 'country') {
      this.addItemsToList(this.countryDraft(), this.preferredCountries, 30);
      this.countryDraft.set('');
    } else {
      this.addItemsToList(this.cityDraft(), this.preferredCities, 30);
      this.cityDraft.set('');
    }
  }

  toggleQuickCountry(country: string): void {
    const exists = this.preferredCountries().some(
      c => c.toLowerCase() === country.toLowerCase()
    );
    if (exists) {
      this.preferredCountries.update(items =>
        items.filter(item => item.toLowerCase() !== country.toLowerCase())
      );
      this.form.markAsDirty();
      return;
    }
    this.addItemsToList(country, this.preferredCountries, 30);
  }

  hasCountry(country: string): boolean {
    return this.preferredCountries().some(c => c.toLowerCase() === country.toLowerCase());
  }

  removeChip(kind: 'title' | 'country' | 'city', value: string): void {
    if (kind === 'title') {
      this.preferredJobTitles.update(items => items.filter(item => item !== value));
    } else if (kind === 'country') {
      this.preferredCountries.update(items => items.filter(item => item !== value));
    } else {
      this.preferredCities.update(items => items.filter(item => item !== value));
    }
    this.form.markAsDirty();
  }

  onMatchScoreSliderInput(event: Event): void {
    const val = Number((event.target as HTMLInputElement).value);
    if (Number.isFinite(val)) {
      this.form.controls.minMatchScore.setValue(Math.max(0, Math.min(100, Math.trunc(val))));
      this.form.controls.minMatchScore.markAsDirty();
    }
  }

  onSubmit(): void {
    if (this.titleDraft().trim()) {
      this.commitDraft('title');
    }
    if (this.countryDraft().trim()) {
      this.commitDraft('country');
    }
    if (this.cityDraft().trim()) {
      this.commitDraft('city');
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage.set('Please resolve the highlighted preference errors before saving.');
      this.successMessage.set(null);
      return;
    }

    if (this.isSaving()) {
      return;
    }

    this.isSaving.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    const raw = this.form.getRawValue();
    const request: UpdateJobPreferencesRequest = {
      preferredJobTitles: [...this.preferredJobTitles()],
      preferredCountries: [...this.preferredCountries()],
      preferredCities: [...this.preferredCities()],
      minSalary: this.parseNumberOrNull(raw.minSalary),
      salaryCurrency: raw.salaryCurrency.trim().toUpperCase() || 'EUR',
      requiresSponsorship: raw.requiresSponsorship,
      requiresRelocation: raw.requiresRelocation,
      workArrangement: raw.workArrangement,
      englishSpeakingOnly: raw.englishSpeakingOnly,
      minMatchScore: Math.max(0, Math.min(100, Math.trunc(Number(raw.minMatchScore)))),
      autopilotEnabled: raw.autopilotEnabled,
      emailNotificationsEnabled: raw.emailNotificationsEnabled
    };

    this.preferencesService
      .savePreferences(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSaving.set(false))
      )
      .subscribe({
        next: () => {
          this.successMessage.set('Your job search and autopilot preferences have been saved.');
          this.form.markAsPristine();
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to save job search preferences. Please retry.')
          );
        }
      });
  }

  private addItemsToList(
    rawInput: string,
    targetSignal: ReturnType<typeof signal<readonly string[]>>,
    maxCount: number
  ): void {
    const entries = rawInput
      .split(',')
      .map(part => part.trim())
      .filter(Boolean);
    if (entries.length === 0) {
      return;
    }

    const next = [...targetSignal()];
    let added = false;
    for (const candidate of entries) {
      if (next.length >= maxCount) {
        break;
      }
      if (!next.some(existing => existing.toLowerCase() === candidate.toLowerCase())) {
        next.push(candidate.slice(0, 100));
        added = true;
      }
    }

    if (added) {
      targetSignal.set(next);
      this.form.markAsDirty();
    }
  }

  private parseNumberOrNull(value: number | string | null | undefined): number | null {
    if (value === null || value === undefined || value === '') {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
}

