import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  APPLICATION_STAGE_LIST,
  ApplicationStatus,
  JobApplicationDetailResponse,
  UpdateApplicationRequest,
  UpdateApplicationStatusRequest,
  dateInputToStartOfDayUtcIso,
  datetimeLocalToUtcIso,
  formatSalaryOffer,
  getApplicationStageMeta,
  normalizeSafeHttpUrl,
  toDateInputValue,
  toDatetimeLocalValue
} from '../../../../core/models/application.models';
import { CoverLetterSummaryDto } from '../../../../core/models/cover-letter.models';
import { TailoredResumeSummaryDto } from '../../../../core/models/tailored-resume.models';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';

@Component({
  selector: 'app-application-detail-drawer',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink, ConfirmDialogComponent, StatusBadgeComponent],
  templateUrl: './application-detail-drawer.component.html',
  styleUrl: './application-detail-drawer.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApplicationDetailDrawerComponent {
  readonly application = input<JobApplicationDetailResponse | null>(null);
  readonly tailoredResumes = input<readonly TailoredResumeSummaryDto[]>([]);
  readonly coverLetters = input<readonly CoverLetterSummaryDto[]>([]);
  readonly saving = input<boolean>(false);
  readonly transitioning = input<boolean>(false);
  readonly deleting = input<boolean>(false);

  readonly close = output<void>();
  readonly saveDetails = output<{ uuid: string; request: UpdateApplicationRequest }>();
  readonly transitionStatus = output<{ uuid: string; request: UpdateApplicationStatusRequest }>();
  readonly deleteConfirmed = output<string>();
  readonly openRecruiterTab = output<string>();

  readonly stages = APPLICATION_STAGE_LIST;

  readonly selectedNextStatus = signal<ApplicationStatus>('INTERESTED');
  readonly transitionNotes = signal<string>('');
  readonly appliedDateInput = signal<string>('');
  readonly nextInterviewInput = signal<string>('');
  readonly nextFollowUpInput = signal<string>('');
  readonly followUpCompleted = signal<boolean>(false);
  readonly salaryOfferInput = signal<string | number | null>('');
  readonly offeredCurrencyInput = signal<string>('EUR');
  readonly tailoredResumeUuidInput = signal<string>('');
  readonly coverLetterUuidInput = signal<string>('');
  readonly notesInput = signal<string>('');
  readonly deleteConfirmOpen = signal<boolean>(false);

  readonly sortedResumes = computed<readonly TailoredResumeSummaryDto[]>(() => {
    const app = this.application();
    const list = [...this.tailoredResumes()];
    if (!app) {
      return list;
    }
    return list.sort((a, b) => {
      const aMatch = a.jobId === app.jobId ? 0 : 1;
      const bMatch = b.jobId === app.jobId ? 0 : 1;
      if (aMatch !== bMatch) {
        return aMatch - bMatch;
      }
      return b.versionNumber - a.versionNumber;
    });
  });

  readonly sortedCoverLetters = computed<readonly CoverLetterSummaryDto[]>(() => {
    const app = this.application();
    const list = [...this.coverLetters()];
    if (!app) {
      return list;
    }
    return list.sort((a, b) => {
      const aMatch = a.jobId === app.jobId ? 0 : 1;
      const bMatch = b.jobId === app.jobId ? 0 : 1;
      if (aMatch !== bMatch) {
        return aMatch - bMatch;
      }
      return b.versionNumber - a.versionNumber;
    });
  });

  readonly safeApplyUrl = computed<string | null>(() => {
    const app = this.application();
    return normalizeSafeHttpUrl(app?.jobApplicationUrl) ?? normalizeSafeHttpUrl(app?.jobOriginalUrl);
  });

  readonly formattedOfferPreview = computed<string | null>(() => {
    const app = this.application();
    return app ? formatSalaryOffer(app.currentSalaryOffered, app.offeredCurrency) : null;
  });

  constructor() {
    effect(() => {
      const app = this.application();
      if (!app) {
        this.deleteConfirmOpen.set(false);
        return;
      }
      this.selectedNextStatus.set(app.status);
      this.transitionNotes.set('');
      this.appliedDateInput.set(toDateInputValue(app.appliedDate));
      this.nextInterviewInput.set(toDatetimeLocalValue(app.nextInterviewDate));
      this.nextFollowUpInput.set(toDateInputValue(app.nextFollowUpDate));
      this.followUpCompleted.set(app.followUpCompleted);
      this.salaryOfferInput.set(
        app.currentSalaryOffered !== null && app.currentSalaryOffered !== undefined
          ? String(app.currentSalaryOffered)
          : ''
      );
      this.offeredCurrencyInput.set(app.offeredCurrency ?? app.jobSalaryCurrency ?? 'EUR');
      this.tailoredResumeUuidInput.set(app.tailoredResume?.uuid ?? '');
      this.coverLetterUuidInput.set(app.coverLetter?.uuid ?? '');
      this.notesInput.set(app.notes ?? '');
      this.deleteConfirmOpen.set(false);
    });
  }

  stageLabel(status: ApplicationStatus | null): string {
    return status ? getApplicationStageMeta(status).label : 'Created';
  }

  submitTransition(): void {
    const app = this.application();
    if (!app) {
      return;
    }
    const nextStatus = this.selectedNextStatus();
    const note = this.transitionNotes().trim();
    if (nextStatus === app.status && !note) {
      return;
    }
    this.transitionStatus.emit({
      uuid: app.uuid,
      request: {
        newStatus: nextStatus,
        transitionNotes: note || undefined
      }
    });
    this.transitionNotes.set('');
  }

  submitMetadataUpdate(): void {
    const app = this.application();
    if (!app) {
      return;
    }

    const appliedDateRaw = String(this.appliedDateInput() ?? '').trim();
    const interviewRaw = String(this.nextInterviewInput() ?? '').trim();
    const followUpRaw = String(this.nextFollowUpInput() ?? '').trim();
    const salaryRaw = String(this.salaryOfferInput() ?? '').trim();
    const parsedSalary = salaryRaw ? Number(salaryRaw) : null;
    const currencyRaw = String(this.offeredCurrencyInput() ?? '').trim().toUpperCase();
    const resumeUuid = String(this.tailoredResumeUuidInput() ?? '').trim();
    const coverLetterUuid = String(this.coverLetterUuidInput() ?? '').trim();

    const request: UpdateApplicationRequest = {
      tailoredResumeUuid: resumeUuid || null,
      clearTailoredResume: !resumeUuid && Boolean(app.tailoredResume),
      coverLetterUuid: coverLetterUuid || null,
      clearCoverLetter: !coverLetterUuid && Boolean(app.coverLetter),
      appliedDate: appliedDateRaw ? dateInputToStartOfDayUtcIso(appliedDateRaw) : null,
      clearAppliedDate: !appliedDateRaw && Boolean(app.appliedDate),
      nextInterviewDate: interviewRaw ? datetimeLocalToUtcIso(interviewRaw) : null,
      clearNextInterviewDate: !interviewRaw && Boolean(app.nextInterviewDate),
      nextFollowUpDate: followUpRaw || null,
      clearNextFollowUpDate: !followUpRaw && Boolean(app.nextFollowUpDate),
      followUpCompleted: this.followUpCompleted(),
      currentSalaryOffered:
        parsedSalary !== null && !Number.isNaN(parsedSalary) && parsedSalary >= 0
          ? parsedSalary
          : null,
      clearSalaryOffer: !salaryRaw && app.currentSalaryOffered !== null,
      offeredCurrency:
        parsedSalary !== null && !Number.isNaN(parsedSalary) && /^[A-Z]{3}$/.test(currencyRaw)
          ? currencyRaw
          : undefined,
      notes: this.notesInput(),
      expectedVersion: app.version
    };

    this.saveDetails.emit({ uuid: app.uuid, request });
  }

  confirmDelete(): void {
    const app = this.application();
    if (!app) {
      return;
    }
    this.deleteConfirmOpen.set(false);
    this.deleteConfirmed.emit(app.uuid);
  }
}
