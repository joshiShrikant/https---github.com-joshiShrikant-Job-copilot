import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  effect,
  inject,
  input,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { extractApiErrorMessage } from '../../core/models/api.models';
import { ProfileService } from '../../core/services/profile.service';
import { ResumeService } from '../../core/services/resume.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { EducationSectionComponent } from './components/education-section/education-section.component';
import { ExperienceSectionComponent } from './components/experience-section/experience-section.component';
import { ProfileBasicsSectionComponent } from './components/profile-basics-section/profile-basics-section.component';
import { ResumesSectionComponent } from './components/resumes-section/resumes-section.component';
import { SkillsSectionComponent } from './components/skills-section/skills-section.component';

export type ProfileTabId = 'overview' | 'skills' | 'experience' | 'education' | 'resumes';

const VALID_TABS: readonly ProfileTabId[] = [
  'overview',
  'skills',
  'experience',
  'education',
  'resumes'
];

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    PageHeaderComponent,
    ProfileBasicsSectionComponent,
    SkillsSectionComponent,
    ExperienceSectionComponent,
    EducationSectionComponent,
    ResumesSectionComponent
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProfileComponent implements OnInit {
  private readonly profileService = inject(ProfileService);
  private readonly resumeService = inject(ResumeService);
  private readonly destroyRef = inject(DestroyRef);

  // Optional query param binding (?tab=skills)
  readonly tab = input<string>('');

  readonly activeTab = signal<ProfileTabId>('overview');
  readonly loadErrorMessage = signal<string | null>(null);

  readonly isLoadingProfile = this.profileService.isLoading;
  readonly profileExists = this.profileService.profileExists;
  readonly completenessPercent = this.profileService.completenessPercent;
  readonly skills = this.profileService.skills;
  readonly experiences = this.profileService.experiences;
  readonly educations = this.profileService.educations;
  readonly resumes = this.resumeService.resumes;

  constructor() {
    effect(() => {
      const requested = this.tab()?.trim().toLowerCase() as ProfileTabId | undefined;
      if (requested && VALID_TABS.includes(requested)) {
        this.activeTab.set(requested);
      }
    });
  }

  ngOnInit(): void {
    this.loadWorkspaceData();
  }

  selectTab(tabId: ProfileTabId): void {
    this.activeTab.set(tabId);
  }

  reload(): void {
    this.loadWorkspaceData();
  }

  onMasterResumeApproved(): void {
    this.profileService
      .loadProfile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: () => {
          // Keep current state cleanly if background profile refresh fails transiently
        }
      });
  }

  private loadWorkspaceData(): void {
    this.loadErrorMessage.set(null);

    forkJoin({
      profile: this.profileService.loadProfile().pipe(
        catchError((err: unknown) => {
          this.loadErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to load your candidate profile right now.')
          );
          return of(null);
        })
      ),
      resumes: this.resumeService.loadResumes().pipe(
        catchError((err: unknown) => {
          if (!this.loadErrorMessage()) {
            this.loadErrorMessage.set(
              extractApiErrorMessage(err, 'Unable to load your uploaded resumes right now.')
            );
          }
          return of([]);
        })
      )
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe();
  }
}

