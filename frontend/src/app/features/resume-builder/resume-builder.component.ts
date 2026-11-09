import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
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
import { JobDetail, JobSummary } from '../../core/models/job.models';
import { ResumeResponse } from '../../core/models/resume.models';
import {
  RESUME_TEMPLATE_OPTIONS,
  ResumeExportFormat,
  ResumeTemplate,
  TailoredResumeDataDto,
  TailoredResumeEducationDto,
  TailoredResumeExperienceDto,
  TailoredResumeExportFile,
  TailoredResumeProjectDto,
  TailoredResumeResponse,
  TailoredResumeSummaryDto,
  UpdateTailoredResumeRequest,
  formatResumeTemplateLabel
} from '../../core/models/tailored-resume.models';
import { JobService } from '../../core/services/job.service';
import { ResumeService } from '../../core/services/resume.service';
import { TailoredResumeService } from '../../core/services/tailored-resume.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { MatchScoreGaugeComponent } from '../../shared/components/match-score-gauge/match-score-gauge.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';
import { TailoredCvPreviewComponent } from './components/tailored-cv-preview/tailored-cv-preview.component';

export interface SelectableJobOption {
  readonly id: number;
  readonly title: string;
  readonly companyName: string;
  readonly country: string;
  readonly matchPercentage: number | null;
}

