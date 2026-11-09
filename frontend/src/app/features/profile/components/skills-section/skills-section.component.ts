import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { extractApiErrorMessage } from '../../../../core/models/api.models';
import {
  PROFICIENCY_LEVEL_OPTIONS,
  ProficiencyLevel,
  UpsertSkillRequest,
  UserSkillDto
} from '../../../../core/models/profile.models';
import { ProfileService } from '../../../../core/services/profile.service';
import { nonBlankValidator } from '../../utils/profile-validators';

@Component({
  selector: 'app-skills-section',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './skills-section.component.html',
  styleUrl: './skills-section.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SkillsSectionComponent {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly profileService = inject(ProfileService);
  private readonly destroyRef = inject(DestroyRef);

  readonly proficiencyOptions = PROFICIENCY_LEVEL_OPTIONS;
  readonly skills = this.profileService.skills;

  readonly isFormOpen = signal<boolean>(false);
  readonly editingSkillId = signal<number | null>(null);
  readonly isSubmitting = signal<boolean>(false);
  readonly deletingSkillId = signal<number | null>(null);
  readonly confirmDeleteId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    skillName: ['', [Validators.required, nonBlankValidator(), Validators.maxLength(100)]],
    yearsExperience: [2, [Validators.required, Validators.min(0), Validators.max(70)]],
    proficiencyLevel: ['INTERMEDIATE' as ProficiencyLevel, [Validators.required]],
    primary: [false]
  });

  openCreateForm(): void {
    this.editingSkillId.set(null);
    this.confirmDeleteId.set(null);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.form.reset({
      skillName: '',
      yearsExperience: 2,
      proficiencyLevel: 'INTERMEDIATE',
      primary: false
    });
    this.isFormOpen.set(true);
  }

  openEditForm(skill: UserSkillDto): void {
    this.editingSkillId.set(skill.id);
    this.confirmDeleteId.set(null);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.form.reset({
      skillName: skill.skillName,
      yearsExperience: skill.yearsExperience,
      proficiencyLevel: skill.proficiencyLevel,
      primary: skill.primary
    });
    this.isFormOpen.set(true);
  }

  cancelForm(): void {
    this.isFormOpen.set(false);
    this.editingSkillId.set(null);
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
    const request: UpsertSkillRequest = {
      skillName: raw.skillName.trim(),
      yearsExperience: Number(raw.yearsExperience),
      proficiencyLevel: raw.proficiencyLevel,
      primary: raw.primary
    };

    const editingId = this.editingSkillId();
    const request$ =
      editingId === null
        ? this.profileService.addSkill(request)
        : this.profileService.updateSkill(editingId, request);

    request$
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false))
      )
      .subscribe({
        next: saved => {
          this.isFormOpen.set(false);
          this.editingSkillId.set(null);
          this.successMessage.set(
            editingId === null
              ? `Added skill "${saved.skillName}".`
              : `Updated skill "${saved.skillName}".`
          );
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to save skill. Please verify inputs and try again.')
          );
        }
      });
  }

  requestDelete(skillId: number): void {
    this.confirmDeleteId.set(skillId);
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  cancelDelete(): void {
    this.confirmDeleteId.set(null);
  }

  confirmDelete(skill: UserSkillDto): void {
    if (this.deletingSkillId() === skill.id) {
      return;
    }

    this.deletingSkillId.set(skill.id);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.profileService
      .deleteSkill(skill.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.deletingSkillId.set(null);
          this.confirmDeleteId.set(null);
        })
      )
      .subscribe({
        next: () => {
          this.successMessage.set(`Removed "${skill.skillName}" from your skills.`);
        },
        error: (err: unknown) => {
          this.errorMessage.set(extractApiErrorMessage(err, 'Unable to delete skill.'));
        }
      });
  }

  formatProficiency(level: ProficiencyLevel): string {
    return level.charAt(0) + level.slice(1).toLowerCase();
  }
}

