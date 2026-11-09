import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { extractApiErrorMessage } from '../../core/models/api.models';
import { ResumeResponse } from '../../core/models/resume.models';
import { ResumeService } from '../../core/services/resume.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ResumesSectionComponent } from '../profile/components/resumes-section/resumes-section.component';

@Component({
  selector: 'app-resumes',
  standalone: true,
  imports: [PageHeaderComponent, ResumesSectionComponent, RouterLink],
  templateUrl: './resumes.component.html',
  styleUrl: './resumes.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResumesComponent implements OnInit {
  private readonly resumeService = inject(ResumeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loadErrorMessage = signal<string | null>(null);
  readonly profileSyncedNotice = signal<string | null>(null);

  ngOnInit(): void {
    this.loadResumes();
  }

  reload(): void {
    this.loadResumes();
  }

  onMasterApproved(approved: ResumeResponse): void {
    this.profileSyncedNotice.set(
      `Master CV "${approved.title}" (v${approved.versionNumber}) is approved and synced with your candidate profile.`
    );
  }

  private loadResumes(): void {
    this.loadErrorMessage.set(null);
    this.resumeService
      .loadResumes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: (err: unknown) => {
          this.loadErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to load your uploaded resumes.')
          );
        }
      });
  }
}

