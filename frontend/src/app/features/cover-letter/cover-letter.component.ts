import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  NgZone,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { extractApiErrorMessage } from '../../core/models/api.models';
import {
  COVER_LETTER_TONE_OPTIONS,
  CoverLetterExportFile,
  CoverLetterExportFormat,
  CoverLetterMetadataDto,
  CoverLetterResponse,
  CoverLetterSummaryDto,
  CoverLetterTone,
  UpdateCoverLetterRequest,
  formatCoverLetterToneLabel
} from '../../core/models/cover-letter.models';
import { JobDetail, JobSummary } from '../../core/models/job.models';
import { ResumeResponse } from '../../core/models/resume.models';
import { TailoredResumeSummaryDto } from '../../core/models/tailored-resume.models';
import { CoverLetterService } from '../../core/services/cover-letter.service';
import { JobService } from '../../core/services/job.service';
import { ProfileService } from '../../core/services/profile.service';
import { ResumeService } from '../../core/services/resume.service';
import { TailoredResumeService } from '../../core/services/tailored-resume.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';
import { CoverLetterPreviewComponent } from './components/cover-letter-preview/cover-letter-preview.component';

export interface SelectableCoverLetterJobOption {
  readonly id: number;
  readonly title: string;
  readonly companyName: string;
  readonly country: string;
  readonly matchPercentage: number | null;
}

