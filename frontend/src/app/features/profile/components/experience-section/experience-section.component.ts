import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { extractApiErrorMessage } from '../../../../core/models/api.models';
import {
  UpsertWorkExperienceRequest,
  WorkExperienceDto
} from '../../../../core/models/profile.models';
import { ProfileService } from '../../../../core/services/profile.service';
import {
  dateRangeValidator,
  formatMonthYear,
  nonBlankValidator,
  notFutureDateValidator,
  todayIsoDate,
  trimToNull
} from '../../utils/profile-validators';

@Component({
  selector: 'app-experience-section',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './experience-section.component.html',
  styleUrl: './experience-section.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExperienceSectionComponent {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly profileService = inject(ProfileService);
  private readonly destroyRef = inject(DestroyRef);

  readonly experiences = this.profileService.experiences;
  readonly todayDate = todayIsoDate();

  readonly isFormOpen = signal<boolean>(false);
  readonly editingId = signal<number | null>(null);
  readonly isSubmitting = signal<boolean>(false);
  readonly deletingId = signal<number | null>(null);
  readonly confirmDeleteId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly skillsUsed = signal<readonly string[]>([]);
  readonly skillDraft = signal<string>('');

  readonly form = this.fb.group(
    {
      companyName: ['', [Validators.required, nonBlankValidator(), Validators.maxLength(200)]],
      designation: ['', [Validators.required, nonBlankValidator(), Validators.maxLength(150)]],
      location: ['', [Validators.maxLength(150)]],
      startDate: ['', [Validators.required, notFutureDateValidator()]],
      endDate: [''],
      current: [false],
      description: ['', [Validators.maxLength(5000)]]
    },
    { validators: [dateRangeValidator('startDate', 'endDate', 'current')] }
  );

  constructor() {
    this.form.controls.current.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(isCurrent => {
        this.syncEndDateControlState(isCurrent);
      });
  }

  openCreateForm(): void {
    this.editingId.set(null);
    this.confirmDeleteId.set(null);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.skillsUsed.set([]);
    this.skillDraft.set('');

    this.form.reset({
      companyName: '',
      designation: '',
      location: '',
      startDate: '',
      endDate: '',
      current: false,
      description: ''
    });
    this.syncEndDateControlState(false);
    this.isFormOpen.set(true);
  }

  openEditForm(item: WorkExperienceDto): void {
    this.editingId.set(item.id);
    this.confirmDeleteId.set(null);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.skillsUsed.set([...(item.skillsUsed ?? [])]);
    this.skillDraft.set('');

    this.form.reset({
      companyName: item.companyName,
      designation: item.designation,
      location: item.location ?? '',
      startDate: item.startDate,
      endDate: item.current ? '' : (item.endDate ?? ''),
      current: item.current,
      description: item.description ?? ''
    });
    this.syncEndDateControlState(item.current);
    this.isFormOpen.set(true);
  }

  cancelForm(): void {
    this.isFormOpen.set(false);
    this.editingId.set(null);
    this.errorMessage.set(null);
  }

  onSkillDraftInput(event: Event): void {
    this.skillDraft.set((event.target as HTMLInputElement).value);
  }

  onSkillKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.addSkillUsed();
    }
  }

  addSkillUsed(): void {
    const rawItems = this.skillDraft()
      .split(',')
      .map(s => s.trim())
      .filter(Boolean);
    if (rawItems.length === 0) {
      this.skillDraft.set('');
      return;
    }

    const updated = [...this.skillsUsed()];
    for (const candidate of rawItems) {
      if (updated.length >= 50) {
        break;
      }
      if (!updated.some(existing => existing.toLowerCase() === candidate.toLowerCase())) {
        updated.push(candidate.slice(0, 100));
      }
    }
    this.skillsUsed.set(updated);
    this.skillDraft.set('');
  }

  removeSkillUsed(skill: string): void {
    this.skillsUsed.update(items => items.filter(item => item !== skill));
  }

  onSubmit(): void {
    if (this.skillDraft().trim()) {
      this.addSkillUsed();
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (this.isSubmitting()) {
      return;
    }

    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    const raw = this.form.getRawValue();
    const request: UpsertWorkExperienceRequest = {
      companyName: raw.companyName.trim(),
      designation: raw.designation.trim(),
      location: trimToNull(raw.location),
      startDate: raw.startDate,
      endDate: raw.current ? null : trimToNull(raw.endDate),
      current: raw.current,
      description: trimToNull(raw.description),
      skillsUsed: [...this.skillsUsed()]
    };

    const editingId = this.editingId();
    const request$ =
      editingId === null
        ? this.profileService.addWorkExperience(request)
        : this.profileService.updateWorkExperience(editingId, request);

    request$
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false))
      )
      .subscribe({
        next: saved => {
          this.isFormOpen.set(false);
          this.editingId.set(null);
          this.successMessage.set(
            editingId === null
              ? `Added experience at "${saved.companyName}".`
              : `Updated experience at "${saved.companyName}".`
          );
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to save work experience. Please verify your dates.')
          );
        }
      });
  }

  requestDelete(experienceId: number): void {
    this.confirmDeleteId.set(experienceId);
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  cancelDelete(): void {
    this.confirmDeleteId.set(null);
  }

  confirmDelete(item: WorkExperienceDto): void {
    if (this.deletingId() === item.id) {
      return;
    }

    this.deletingId.set(item.id);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.profileService
      .deleteWorkExperience(item.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.deletingId.set(null);
          this.confirmDeleteId.set(null);
        })
      )
      .subscribe({
        next: () => {
          this.successMessage.set(`Removed experience at "${item.companyName}".`);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to delete work experience entry.')
          );
        }
      });
  }

  formatDateRange(item: WorkExperienceDto): string {
    const start = formatMonthYear(item.startDate);
    const end = item.current ? 'Present' : formatMonthYear(item.endDate);
    return end ? `${start} – ${end}` : start;
  }

  private syncEndDateControlState(isCurrent: boolean): void {
    const endControl = this.form.controls.endDate;
    if (isCurrent) {
      endControl.setValue('', { emitEvent: false });
      endControl.disable({ emitEvent: false });
    } else {
      endControl.enable({ emitEvent: false });
    }
    this.form.updateValueAndValidity({ emitEvent: false });
  }
}

