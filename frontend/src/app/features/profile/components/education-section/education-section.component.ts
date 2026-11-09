import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { extractApiErrorMessage } from '../../../../core/models/api.models';
import { EducationDto, UpsertEducationRequest } from '../../../../core/models/profile.models';
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
  selector: 'app-education-section',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './education-section.component.html',
  styleUrl: './education-section.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EducationSectionComponent {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly profileService = inject(ProfileService);
  private readonly destroyRef = inject(DestroyRef);

  readonly educations = this.profileService.educations;
  readonly todayDate = todayIsoDate();

  readonly isFormOpen = signal<boolean>(false);
  readonly editingId = signal<number | null>(null);
  readonly isSubmitting = signal<boolean>(false);
  readonly deletingId = signal<number | null>(null);
  readonly confirmDeleteId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly form = this.fb.group(
    {
      institution: ['', [Validators.required, nonBlankValidator(), Validators.maxLength(200)]],
      degree: ['', [Validators.required, nonBlankValidator(), Validators.maxLength(150)]],
      fieldOfStudy: ['', [Validators.required, nonBlankValidator(), Validators.maxLength(150)]],
      country: ['', [Validators.required, nonBlankValidator(), Validators.maxLength(100)]],
      startDate: ['', [Validators.required, notFutureDateValidator()]],
      endDate: [''],
      gradeOrGpa: ['', [Validators.maxLength(50)]]
    },
    { validators: [dateRangeValidator('startDate', 'endDate')] }
  );

  openCreateForm(): void {
    this.editingId.set(null);
    this.confirmDeleteId.set(null);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.form.reset({
      institution: '',
      degree: '',
      fieldOfStudy: '',
      country: '',
      startDate: '',
      endDate: '',
      gradeOrGpa: ''
    });
    this.isFormOpen.set(true);
  }

  openEditForm(item: EducationDto): void {
    this.editingId.set(item.id);
    this.confirmDeleteId.set(null);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.form.reset({
      institution: item.institution,
      degree: item.degree,
      fieldOfStudy: item.fieldOfStudy,
      country: item.country,
      startDate: item.startDate,
      endDate: item.endDate ?? '',
      gradeOrGpa: item.gradeOrGpa ?? ''
    });
    this.isFormOpen.set(true);
  }

  cancelForm(): void {
    this.isFormOpen.set(false);
    this.editingId.set(null);
    this.errorMessage.set(null);
  }

  onSubmit(): void {
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
    const request: UpsertEducationRequest = {
      institution: raw.institution.trim(),
      degree: raw.degree.trim(),
      fieldOfStudy: raw.fieldOfStudy.trim(),
      country: raw.country.trim(),
      startDate: raw.startDate,
      endDate: trimToNull(raw.endDate),
      gradeOrGpa: trimToNull(raw.gradeOrGpa)
    };

    const editingId = this.editingId();
    const request$ =
      editingId === null
        ? this.profileService.addEducation(request)
        : this.profileService.updateEducation(editingId, request);

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
              ? `Added education at "${saved.institution}".`
              : `Updated education at "${saved.institution}".`
          );
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to save education entry. Please verify your inputs.')
          );
        }
      });
  }

  requestDelete(educationId: number): void {
    this.confirmDeleteId.set(educationId);
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  cancelDelete(): void {
    this.confirmDeleteId.set(null);
  }

  confirmDelete(item: EducationDto): void {
    if (this.deletingId() === item.id) {
      return;
    }

    this.deletingId.set(item.id);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.profileService
      .deleteEducation(item.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.deletingId.set(null);
          this.confirmDeleteId.set(null);
        })
      )
      .subscribe({
        next: () => {
          this.successMessage.set(`Removed education entry "${item.institution}".`);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to delete education entry.')
          );
        }
      });
  }

  formatDateRange(item: EducationDto): string {
    const start = formatMonthYear(item.startDate);
    const end = item.endDate ? formatMonthYear(item.endDate) : 'Present';
    return `${start} – ${end}`;
  }
}

