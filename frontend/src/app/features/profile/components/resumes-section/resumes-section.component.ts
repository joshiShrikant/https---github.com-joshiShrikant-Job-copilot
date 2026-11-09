import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  output,
  signal,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { extractApiErrorMessage } from '../../../../core/models/api.models';
import {
  ApproveMasterResumeRequest,
  MasterResumeSyncSummaryDto,
  ResumeParseResponse,
  ResumeResponse
} from '../../../../core/models/resume.models';
import { ResumeService } from '../../../../core/services/resume.service';
import { ResumeReviewPanelComponent } from '../resume-review-panel/resume-review-panel.component';

@Component({
  selector: 'app-resumes-section',
  standalone: true,
  imports: [ResumeReviewPanelComponent],
  templateUrl: './resumes-section.component.html',
  styleUrl: './resumes-section.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResumesSectionComponent {
  private readonly resumeService = inject(ResumeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);

  readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');
  readonly masterApproved = output<ResumeResponse>();

  readonly resumes = this.resumeService.resumes;
  readonly isLoading = this.resumeService.isLoading;

  readonly selectedFile = signal<File | null>(null);
  readonly resumeTitle = signal<string>('');
  readonly isDragging = signal<boolean>(false);
  readonly isUploading = signal<boolean>(false);
  readonly downloadingUuid = signal<string | null>(null);
  readonly deletingUuid = signal<string | null>(null);
  readonly confirmDeleteUuid = signal<string | null>(null);

  readonly activeReviewUuid = signal<string | null>(null);
  readonly reviewParseResult = signal<ResumeParseResponse | null>(null);
  readonly isLoadingReviewDraft = signal<boolean>(false);
  readonly parsingUuid = signal<string | null>(null);
  readonly isApproving = signal<boolean>(false);
  readonly lastSyncSummary = signal<MasterResumeSyncSummaryDto | null>(null);

  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly activeReviewResume = computed(() => {
    const uuid = this.activeReviewUuid();
    if (!uuid) {
      return null;
    }
    return this.resumes().find(item => item.uuid === uuid) ?? null;
  });

  openFilePicker(): void {
    this.fileInput()?.nativeElement.click();
  }

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0) ?? null;
    if (file) {
      this.acceptCandidateFile(file);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(false);

    const file = event.dataTransfer?.files?.item(0) ?? null;
    if (file) {
      this.acceptCandidateFile(file);
    }
  }

  onTitleInput(event: Event): void {
    this.resumeTitle.set((event.target as HTMLInputElement).value);
  }

  clearSelection(): void {
    this.selectedFile.set(null);
    this.resumeTitle.set('');
    const input = this.fileInput()?.nativeElement;
    if (input) {
      input.value = '';
    }
  }

  uploadSelectedResume(): void {
    const file = this.selectedFile();
    const validationError = this.resumeService.validateResumeFile(file);
    if (validationError || !file) {
      this.errorMessage.set(validationError ?? 'Please select a valid PDF or DOCX resume.');
      this.successMessage.set(null);
      return;
    }

    const title = this.resumeTitle().trim();
    if (!title) {
      this.errorMessage.set('Please enter a descriptive title for your master resume.');
      this.successMessage.set(null);
      return;
    }
    if (title.length > 200) {
      this.errorMessage.set('Resume title must not exceed 200 characters.');
      this.successMessage.set(null);
      return;
    }

    if (this.isUploading()) {
      return;
    }

    this.isUploading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.resumeService
      .uploadMasterResume(file, title)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isUploading.set(false))
      )
      .subscribe({
        next: uploaded => {
          this.clearSelection();
          this.successMessage.set(
            `Uploaded "${uploaded.title}" (v${uploaded.versionNumber}). Parse with AI or review it below.`
          );
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to upload resume. Please verify the file and retry.')
          );
        }
      });
  }

  parseResume(resume: ResumeResponse, forceRefresh = true): void {
    if (this.parsingUuid() === resume.uuid) {
      return;
    }

    const targetUuid = resume.uuid;
    this.activeReviewUuid.set(targetUuid);
    if (this.reviewParseResult()?.resumeUuid !== targetUuid) {
      this.reviewParseResult.set(null);
    }
    this.isLoadingReviewDraft.set(false);
    this.parsingUuid.set(targetUuid);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.lastSyncSummary.set(null);

    this.resumeService
      .parseMasterResume(targetUuid, forceRefresh)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (this.parsingUuid() === targetUuid) {
            this.parsingUuid.set(null);
          }
        })
      )
      .subscribe({
        next: parsed => {
          if (this.activeReviewUuid() === targetUuid) {
            this.reviewParseResult.set(parsed);
          }
          this.successMessage.set(
            `Extracted structured CV draft for "${resume.title}". Review fields and click Approve to sync with your profile.`
          );
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(
              err,
              'Unable to parse resume with AI right now. You can retry or fill fields manually in Review & Approve.'
            )
          );
        }
      });
  }

  openReviewPanel(resume: ResumeResponse): void {
    const targetUuid = resume.uuid;
    this.activeReviewUuid.set(targetUuid);
    this.reviewParseResult.set(null);
    this.isLoadingReviewDraft.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.resumeService
      .getParsedResume(targetUuid)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (this.activeReviewUuid() === targetUuid) {
            this.isLoadingReviewDraft.set(false);
          }
        })
      )
      .subscribe({
        next: parsed => {
          if (this.activeReviewUuid() === targetUuid) {
            this.reviewParseResult.set(parsed);
          }
        },
        error: (err: unknown) => {
          if (this.activeReviewUuid() === targetUuid) {
            this.errorMessage.set(
              extractApiErrorMessage(err, 'Unable to load parsed draft for review.')
            );
          }
        }
      });
  }

  closeReviewPanel(): void {
    this.activeReviewUuid.set(null);
    this.reviewParseResult.set(null);
    this.isLoadingReviewDraft.set(false);
  }

  approveMasterResume(request: ApproveMasterResumeRequest): void {
    if (this.isApproving()) {
      return;
    }

    this.isApproving.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.resumeService
      .approveMasterResume(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isApproving.set(false))
      )
      .subscribe({
        next: approved => {
          this.lastSyncSummary.set(approved.profileSyncSummary ?? null);
          this.activeReviewUuid.set(null);
          this.reviewParseResult.set(null);
          this.successMessage.set(this.formatApprovalSuccessMessage(approved));
          this.masterApproved.emit(approved);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(
              err,
              'Unable to approve master resume. Verify required dates and education countries.'
            )
          );
        }
      });
  }

  downloadResume(resume: ResumeResponse): void {
    if (this.downloadingUuid() === resume.uuid) {
      return;
    }

    this.downloadingUuid.set(resume.uuid);
    this.errorMessage.set(null);

    this.resumeService
      .downloadResume(resume.uuid, resume.fileName)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.downloadingUuid.set(null))
      )
      .subscribe({
        next: ({ blob, fileName }) => {
          this.triggerBlobDownload(blob, fileName);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to download resume file. Please try again.')
          );
        }
      });
  }

  requestDelete(uuid: string): void {
    this.confirmDeleteUuid.set(uuid);
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  cancelDelete(): void {
    this.confirmDeleteUuid.set(null);
  }

  confirmDelete(resume: ResumeResponse): void {
    if (this.deletingUuid() === resume.uuid) {
      return;
    }

    this.deletingUuid.set(resume.uuid);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.resumeService
      .deleteResume(resume.uuid)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.deletingUuid.set(null);
          this.confirmDeleteUuid.set(null);
        })
      )
      .subscribe({
        next: () => {
          if (this.activeReviewUuid() === resume.uuid) {
            this.closeReviewPanel();
          }
          this.successMessage.set(`Deleted resume "${resume.title}".`);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(
              err,
              'Unable to delete resume. It may still be referenced by a tailored resume.'
            )
          );
        }
      });
  }

  formatFileSize(bytes: number): string {
    return this.resumeService.formatFileSize(bytes);
  }

  getFormatBadge(resume: ResumeResponse): string {
    if (resume.mimeType === 'application/pdf' || resume.fileName.toLowerCase().endsWith('.pdf')) {
      return 'PDF';
    }
    return 'DOCX';
  }

  formatDate(isoTimestamp: string): string {
    if (!isoTimestamp) {
      return '';
    }
    const parsed = new Date(isoTimestamp);
    if (Number.isNaN(parsed.getTime())) {
      return isoTimestamp;
    }
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    }).format(parsed);
  }

  private formatApprovalSuccessMessage(approved: ResumeResponse): string {
    const sync = approved.profileSyncSummary;
    if (!sync) {
      return `Approved "${approved.title}" (v${approved.versionNumber}) as your active master CV.`;
    }
    const totalSkills = sync.skillsAdded + sync.skillsUpdated;
    const totalExp = sync.experiencesAdded + sync.experiencesUpdated;
    const totalEdu = sync.educationsAdded + sync.educationsUpdated;
    return `Approved "${approved.title}" (v${approved.versionNumber}) & synced (${sync.syncMode}): ${totalSkills} skills, ${totalExp} work roles, ${totalEdu} educations updated.`;
  }

  private acceptCandidateFile(file: File): void {
    const validationError = this.resumeService.validateResumeFile(file);
    if (validationError) {
      this.clearSelection();
      this.errorMessage.set(validationError);
      this.successMessage.set(null);
      return;
    }

    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.selectedFile.set(file);

    if (!this.resumeTitle().trim()) {
      const stripped = file.name.replace(/\.(pdf|docx)$/i, '').replace(/[_-]+/g, ' ').trim();
      this.resumeTitle.set((stripped || 'Master Resume').slice(0, 200));
    }
  }

  private triggerBlobDownload(blob: Blob, fileName: string): void {
    const win = this.document.defaultView;
    const urlCreator = win?.URL ?? URL;
    const objectUrl = urlCreator.createObjectURL(blob);

    const anchor = this.document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = fileName;
    anchor.style.display = 'none';
    this.document.body.appendChild(anchor);
    anchor.click();
    this.document.body.removeChild(anchor);
    urlCreator.revokeObjectURL(objectUrl);
  }
}