@Component({
  selector: 'app-cover-letter',
  standalone: true,
  imports: [
    RouterLink,
    PageHeaderComponent,
    ConfirmDialogComponent,
    CoverLetterPreviewComponent,
    TimeAgoPipe
  ],
  templateUrl: './cover-letter.component.html',
  styleUrl: './cover-letter.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CoverLetterComponent implements OnInit {
  private readonly coverLetterService = inject(CoverLetterService);
  private readonly jobService = inject(JobService);
  private readonly tailoredResumeService = inject(TailoredResumeService);
  private readonly resumeService = inject(ResumeService);
  private readonly profileService = inject(ProfileService);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly document = inject(DOCUMENT);
  private readonly ngZone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);

  readonly toneOptions = COVER_LETTER_TONE_OPTIONS;

  readonly coverLetters = this.coverLetterService.coverLetters;
  readonly activeCoverLetter = this.coverLetterService.selectedCoverLetter;
  readonly isLoadingList = this.coverLetterService.isLoadingList;
  readonly isLoadingDetail = this.coverLetterService.isLoadingDetail;
  readonly isGenerating = this.coverLetterService.isGenerating;
  readonly isSaving = this.coverLetterService.isSaving;
  readonly exportingFormat = this.coverLetterService.exportingFormat;
  readonly isExporting = this.coverLetterService.isExporting;
  readonly deletingUuid = this.coverLetterService.deletingUuid;

  readonly tailoredResumes = this.tailoredResumeService.resumes;
  readonly uploadedResumes = this.resumeService.resumes;
  readonly indexedJobs = this.jobService.jobs;
  readonly profile = this.profileService.profile;
  readonly profileExists = this.profileService.profileExists;
  readonly profileSkills = this.profileService.skills;
  readonly profileExperiences = this.profileService.experiences;
  readonly profileEducations = this.profileService.educations;

  // Generator controls
  readonly selectedJobId = signal<number | null>(null);
  readonly selectedTailoredResumeUuid = signal<string>('');
  readonly selectedTone = signal<CoverLetterTone>('PROFESSIONAL');
  readonly personalNotes = signal<string>('');
  readonly forceRefresh = signal<boolean>(true);
  readonly deepLinkedJobDetail = signal<JobDetail | null>(null);

  // Version list filter & workspace tabs
  readonly versionFilterMode = signal<'all' | 'job'>('all');
  readonly viewMode = signal<'edit' | 'preview'>('edit');

  // Editable draft signals
  readonly draftTitle = signal<string>('');
  readonly draftTone = signal<CoverLetterTone>('PROFESSIONAL');
  readonly draftPersonalNotes = signal<string>('');
  readonly draftContent = signal<string>('');
  readonly hasUnsavedChanges = signal<boolean>(false);

  // Status, clipboard & dialogs
  readonly workspaceInitialized = signal<boolean>(false);
  readonly loadErrorMessage = signal<string | null>(null);
  readonly generateErrorMessage = signal<string | null>(null);
  readonly saveErrorMessage = signal<string | null>(null);
  readonly exportErrorMessage = signal<string | null>(null);
  readonly actionSuccessMessage = signal<string | null>(null);
  readonly copiedToClipboard = signal<boolean>(false);
  readonly pendingDeleteVersion = signal<CoverLetterSummaryDto | null>(null);

  private pendingTargetUuidFromQuery: string | null = null;
  private copyResetTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => {
      if (this.copyResetTimer !== null) {
        clearTimeout(this.copyResetTimer);
      }
    });
  }

  readonly jobOptions = computed<readonly SelectableCoverLetterJobOption[]>(() => {
    const map = new Map<number, SelectableCoverLetterJobOption>();
    for (const job of this.indexedJobs()) {
      map.set(job.id, this.toSelectableJob(job));
    }
    const deep = this.deepLinkedJobDetail();
    if (deep && !map.has(deep.id)) {
      map.set(deep.id, {
        id: deep.id,
        title: deep.title,
        companyName: deep.companyName,
        country: deep.country,
        matchPercentage: deep.matchPercentage ?? null
      });
    }
    for (const version of this.coverLetters()) {
      if (!map.has(version.jobId)) {
        map.set(version.jobId, {
          id: version.jobId,
          title: version.jobTitle,
          companyName: version.companyName,
          country: version.jobCountry ?? '',
          matchPercentage: null
        });
      }
    }
    return Array.from(map.values());
  });

  readonly selectedJobOption = computed<SelectableCoverLetterJobOption | null>(() => {
    const id = this.selectedJobId();
    if (id === null) {
      return null;
    }
    return this.jobOptions().find(item => item.id === id) ?? null;
  });

  readonly orderedTailoredResumes = computed<readonly TailoredResumeSummaryDto[]>(() => {
    const jobId = this.selectedJobId();
    const list = [...this.tailoredResumes()];
    if (jobId === null) {
      return list;
    }
    const matching = list.filter(item => item.jobId === jobId);
    const others = list.filter(item => item.jobId !== jobId);
    return [...matching, ...others];
  });

  readonly autoMatchedTailoredResume = computed<TailoredResumeSummaryDto | null>(() => {
    const jobId = this.selectedJobId();
    if (jobId === null) {
      return null;
    }
    return this.tailoredResumes().find(item => item.jobId === jobId) ?? null;
  });

  readonly selectedToneMeta = computed(
    () =>
      this.toneOptions.find(opt => opt.value === this.selectedTone()) ?? this.toneOptions[0]
  );

  readonly hasCandidateEvidence = computed<boolean>(() => {
    const p = this.profile();
    const hasProfileFacts =
      this.profileExists() &&
      Boolean(p?.currentDesignation?.trim() || p?.bio?.trim());
    return (
      hasProfileFacts ||
      this.profileSkills().length > 0 ||
      this.profileExperiences().length > 0 ||
      this.profileEducations().length > 0 ||
      this.uploadedResumes().length > 0 ||
      this.tailoredResumes().length > 0
    );
  });

  readonly lacksCandidateEvidence = computed<boolean>(
    () => this.workspaceInitialized() && !this.hasCandidateEvidence()
  );

  readonly canGenerate = computed<boolean>(
    () => this.selectedJobId() !== null && !this.isGenerating()
  );

  readonly canSave = computed<boolean>(
    () =>
      Boolean(this.activeCoverLetter()) &&
      !this.isSaving() &&
      this.draftContent().trim().length > 0
  );

  readonly liveWordCount = computed<number>(() => {
    const trimmed = this.draftContent().trim();
    return trimmed ? trimmed.split(/\s+/).length : 0;
  });

  readonly liveCharCount = computed<number>(() => this.draftContent().length);

  readonly activeMetadata = computed<CoverLetterMetadataDto | null>(
    () => this.activeCoverLetter()?.metadata ?? null
  );

  readonly keyAlignmentPoints = computed<readonly string[]>(
    () => this.activeMetadata()?.keyAlignmentPoints ?? []
  );

  readonly highlightedSkills = computed<readonly string[]>(
    () => this.activeMetadata()?.highlightedSkills ?? []
  );

  readonly groundingWarnings = computed<readonly string[]>(
    () => this.activeMetadata()?.groundingWarnings ?? []
  );

  readonly deleteDialogMessage = computed<string>(() => {
    const target = this.pendingDeleteVersion();
    if (!target) {
      return 'Delete this cover letter draft?';
    }
    return `Delete cover letter "${target.title}" (v${target.versionNumber}) for ${target.companyName}?`;
  });

  readonly headerBadge = computed<string>(() => {
    const count = this.coverLetters().length;
    if (count === 0) {
      return 'Company-Aligned Prose';
    }
    return `${count} Saved ${count === 1 ? 'Draft' : 'Drafts'}`;
  });

  ngOnInit(): void {
    this.loadInitialWorkspace();

    if (this.route?.queryParamMap) {
      this.route.queryParamMap
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(params => {
          const rawJobId = params.get('jobId');
          const parsedJobId = rawJobId ? Number(rawJobId) : NaN;
          if (Number.isInteger(parsedJobId) && parsedJobId > 0) {
            this.selectedJobId.set(parsedJobId);
            this.loadDeepLinkedJob(parsedJobId);
          }

          const rawTailoredUuid =
            params.get('tailoredResumeUuid') ?? params.get('tailoredUuid');
          if (rawTailoredUuid && rawTailoredUuid.trim().length > 0) {
            this.selectedTailoredResumeUuid.set(rawTailoredUuid.trim());
          }

          const rawCoverLetterUuid = params.get('coverLetterUuid');
          if (rawCoverLetterUuid && rawCoverLetterUuid.trim().length > 0) {
            this.pendingTargetUuidFromQuery = rawCoverLetterUuid.trim();
            this.selectCoverLetter(this.pendingTargetUuidFromQuery);
          }
        });
    }
  }

  reloadWorkspace(): void {
    this.loadInitialWorkspace();
  }

  onJobSelectChange(event: Event): void {
    const raw = (event.target as HTMLSelectElement).value;
    const id = raw ? Number(raw) : NaN;
    const nextJobId = Number.isInteger(id) && id > 0 ? id : null;
    this.selectedJobId.set(nextJobId);
    this.generateErrorMessage.set(null);

    const currentTailoredUuid = this.selectedTailoredResumeUuid();
    if (currentTailoredUuid) {
      const matched = this.tailoredResumes().find(item => item.uuid === currentTailoredUuid);
      if (matched && nextJobId !== null && matched.jobId !== nextJobId) {
        this.selectedTailoredResumeUuid.set('');
      }
    }

    if (this.versionFilterMode() === 'job') {
      this.refreshVersionList();
    }
  }

  onTailoredResumeSelectChange(event: Event): void {
    this.selectedTailoredResumeUuid.set((event.target as HTMLSelectElement).value);
    this.generateErrorMessage.set(null);
  }

  onToneSelectChange(event: Event): void {
    this.selectedTone.set((event.target as HTMLSelectElement).value as CoverLetterTone);
  }

  onPersonalNotesInput(event: Event): void {
    this.personalNotes.set((event.target as HTMLTextAreaElement).value);
  }

  onForceRefreshChange(event: Event): void {
    this.forceRefresh.set((event.target as HTMLInputElement).checked);
  }

  setVersionFilterMode(mode: 'all' | 'job'): void {
    if (this.versionFilterMode() === mode) {
      return;
    }
    this.versionFilterMode.set(mode);
    this.refreshVersionList();
  }

  setViewMode(mode: 'edit' | 'preview'): void {
    this.viewMode.set(mode);
  }

  generateCoverLetter(): void {
    const jobId = this.selectedJobId();
    if (jobId === null || this.isGenerating()) {
      return;
    }

    const tailoredUuid = this.selectedTailoredResumeUuid().trim() || null;
    const notes = this.personalNotes().trim() || null;

    this.generateErrorMessage.set(null);
    this.saveErrorMessage.set(null);
    this.exportErrorMessage.set(null);
    this.actionSuccessMessage.set(null);

    this.coverLetterService
      .generateCoverLetter({
        jobId,
        tailoredResumeUuid: tailoredUuid,
        tone: this.selectedTone(),
        personalNotes: notes,
        forceRefresh: this.forceRefresh()
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: generated => {
          this.syncEditorFromResponse(generated);
          this.actionSuccessMessage.set(
            `Generated grounded cover letter "${generated.title}" (${generated.wordCount} words).`
          );
        },
        error: (err: unknown) => {
          this.generateErrorMessage.set(
            extractApiErrorMessage(
              err,
              'Unable to generate cover letter right now. Verify AI service status or add profile/resume evidence.'
            )
          );
        }
      });
  }

  selectCoverLetter(uuid: string): void {
    this.saveErrorMessage.set(null);
    this.exportErrorMessage.set(null);
    this.generateErrorMessage.set(null);
    this.coverLetterService
      .getCoverLetter(uuid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: detail => {
          this.selectedJobId.set(detail.jobId);
          this.syncEditorFromResponse(detail);
        },
        error: (err: unknown) => {
          this.saveErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to load the selected cover letter draft.')
          );
        }
      });
  }

  onDraftTitleInput(event: Event): void {
    this.draftTitle.set((event.target as HTMLInputElement).value);
    this.markDirty();
  }

  onDraftToneChange(event: Event): void {
    this.draftTone.set((event.target as HTMLSelectElement).value as CoverLetterTone);
    this.markDirty();
  }

  onDraftPersonalNotesInput(event: Event): void {
    this.draftPersonalNotes.set((event.target as HTMLTextAreaElement).value);
    this.markDirty();
  }

  onDraftContentInput(event: Event): void {
    this.draftContent.set((event.target as HTMLTextAreaElement).value);
    this.markDirty();
  }

  resetUnsavedEdits(): void {
    const active = this.activeCoverLetter();
    if (active) {
      this.syncEditorFromResponse(active);
      this.saveErrorMessage.set(null);
      this.exportErrorMessage.set(null);
      this.actionSuccessMessage.set('Reverted unsaved cover letter edits to last saved version.');
    }
  }

  saveDraftChanges(): void {
    const active = this.activeCoverLetter();
    if (!active || !this.canSave()) {
      return;
    }

    const payload = this.buildUpdatePayload(active);
    this.saveErrorMessage.set(null);
    this.exportErrorMessage.set(null);
    this.actionSuccessMessage.set(null);

    this.coverLetterService
      .updateCoverLetter(active.uuid, payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.syncEditorFromResponse(updated);
          this.actionSuccessMessage.set(
            `Saved edits to "${updated.title}" (${updated.wordCount} words).`
          );
        },
        error: (err: unknown) => {
          this.saveErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to save cover letter edits. Check field limits and retry.')
          );
        }
      });
  }

  async copyDraftToClipboard(): Promise<void> {
    const text = this.draftContent().trim();
    if (!text) {
      return;
    }

    this.saveErrorMessage.set(null);
    let copied = false;

    const clipboard = globalThis.navigator?.clipboard;
    if (clipboard && typeof clipboard.writeText === 'function') {
      try {
        await clipboard.writeText(text);
        copied = true;
      } catch {
        copied = false;
      }
    }

    if (!copied) {
      copied = this.copyUsingFallbackTextarea(text);
    }

    if (copied) {
      this.markCopiedFeedback();
      this.actionSuccessMessage.set('Copied cover letter text to clipboard.');
    } else {
      this.saveErrorMessage.set('Unable to copy text to clipboard in this browser.');
    }
  }

  exportDocument(format: CoverLetterExportFormat): void {
    const active = this.activeCoverLetter();
    if (!active || this.isExporting() || this.isSaving()) {
      return;
    }

    if (this.hasUnsavedChanges() && this.draftContent().trim().length === 0) {
      this.exportErrorMessage.set('Cover letter content must not be blank before exporting.');
      return;
    }

    this.exportErrorMessage.set(null);
    this.saveErrorMessage.set(null);
    this.actionSuccessMessage.set(null);

    if (this.hasUnsavedChanges()) {
      const payload = this.buildUpdatePayload(active);
      this.coverLetterService
        .updateCoverLetter(active.uuid, payload)
        .pipe(
          tap(updated => this.syncEditorFromResponse(updated)),
          switchMap(updated =>
            this.coverLetterService.exportCoverLetter(
              updated.uuid,
              format,
              this.buildFallbackExportFilename(updated.title, format)
            )
          ),
          takeUntilDestroyed(this.destroyRef)
        )
        .subscribe({
          next: exported => this.handleExportSuccess(exported, true),
          error: (err: unknown) => this.handleExportError(err, format)
        });
      return;
    }

    this.coverLetterService
      .exportCoverLetter(
        active.uuid,
        format,
        this.buildFallbackExportFilename(active.title, format)
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: exported => this.handleExportSuccess(exported, false),
        error: (err: unknown) => this.handleExportError(err, format)
      });
  }

  requestDeleteVersion(version: CoverLetterSummaryDto, event: Event): void {
    event.stopPropagation();
    this.pendingDeleteVersion.set(version);
  }

  cancelDeleteVersion(): void {
    if (!this.deletingUuid()) {
      this.pendingDeleteVersion.set(null);
    }
  }

  confirmDeleteVersion(): void {
    const target = this.pendingDeleteVersion();
    if (!target || this.deletingUuid()) {
      return;
    }

    const wasActive = this.activeCoverLetter()?.uuid === target.uuid;
    this.coverLetterService
      .deleteCoverLetter(target.uuid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.pendingDeleteVersion.set(null);
          this.actionSuccessMessage.set(`Deleted cover letter "${target.title}".`);
          if (wasActive) {
            const nextSummary = this.coverLetters()[0] ?? null;
            if (nextSummary) {
              this.selectCoverLetter(nextSummary.uuid);
            } else {
              this.clearEditorDraft();
            }
          }
        },
        error: (err: unknown) => {
          this.pendingDeleteVersion.set(null);
          this.saveErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to delete cover letter draft.')
          );
        }
      });
  }

  formatToneLabel(tone: CoverLetterTone | string | null | undefined): string {
    return formatCoverLetterToneLabel(tone);
  }

  private loadInitialWorkspace(): void {
    this.loadErrorMessage.set(null);

    forkJoin({
      jobsPage: this.jobService
        .searchJobs({ sort: 'matchScore,desc' }, 0, 50)
        .pipe(catchError(() => of(null))),
      tailored: this.tailoredResumeService
        .loadTailoredResumes(null)
        .pipe(catchError(() => of([] as TailoredResumeSummaryDto[]))),
      resumes: this.resumeService
        .loadResumes()
        .pipe(catchError(() => of([] as ResumeResponse[]))),
      profile: this.profileService.loadProfile().pipe(catchError(() => of(null))),
      coverLetters: this.coverLetterService.loadCoverLetters(null).pipe(
        catchError(err => {
          this.loadErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to load saved cover letter drafts.')
          );
          return of([] as CoverLetterSummaryDto[]);
        })
      )
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ jobsPage, coverLetters }) => {
        this.workspaceInitialized.set(true);

        if (this.selectedJobId() === null && jobsPage && jobsPage.content.length > 0) {
          this.selectedJobId.set(jobsPage.content[0].id);
        }

        if (!this.pendingTargetUuidFromQuery && !this.activeCoverLetter() && coverLetters.length > 0) {
          const targetJob = this.selectedJobId();
          const matchingVersion =
            targetJob !== null ? coverLetters.find(item => item.jobId === targetJob) : undefined;
          const initialVersion = matchingVersion ?? coverLetters[0];
          this.selectCoverLetter(initialVersion.uuid);
        }
      });
  }

  private loadDeepLinkedJob(jobId: number): void {
    const alreadyInIndex = this.indexedJobs().some(job => job.id === jobId);
    if (alreadyInIndex) {
      return;
    }
    this.jobService
      .getJobDetail(jobId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        catchError(() => of(null))
      )
      .subscribe(detail => {
        if (detail) {
          this.deepLinkedJobDetail.set(detail);
        }
      });
  }

  private refreshVersionList(): void {
    const filterJobId = this.versionFilterMode() === 'job' ? this.selectedJobId() : null;
    this.coverLetterService
      .loadCoverLetters(filterJobId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: (err: unknown) => {
          this.loadErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to refresh cover letter versions.')
          );
        }
      });
  }

  private syncEditorFromResponse(response: CoverLetterResponse): void {
    this.draftTitle.set(response.title);
    this.draftTone.set(response.tone ?? 'PROFESSIONAL');
    this.draftPersonalNotes.set(response.personalNotes ?? '');
    this.draftContent.set(response.content ?? '');
    this.hasUnsavedChanges.set(false);
    this.copiedToClipboard.set(false);
  }

  private clearEditorDraft(): void {
    this.draftTitle.set('');
    this.draftTone.set('PROFESSIONAL');
    this.draftPersonalNotes.set('');
    this.draftContent.set('');
    this.hasUnsavedChanges.set(false);
    this.copiedToClipboard.set(false);
  }

  private buildUpdatePayload(active: CoverLetterResponse): UpdateCoverLetterRequest {
    return {
      title: this.draftTitle().trim() || active.title,
      tone: this.draftTone(),
      personalNotes: this.draftPersonalNotes().trim(),
      content: this.draftContent().trim()
    };
  }

  private markDirty(): void {
    this.hasUnsavedChanges.set(true);
    this.copiedToClipboard.set(false);
    this.actionSuccessMessage.set(null);
  }

  private markCopiedFeedback(): void {
    this.copiedToClipboard.set(true);
    if (this.copyResetTimer !== null) {
      clearTimeout(this.copyResetTimer);
    }
    this.ngZone.runOutsideAngular(() => {
      this.copyResetTimer = setTimeout(() => {
        this.ngZone.run(() => {
          this.copiedToClipboard.set(false);
          this.copyResetTimer = null;
        });
      }, 2200);
    });
  }

  private copyUsingFallbackTextarea(text: string): boolean {
    const body = this.document.body;
    if (!body || typeof this.document.execCommand !== 'function') {
      return false;
    }
    const textarea = this.document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    body.appendChild(textarea);
    textarea.select();
    let ok = false;
    try {
      ok = this.document.execCommand('copy');
    } catch {
      ok = false;
    } finally {
      body.removeChild(textarea);
    }
    return ok;
  }

  private handleExportSuccess(file: CoverLetterExportFile, savedFirst: boolean): void {
    this.triggerBrowserDownload(file);
    const label = file.format.toUpperCase();
    this.actionSuccessMessage.set(
      savedFirst
        ? `Saved draft edits and exported ${label} (${file.fileName}).`
        : `Exported ${label} (${file.fileName}).`
    );
  }

  private handleExportError(err: unknown, format: CoverLetterExportFormat): void {
    this.exportErrorMessage.set(
      extractApiErrorMessage(
        err,
        `Unable to export cover letter as ${format.toUpperCase()}. Please retry.`
      )
    );
  }

  private triggerBrowserDownload(file: CoverLetterExportFile): void {
    const urlFactory = globalThis.URL;
    if (typeof urlFactory?.createObjectURL !== 'function') {
      return;
    }
    const objectUrl = urlFactory.createObjectURL(file.blob);
    const link = this.document.createElement('a');
    link.href = objectUrl;
    link.download = file.fileName;
    link.style.display = 'none';
    this.document.body?.appendChild(link);
    link.click();
    this.document.body?.removeChild(link);
    setTimeout(() => urlFactory.revokeObjectURL(objectUrl), 100);
  }

  private buildFallbackExportFilename(title: string, format: CoverLetterExportFormat): string {
    const slug = (title || 'cover-letter')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 60);
    return `${slug || 'cover-letter'}.${format}`;
  }

  private toSelectableJob(job: JobSummary): SelectableCoverLetterJobOption {
    return {
      id: job.id,
      title: job.title,
      companyName: job.companyName,
      country: job.country,
      matchPercentage: job.matchPercentage
    };
  }
}
