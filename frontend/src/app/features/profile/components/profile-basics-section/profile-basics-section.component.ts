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
import { finalize } from 'rxjs';
import { extractApiErrorMessage } from '../../../../core/models/api.models';
import {
  COMMON_SALARY_CURRENCIES,
  RELOCATION_PREFERENCE_OPTIONS,
  RelocationPreference,
  UpsertProfileRequest,
  WORK_AUTHORIZATION_OPTIONS,
  WorkAuthorizationStatus
} from '../../../../core/models/profile.models';
import { ProfileService } from '../../../../core/services/profile.service';
import { httpUrlValidator, nonBlankValidator, trimToNull } from '../../utils/profile-validators';

@Component({
  selector: 'app-profile-basics-section',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './profile-basics-section.component.html',
  styleUrl: './profile-basics-section.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProfileBasicsSectionComponent {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly profileService = inject(ProfileService);
  private readonly destroyRef = inject(DestroyRef);

  readonly workAuthOptions = WORK_AUTHORIZATION_OPTIONS;
  readonly relocationOptions = RELOCATION_PREFERENCE_OPTIONS;
  readonly currencies = COMMON_SALARY_CURRENCIES;

  readonly profileExists = this.profileService.profileExists;
  readonly isSaving = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly preferredJobTitles = signal<readonly string[]>([]);
  readonly preferredCountries = signal<readonly string[]>([]);
  readonly titleDraft = signal<string>('');
  readonly countryDraft = signal<string>('');
  readonly emailDisplay = signal<string>('');

  readonly form = this.fb.group({
    firstName: [
      '',
      [Validators.required, nonBlankValidator(), Validators.minLength(2), Validators.maxLength(100)]
    ],
    lastName: [
      '',
      [Validators.required, nonBlankValidator(), Validators.minLength(2), Validators.maxLength(100)]
    ],
    currentDesignation: ['', [Validators.maxLength(150)]],
    yearsOfExperience: [0 as number | null, [Validators.min(0), Validators.max(70)]],
    currentCountry: ['', [Validators.maxLength(100)]],
    currentCity: ['', [Validators.maxLength(100)]],
    expectedSalaryMin: [null as number | null, [Validators.min(0), Validators.max(99999999.99)]],
    expectedSalaryCurrency: ['EUR', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    noticePeriodDays: [30 as number | null, [Validators.min(0), Validators.max(365)]],
    workAuthorizationStatus: ['NEEDS_SPONSORSHIP' as WorkAuthorizationStatus],
    relocationPreference: ['WILLING_TO_RELOCATE' as RelocationPreference],
    linkedinUrl: ['', [Validators.maxLength(255), httpUrlValidator()]],
    githubUrl: ['', [Validators.maxLength(255), httpUrlValidator()]],
    portfolioUrl: ['', [Validators.maxLength(255), httpUrlValidator()]],
    bio: ['', [Validators.maxLength(2000)]]
  });

  constructor() {
    effect(() => {
      const profile = this.profileService.profile();
      if (!profile) {
        return;
      }

      this.emailDisplay.set(profile.email ?? '');
      this.preferredJobTitles.set([...(profile.preferredJobTitles ?? [])]);
      this.preferredCountries.set([...(profile.preferredCountries ?? [])]);

      this.form.patchValue(
        {
          firstName: profile.firstName ?? '',
          lastName: profile.lastName ?? '',
          currentDesignation: profile.currentDesignation ?? '',
          yearsOfExperience: profile.yearsOfExperience ?? 0,
          currentCountry: profile.currentCountry ?? '',
          currentCity: profile.currentCity ?? '',
          expectedSalaryMin: profile.expectedSalaryMin ?? null,
          expectedSalaryCurrency: profile.expectedSalaryCurrency ?? 'EUR',
          noticePeriodDays: profile.noticePeriodDays ?? 30,
          workAuthorizationStatus: profile.workAuthorizationStatus ?? 'NEEDS_SPONSORSHIP',
          relocationPreference: profile.relocationPreference ?? 'WILLING_TO_RELOCATE',
          linkedinUrl: profile.linkedinUrl ?? '',
          githubUrl: profile.githubUrl ?? '',
          portfolioUrl: profile.portfolioUrl ?? '',
          bio: profile.bio ?? ''
        },
        { emitEvent: false }
      );
      this.form.markAsPristine();
    });
  }

  onTitleDraftInput(event: Event): void {
    this.titleDraft.set((event.target as HTMLInputElement).value);
  }

  onCountryDraftInput(event: Event): void {
    this.countryDraft.set((event.target as HTMLInputElement).value);
  }

  addPreferredTitle(): void {
    this.addItemsToList(this.titleDraft(), this.preferredJobTitles, 25);
    this.titleDraft.set('');
  }

  removePreferredTitle(title: string): void {
    this.preferredJobTitles.update(items => items.filter(item => item !== title));
    this.form.markAsDirty();
  }

  addPreferredCountry(): void {
    this.addItemsToList(this.countryDraft(), this.preferredCountries, 30);
    this.countryDraft.set('');
  }

  removePreferredCountry(country: string): void {
    this.preferredCountries.update(items => items.filter(item => item !== country));
    this.form.markAsDirty();
  }

  onTagKeydown(event: KeyboardEvent, kind: 'title' | 'country'): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      if (kind === 'title') {
        this.addPreferredTitle();
      } else {
        this.addPreferredCountry();
      }
    }
  }

  onSubmit(): void {
    if (this.titleDraft().trim()) {
      this.addPreferredTitle();
    }
    if (this.countryDraft().trim()) {
      this.addPreferredCountry();
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage.set('Please resolve the highlighted validation errors before saving.');
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
    const request: UpsertProfileRequest = {
      firstName: raw.firstName.trim(),
      lastName: raw.lastName.trim(),
      currentDesignation: trimToNull(raw.currentDesignation),
      yearsOfExperience: this.parseNumberOrNull(raw.yearsOfExperience),
      currentCountry: trimToNull(raw.currentCountry),
      currentCity: trimToNull(raw.currentCity),
      expectedSalaryMin: this.parseNumberOrNull(raw.expectedSalaryMin),
      expectedSalaryCurrency: trimToNull(raw.expectedSalaryCurrency)?.toUpperCase() ?? 'EUR',
      noticePeriodDays: this.parseIntOrNull(raw.noticePeriodDays),
      workAuthorizationStatus: raw.workAuthorizationStatus,
      relocationPreference: raw.relocationPreference,
      linkedinUrl: trimToNull(raw.linkedinUrl),
      githubUrl: trimToNull(raw.githubUrl),
      portfolioUrl: trimToNull(raw.portfolioUrl),
      bio: trimToNull(raw.bio),
      preferredJobTitles: [...this.preferredJobTitles()],
      preferredCountries: [...this.preferredCountries()]
    };

    this.profileService
      .saveProfile(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSaving.set(false))
      )
      .subscribe({
        next: () => {
          this.successMessage.set('Your candidate profile has been saved.');
          this.form.markAsPristine();
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to save profile details. Please try again.')
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
    for (const entry of entries) {
      const candidate = entry.slice(0, 100);
      if (next.length >= maxCount) {
        break;
      }
      if (!next.some(existing => existing.toLowerCase() === candidate.toLowerCase())) {
        next.push(candidate);
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

  private parseIntOrNull(value: number | string | null | undefined): number | null {
    const parsed = this.parseNumberOrNull(value);
    return parsed === null ? null : Math.trunc(parsed);
  }
}