@Component({
  selector: 'app-resume-builder',
  standalone: true,
  imports: [
    RouterLink,
    PageHeaderComponent,
    MatchScoreGaugeComponent,
    ConfirmDialogComponent,
    TailoredCvPreviewComponent,
    TimeAgoPipe
  ],
  templateUrl: './resume-builder.component.html',
  styleUrl: './resume-builder.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResumeBuilderComponent implements OnInit {
  private readonly tailoredService = inject(TailoredResumeService);
  private readonly resumeService = inject(ResumeService);
  private readonly jobService = inject(JobService);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  readonly templateOptions = RESUME_TEMPLATE_OPTIONS;

  readonly tailoredResumes = this.tailoredService.resumes;
  readonly activeResume = this.tailoredService.selectedResume;
  readonly isLoadingList = this.tailoredService.isLoadingList;
  readonly isLoadingDetail = this.tailoredService.isLoadingDetail;
  readonly isGenerating = this.tailoredService.isGenerating;
  readonly isSaving = this.tailoredService.isSaving;
  readonly exportingFormat = this.tailoredService.exportingFormat;
  readonly isExporting = this.tailoredService.isExporting;
  readonly deletingUuid = this.tailoredService.deletingUuid;
  readonly allUploadedResumes = this.resumeService.resumes;
  readonly indexedJobs = this.jobService.jobs;

  // Generator controls
  readonly selectedJobId = signal<number | null>(null);
  readonly selectedMasterUuid = signal<string>('');
  readonly selectedTemplate = signal<ResumeTemplate>('MODERN');
  readonly tailoringFocus = signal<string>('');
  readonly forceRefresh = signal<boolean>(true);
  readonly deepLinkedJobDetail = signal<JobDetail | null>(null);

  // Version list filter mode
  readonly versionFilterMode = signal<'all' | 'job'>('all');

  // Workspace view & comparison toggles
  readonly viewMode = signal<'edit' | 'preview'>('edit');
  readonly expandedOriginalExpIndices = signal<ReadonlySet<number>>(new Set());

  // Editable draft form state
  readonly draftTitle = signal<string>('');
  readonly draftTemplate = signal<ResumeTemplate>('MODERN');
  readonly draftSummary = signal<string>('');
  readonly draftHighlightedSkills = signal<string[]>([]);
  readonly draftAdditionalSkills = signal<string[]>([]);
  readonly draftExperiences = signal<TailoredResumeExperienceDto[]>([]);
  readonly draftProjects = signal<TailoredResumeProjectDto[]>([]);
  readonly draftEducations = signal<TailoredResumeEducationDto[]>([]);
  readonly draftLanguages = signal<string[]>([]);
  readonly draftCertifications = signal<string[]>([]);
  readonly hasUnsavedChanges = signal<boolean>(false);

  // Skill/tag draft inputs
  readonly newHighlightedSkillInput = signal<string>('');
  readonly newAdditionalSkillInput = signal<string>('');
  readonly newLanguageInput = signal<string>('');
  readonly newCertificationInput = signal<string>('');

  // Status & confirmation dialogs
  readonly loadErrorMessage = signal<string | null>(null);
  readonly generateErrorMessage = signal<string | null>(null);
  readonly saveErrorMessage = signal<string | null>(null);
  readonly exportErrorMessage = signal<string | null>(null);
  readonly actionSuccessMessage = signal<string | null>(null);
  readonly pendingDeleteVersion = signal<TailoredResumeSummaryDto | null>(null);

  private pendingTargetUuidFromQuery: string | null = null;

  readonly masterResumes = computed<readonly ResumeResponse[]>(() =>
    this.allUploadedResumes().filter(item => item.resumeType === 'MASTER')
  );

  readonly approvedMasterResume = computed<ResumeResponse | null>(
    () => this.masterResumes().find(item => item.isMasterApproved) ?? null
  );

  readonly jobOptions = computed<readonly SelectableJobOption[]>(() => {
    const map = new Map<number, SelectableJobOption>();
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
    for (const version of this.tailoredResumes()) {
      if (!map.has(version.jobId)) {
        map.set(version.jobId, {
          id: version.jobId,
          title: version.jobTitle,
          companyName: version.companyName,
          country: version.jobCountry ?? '',
          matchPercentage: version.matchScore ?? null
        });
      }
    }
    return Array.from(map.values());
  });

  readonly selectedJobOption = computed<SelectableJobOption | null>(() => {
    const id = this.selectedJobId();
    if (id === null) {
      return null;
    }
    return this.jobOptions().find(item => item.id === id) ?? null;
  });

  readonly selectedTemplateMeta = computed(
    () =>
      this.templateOptions.find(opt => opt.value === this.selectedTemplate()) ??
      this.templateOptions[0]
  );

  readonly canGenerate = computed<boolean>(
    () => this.selectedJobId() !== null && this.masterResumes().length > 0 && !this.isGenerating()
  );

  readonly deleteDialogMessage = computed<string>(() => {
    const target = this.pendingDeleteVersion();
    if (!target) {
      return 'Delete this tailored resume version?';
    }
    return `Delete tailored draft "${target.title}" (v${target.versionNumber}) for ${target.companyName}? Your source Master CV will not be affected.`;
  });

  readonly matchedKeywordsList = computed<readonly string[]>(() => {
    const active = this.activeResume();
    return active?.resumeData?.matchedKeywords ?? [];
  });

  readonly missingKeywordsList = computed<readonly string[]>(() => {
    const active = this.activeResume();
    return active?.missingKeywords ?? active?.resumeData?.missingKeywords ?? [];
  });

  readonly groundingWarningsList = computed<readonly string[]>(() => {
    const active = this.activeResume();
    return active?.resumeData?.groundingWarnings ?? [];
  });

  readonly previewEducations = computed<readonly TailoredResumeEducationDto[]>(
    () => this.draftEducations()
  );

  readonly previewHeader = computed(() => this.activeResume()?.resumeData?.header ?? null);

  readonly headerBadge = computed<string>(() => {
    const count = this.tailoredResumes().length;
    if (count === 0) {
      return 'Anti-Hallucination Grounded';
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

          const rawUuid = params.get('tailoredUuid');
          if (rawUuid && rawUuid.trim().length > 0) {
            this.pendingTargetUuidFromQuery = rawUuid.trim();
            this.selectTailoredResume(this.pendingTargetUuidFromQuery);
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
    this.selectedJobId.set(Number.isInteger(id) && id > 0 ? id : null);
    this.generateErrorMessage.set(null);

    if (this.versionFilterMode() === 'job') {
      this.refreshVersionList();
    }
  }

  onMasterSelectChange(event: Event): void {
    this.selectedMasterUuid.set((event.target as HTMLSelectElement).value);
    this.generateErrorMessage.set(null);
  }

  onTemplateSelectChange(event: Event): void {
    this.selectedTemplate.set((event.target as HTMLSelectElement).value as ResumeTemplate);
  }

  onFocusInput(event: Event): void {
    this.tailoringFocus.set((event.target as HTMLTextAreaElement).value);
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

  generateTailoredResume(): void {
    const jobId = this.selectedJobId();
    if (jobId === null || this.isGenerating()) {
      return;
    }

    const masterUuid = this.selectedMasterUuid().trim() || null;
    const focus = this.tailoringFocus().trim() || null;

    this.generateErrorMessage.set(null);
    this.saveErrorMessage.set(null);
    this.actionSuccessMessage.set(null);

    this.tailoredService
      .generateTailoredResume({
        jobId,
        masterResumeUuid: masterUuid,
        templateName: this.selectedTemplate(),
        tailoringFocus: focus,
        forceRefresh: this.forceRefresh()
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: generated => {
          this.syncEditorFromResponse(generated);
          const modeLabel = generated.editedByUser ? 'draft' : `v${generated.versionNumber}`;
          this.actionSuccessMessage.set(
            `Generated grounded tailored CV "${generated.title}" (${modeLabel}, ATS coverage ${this.formatCoverage(generated.atsKeywordCoverage)}%).`
          );
        },
        error: (err: unknown) => {
          this.generateErrorMessage.set(
            extractApiErrorMessage(
              err,
              'Unable to generate tailored resume right now. Check AI service status or upload a master CV.'
            )
          );
        }
      });
  }

  selectTailoredResume(uuid: string): void {
    this.saveErrorMessage.set(null);
    this.exportErrorMessage.set(null);
    this.generateErrorMessage.set(null);
    this.tailoredService
      .getTailoredResume(uuid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: detail => {
          this.selectedJobId.set(detail.jobId);
          this.syncEditorFromResponse(detail);
        },
        error: (err: unknown) => {
          this.saveErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to load the selected tailored resume draft.')
          );
        }
      });
  }

  resetUnsavedEdits(): void {
    const active = this.activeResume();
    if (active) {
      this.syncEditorFromResponse(active);
      this.saveErrorMessage.set(null);
      this.exportErrorMessage.set(null);
      this.actionSuccessMessage.set('Reverted unsaved draft edits to last saved version.');
    }
  }

  saveDraftChanges(): void {
    const active = this.activeResume();
    if (!active || this.isSaving()) {
      return;
    }

    const payload = this.buildUpdatePayload(active);
    this.saveErrorMessage.set(null);
    this.exportErrorMessage.set(null);
    this.actionSuccessMessage.set(null);

    this.tailoredService
      .updateTailoredResume(active.uuid, payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.syncEditorFromResponse(updated);
          this.actionSuccessMessage.set(
            `Saved changes to "${updated.title}" — ATS coverage recalculated to ${this.formatCoverage(updated.atsKeywordCoverage)}%.`
          );
        },
        error: (err: unknown) => {
          this.saveErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to save draft edits. Check field lengths and retry.')
          );
        }
      });
  }

  exportDocument(format: ResumeExportFormat, regenerate = false): void {
    const active = this.activeResume();
    if (!active || this.isExporting() || this.isSaving()) {
      return;
    }

    this.exportErrorMessage.set(null);
    this.saveErrorMessage.set(null);
    this.actionSuccessMessage.set(null);

    if (this.hasUnsavedChanges()) {
      const payload = this.buildUpdatePayload(active);
      this.tailoredService
        .updateTailoredResume(active.uuid, payload)
        .pipe(
          tap(updated => this.syncEditorFromResponse(updated)),
          switchMap(updated =>
            this.tailoredService.exportTailoredResume(
              updated.uuid,
              format,
              null,
              false,
              this.buildFallbackExportFilename(updated.title, updated.templateName, format)
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

    this.tailoredService
      .exportTailoredResume(
        active.uuid,
        format,
        null,
        regenerate,
        this.buildFallbackExportFilename(active.title, active.templateName, format)
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: exported => this.handleExportSuccess(exported, false),
        error: (err: unknown) => this.handleExportError(err, format)
      });
  }

  onPreviewTemplateSelected(template: ResumeTemplate): void {
    if (this.draftTemplate() !== template) {
      this.draftTemplate.set(template);
      this.markDirty();
    }
  }

  requestDeleteVersion(version: TailoredResumeSummaryDto, event: Event): void {
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

    const wasActive = this.activeResume()?.uuid === target.uuid;
    this.tailoredService
      .deleteTailoredResume(target.uuid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.pendingDeleteVersion.set(null);
          this.actionSuccessMessage.set(`Deleted tailored draft "${target.title}".`);
          if (wasActive) {
            const nextSummary = this.tailoredResumes()[0] ?? null;
            if (nextSummary) {
              this.selectTailoredResume(nextSummary.uuid);
            } else {
              this.clearEditorDraft();
            }
          }
        },
        error: (err: unknown) => {
          this.pendingDeleteVersion.set(null);
          this.saveErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to delete tailored resume draft.')
          );
        }
      });
  }

  // Draft field mutations
  onDraftTitleInput(event: Event): void {
    this.draftTitle.set((event.target as HTMLInputElement).value);
    this.markDirty();
  }

  onDraftTemplateChange(event: Event): void {
    this.draftTemplate.set((event.target as HTMLSelectElement).value as ResumeTemplate);
    this.markDirty();
  }

  onDraftSummaryInput(event: Event): void {
    this.draftSummary.set((event.target as HTMLTextAreaElement).value);
    this.markDirty();
  }

  onSkillInputChange(kind: 'highlighted' | 'additional' | 'language' | 'certification', event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    switch (kind) {
      case 'highlighted':
        this.newHighlightedSkillInput.set(value);
        break;
      case 'additional':
        this.newAdditionalSkillInput.set(value);
        break;
      case 'language':
        this.newLanguageInput.set(value);
        break;
      case 'certification':
        this.newCertificationInput.set(value);
        break;
    }
  }

  addChipItem(kind: 'highlighted' | 'additional' | 'language' | 'certification'): void {
    const raw =
      kind === 'highlighted'
        ? this.newHighlightedSkillInput()
        : kind === 'additional'
          ? this.newAdditionalSkillInput()
          : kind === 'language'
            ? this.newLanguageInput()
            : this.newCertificationInput();

    const parts = raw
      .split(',')
      .map(s => s.trim())
      .filter(s => s.length > 0);
    if (parts.length === 0) {
      return;
    }

    if (kind === 'highlighted') {
      this.draftHighlightedSkills.update(cur => this.appendUniqueStrings(cur, parts, 40));
      this.newHighlightedSkillInput.set('');
    } else if (kind === 'additional') {
      this.draftAdditionalSkills.update(cur => this.appendUniqueStrings(cur, parts, 40));
      this.newAdditionalSkillInput.set('');
    } else if (kind === 'language') {
      this.draftLanguages.update(cur => this.appendUniqueStrings(cur, parts, 20));
      this.newLanguageInput.set('');
    } else {
      this.draftCertifications.update(cur => this.appendUniqueStrings(cur, parts, 20));
      this.newCertificationInput.set('');
    }
    this.markDirty();
  }

  removeChipItem(
    kind: 'highlighted' | 'additional' | 'language' | 'certification',
    index: number
  ): void {
    const removeAt = (list: string[]) => list.filter((_, i) => i !== index);
    if (kind === 'highlighted') {
      this.draftHighlightedSkills.update(removeAt);
    } else if (kind === 'additional') {
      this.draftAdditionalSkills.update(removeAt);
    } else if (kind === 'language') {
      this.draftLanguages.update(removeAt);
    } else {
      this.draftCertifications.update(removeAt);
    }
    this.markDirty();
  }

  toggleOriginalBullets(expIndex: number): void {
    this.expandedOriginalExpIndices.update(cur => {
      const next = new Set(cur);
      if (next.has(expIndex)) {
        next.delete(expIndex);
      } else {
        next.add(expIndex);
      }
      return next;
    });
  }

  isOriginalBulletsExpanded(expIndex: number): boolean {
    return this.expandedOriginalExpIndices().has(expIndex);
  }

  updateExperienceBullet(expIndex: number, bulletIndex: number, event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    this.draftExperiences.update(list =>
      list.map((exp, idx) => {
        if (idx !== expIndex) {
          return exp;
        }
        const bullets = exp.tailoredBullets.map((b, bIdx) => (bIdx === bulletIndex ? value : b));
        return { ...exp, tailoredBullets: bullets };
      })
    );
    this.markDirty();
  }

  addExperienceBullet(expIndex: number): void {
    this.draftExperiences.update(list =>
      list.map((exp, idx) => {
        if (idx !== expIndex || exp.tailoredBullets.length >= 15) {
          return exp;
        }
        return { ...exp, tailoredBullets: [...exp.tailoredBullets, ''] };
      })
    );
    this.markDirty();
  }

  removeExperienceBullet(expIndex: number, bulletIndex: number): void {
    this.draftExperiences.update(list =>
      list.map((exp, idx) => {
        if (idx !== expIndex) {
          return exp;
        }
        return {
          ...exp,
          tailoredBullets: exp.tailoredBullets.filter((_, bIdx) => bIdx !== bulletIndex)
        };
      })
    );
    this.markDirty();
  }

  restoreOriginalExperienceBullets(expIndex: number): void {
    this.draftExperiences.update(list =>
      list.map((exp, idx) => {
        if (idx !== expIndex || !exp.originalBullets.length) {
          return exp;
        }
        return { ...exp, tailoredBullets: [...exp.originalBullets] };
      })
    );
    this.markDirty();
  }

  moveExperience(expIndex: number, delta: -1 | 1): void {
    const targetIndex = expIndex + delta;
    const current = this.draftExperiences();
    if (targetIndex < 0 || targetIndex >= current.length) {
      return;
    }
    this.draftExperiences.set(this.swapArrayItems(current, expIndex, targetIndex));
    this.expandedOriginalExpIndices.set(new Set());
    this.markDirty();
  }

  moveExperienceBullet(expIndex: number, bulletIndex: number, delta: -1 | 1): void {
    const targetBulletIndex = bulletIndex + delta;
    this.draftExperiences.update(list =>
      list.map((exp, idx) => {
        if (idx !== expIndex || targetBulletIndex < 0 || targetBulletIndex >= exp.tailoredBullets.length) {
          return exp;
        }
        return {
          ...exp,
          tailoredBullets: this.swapArrayItems(exp.tailoredBullets, bulletIndex, targetBulletIndex)
        };
      })
    );
    this.markDirty();
  }

  moveEducation(eduIndex: number, delta: -1 | 1): void {
    const targetIndex = eduIndex + delta;
    const current = this.draftEducations();
    if (targetIndex < 0 || targetIndex >= current.length) {
      return;
    }
    this.draftEducations.set(this.swapArrayItems(current, eduIndex, targetIndex));
    this.markDirty();
  }

  updateProjectDescription(projIndex: number, event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    this.draftProjects.update(list =>
      list.map((proj, idx) => (idx === projIndex ? { ...proj, description: value } : proj))
    );
    this.markDirty();
  }

  updateProjectBullet(projIndex: number, bulletIndex: number, event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    this.draftProjects.update(list =>
      list.map((proj, idx) => {
        if (idx !== projIndex) {
          return proj;
        }
        return {
          ...proj,
          tailoredBullets: proj.tailoredBullets.map((b, bIdx) => (bIdx === bulletIndex ? value : b))
        };
      })
    );
    this.markDirty();
  }

  addProjectBullet(projIndex: number): void {
    this.draftProjects.update(list =>
      list.map((proj, idx) => {
        if (idx !== projIndex || proj.tailoredBullets.length >= 10) {
          return proj;
        }
        return { ...proj, tailoredBullets: [...proj.tailoredBullets, ''] };
      })
    );
    this.markDirty();
  }

  removeProjectBullet(projIndex: number, bulletIndex: number): void {
    this.draftProjects.update(list =>
      list.map((proj, idx) => {
        if (idx !== projIndex) {
          return proj;
        }
        return {
          ...proj,
          tailoredBullets: proj.tailoredBullets.filter((_, bIdx) => bIdx !== bulletIndex)
        };
      })
    );
    this.markDirty();
  }

  formatTemplateName(template: ResumeTemplate | string | null | undefined): string {
    return formatResumeTemplateLabel(template);
  }

  formatCoverage(value: number | null | undefined): string {
    if (value === null || value === undefined || !Number.isFinite(value)) {
      return '0';
    }
    return String(Math.round(value));
  }

  formatDateRange(startDate: string | null, endDate: string | null, current: boolean): string {
    const start = this.formatMonthYear(startDate);
    if (current) {
      return start ? `${start} – Present` : 'Present';
    }
    const end = this.formatMonthYear(endDate);
    if (start && end) {
      return `${start} – ${end}`;
    }
    return start || end || '';
  }

  private loadInitialWorkspace(): void {
    this.loadErrorMessage.set(null);

    forkJoin({
      resumes: this.resumeService.loadResumes().pipe(catchError(() => of([] as ResumeResponse[]))),
      jobsPage: this.jobService
        .searchJobs({ sort: 'matchScore,desc' }, 0, 50)
        .pipe(catchError(() => of(null))),
      tailored: this.tailoredService.loadTailoredResumes(null).pipe(
        catchError(err => {
          this.loadErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to load saved tailored resume versions.')
          );
          return of([] as TailoredResumeSummaryDto[]);
        })
      )
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ resumes, jobsPage, tailored }) => {
        const masters = resumes.filter(r => r.resumeType === 'MASTER');
        if (!this.selectedMasterUuid() && masters.length > 0) {
          const preferredMaster = masters.find(m => m.isMasterApproved) ?? masters[0];
          this.selectedMasterUuid.set(preferredMaster.uuid);
        }

        if (this.selectedJobId() === null && jobsPage && jobsPage.content.length > 0) {
          this.selectedJobId.set(jobsPage.content[0].id);
        }

        if (!this.pendingTargetUuidFromQuery && !this.activeResume() && tailored.length > 0) {
          const targetJob = this.selectedJobId();
          const matchingVersion =
            targetJob !== null ? tailored.find(item => item.jobId === targetJob) : undefined;
          const initialVersion = matchingVersion ?? tailored[0];
          this.selectTailoredResume(initialVersion.uuid);
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
    this.tailoredService
      .loadTailoredResumes(filterJobId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: (err: unknown) => {
          this.loadErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to refresh tailored resume versions.')
          );
        }
      });
  }

  private syncEditorFromResponse(response: TailoredResumeResponse): void {
    const data: TailoredResumeDataDto | null = response.resumeData;
    this.draftTitle.set(response.title);
    this.draftTemplate.set(response.templateName ?? 'MODERN');
    this.draftSummary.set(data?.tailoredSummary ?? response.tailoredSummary ?? '');
    this.draftHighlightedSkills.set([
      ...(data?.highlightedSkills ?? response.highlightedSkills ?? [])
    ]);
    this.draftAdditionalSkills.set([...(data?.additionalSkills ?? [])]);

    const rawExperiences = data?.optimizedExperiences?.length
      ? data.optimizedExperiences
      : response.optimizedExperiences ?? [];
    this.draftExperiences.set(
      rawExperiences.map(exp => ({
        ...exp,
        originalBullets: [...(exp.originalBullets ?? [])],
        tailoredBullets: [...(exp.tailoredBullets ?? [])],
        matchedKeywords: [...(exp.matchedKeywords ?? [])]
      }))
    );

    this.draftProjects.set(
      (data?.projects ?? []).map(proj => ({
        ...proj,
        tailoredBullets: [...(proj.tailoredBullets ?? [])],
        technologies: [...(proj.technologies ?? [])]
      }))
    );

    this.draftEducations.set((data?.educations ?? []).map(edu => ({ ...edu })));
    this.draftLanguages.set([...(data?.languages ?? [])]);
    this.draftCertifications.set([...(data?.certifications ?? [])]);
    this.expandedOriginalExpIndices.set(new Set());
    this.hasUnsavedChanges.set(false);
  }

  private clearEditorDraft(): void {
    this.draftTitle.set('');
    this.draftTemplate.set('MODERN');
    this.draftSummary.set('');
    this.draftHighlightedSkills.set([]);
    this.draftAdditionalSkills.set([]);
    this.draftExperiences.set([]);
    this.draftProjects.set([]);
    this.draftEducations.set([]);
    this.draftLanguages.set([]);
    this.draftCertifications.set([]);
    this.expandedOriginalExpIndices.set(new Set());
    this.hasUnsavedChanges.set(false);
  }

  private buildUpdatePayload(active: TailoredResumeResponse): UpdateTailoredResumeRequest {
    const sanitizedExperiences = this.draftExperiences().map(exp => ({
      ...exp,
      tailoredBullets: exp.tailoredBullets.map(b => b.trim()).filter(b => b.length > 0)
    }));

    const sanitizedProjects = this.draftProjects().map(proj => ({
      ...proj,
      description: proj.description?.trim() || null,
      tailoredBullets: proj.tailoredBullets.map(b => b.trim()).filter(b => b.length > 0)
    }));

    return {
      title: this.draftTitle().trim() || active.title,
      templateName: this.draftTemplate(),
      tailoredSummary: this.draftSummary().trim(),
      highlightedSkills: this.draftHighlightedSkills(),
      additionalSkills: this.draftAdditionalSkills(),
      optimizedExperiences: sanitizedExperiences,
      projects: sanitizedProjects,
      educations: this.draftEducations(),
      languages: this.draftLanguages(),
      certifications: this.draftCertifications()
    };
  }

  private handleExportSuccess(file: TailoredResumeExportFile, savedFirst: boolean): void {
    this.triggerBrowserDownload(file);
    const label = file.format.toUpperCase();
    this.actionSuccessMessage.set(
      savedFirst
        ? `Saved draft edits and exported ${label} (${file.fileName}).`
        : `Exported ${label} (${file.fileName}).`
    );
  }

  private handleExportError(err: unknown, format: ResumeExportFormat): void {
    this.exportErrorMessage.set(
      extractApiErrorMessage(
        err,
        `Unable to export tailored resume as ${format.toUpperCase()}. Please retry.`
      )
    );
  }

  private triggerBrowserDownload(file: TailoredResumeExportFile): void {
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

  private buildFallbackExportFilename(
    title: string,
    template: ResumeTemplate,
    format: ResumeExportFormat
  ): string {
    const slug = (title || 'tailored-resume')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 60);
    const tplSlug = template.toLowerCase().replace(/_/g, '-');
    return `${slug || 'tailored-resume'}-${tplSlug}-cv.${format}`;
  }

  private swapArrayItems<T>(list: readonly T[], fromIndex: number, toIndex: number): T[] {
    const copy = [...list];
    const [item] = copy.splice(fromIndex, 1);
    copy.splice(toIndex, 0, item);
    return copy;
  }

  private markDirty(): void {
    this.hasUnsavedChanges.set(true);
    this.actionSuccessMessage.set(null);
  }

  private appendUniqueStrings(
    existing: readonly string[],
    incoming: readonly string[],
    maxItems: number
  ): string[] {
    const seen = new Set(existing.map(s => s.toLowerCase()));
    const next = [...existing];
    for (const item of incoming) {
      const normalized = item.trim();
      if (normalized && !seen.has(normalized.toLowerCase()) && next.length < maxItems) {
        seen.add(normalized.toLowerCase());
        next.push(normalized);
      }
    }
    return next;
  }

  private formatMonthYear(isoDate: string | null): string {
    if (!isoDate) {
      return '';
    }
    const parsed = new Date(isoDate);
    if (Number.isNaN(parsed.getTime())) {
      return isoDate;
    }
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      year: 'numeric'
    }).format(parsed);
  }

  private toSelectableJob(job: JobSummary): SelectableJobOption {
    return {
      id: job.id,
      title: job.title,
      companyName: job.companyName,
      country: job.country,
      matchPercentage: job.matchPercentage
    };
  }
}
