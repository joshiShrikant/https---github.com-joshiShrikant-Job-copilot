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
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { extractApiErrorMessage } from '../../core/models/api.models';
import {
  APPLICATION_STAGE_LIST,
  ApplicationStatus,
  CreateApplicationRequest,
  JobApplicationDetailResponse,
  UpdateApplicationRequest,
  UpdateApplicationStatusRequest,
  dateInputToStartOfDayUtcIso,
  datetimeLocalToUtcIso
} from '../../core/models/application.models';
import { JobDetail, JobSummary } from '../../core/models/job.models';
import { FollowUpReminderItemDto } from '../../core/models/recruiter.models';
import { ApplicationService } from '../../core/services/application.service';
import { CoverLetterService } from '../../core/services/cover-letter.service';
import { JobService } from '../../core/services/job.service';
import { RecruiterService } from '../../core/services/recruiter.service';
import { TailoredResumeService } from '../../core/services/tailored-resume.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import {
  ApplicationDetailDrawerComponent
} from './components/application-detail-drawer/application-detail-drawer.component';
import {
  ApplicationsCalendarComponent
} from './components/applications-calendar/applications-calendar.component';
import {
  ApplicationsTableComponent
} from './components/applications-table/applications-table.component';
import {
  ApplicationStageMoveEvent,
  KanbanBoardComponent
} from './components/kanban-board/kanban-board.component';
import { RecruiterCrmComponent } from './components/recruiter-crm/recruiter-crm.component';

export type ApplicationsWorkspaceTab = 'kanban' | 'table' | 'calendar' | 'recruiter';

@Component({
  selector: 'app-applications',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    PageHeaderComponent,
    KanbanBoardComponent,
    ApplicationsTableComponent,
    ApplicationsCalendarComponent,
    ApplicationDetailDrawerComponent,
    RecruiterCrmComponent
  ],
  templateUrl: './applications.component.html',
  styleUrl: './applications.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApplicationsComponent implements OnInit {
  private readonly applicationService = inject(ApplicationService);
  private readonly recruiterService = inject(RecruiterService);
  private readonly jobService = inject(JobService);
  private readonly tailoredResumeService = inject(TailoredResumeService);
  private readonly coverLetterService = inject(CoverLetterService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly applications = this.applicationService.applications;
  readonly selectedApplication = this.applicationService.selectedApplication;
  readonly pipelineStats = this.applicationService.pipelineStats;
  readonly isLoading = this.applicationService.isLoading;
  readonly isCreating = this.applicationService.isCreating;
  readonly updatingUuid = this.applicationService.updatingUuid;
  readonly transitioningUuid = this.applicationService.transitioningUuid;
  readonly deletingUuid = this.applicationService.deletingUuid;

  readonly reminders = this.recruiterService.reminders;
  readonly tailoredResumes = this.tailoredResumeService.resumes;
  readonly coverLetters = this.coverLetterService.coverLetters;

  readonly stages = APPLICATION_STAGE_LIST;
  readonly activeTab = signal<ApplicationsWorkspaceTab>('kanban');

  // Filter bar state
  readonly searchQuery = signal<string>('');
  readonly statusFilter = signal<ApplicationStatus | ''>('');
  readonly dueFollowUpOnly = signal<boolean>(false);
  readonly hasInterviewOnly = signal<boolean>(false);

  // Page feedback banners
  readonly loadErrorMessage = signal<string | null>(null);
  readonly actionSuccessMessage = signal<string | null>(null);
  readonly actionErrorMessage = signal<string | null>(null);

  // Deep link state for Recruiter CRM tab
  readonly deepLinkedApplicationUuid = signal<string | null>(null);
  readonly deepLinkedRecruiterId = signal<number | null>(null);
  private pendingDeepLinkJobId = signal<number | null>(null);
  private pendingDeepLinkAppUuid = signal<string | null>(null);
  private readonly extraDeepLinkedJob = signal<JobDetail | null>(null);
  private readonly appsLoaded = signal<boolean>(false);
  private readonly jobsLoaded = signal<boolean>(false);

  // Track new application modal state
  readonly createModalOpen = signal<boolean>(false);
  readonly createJobId = signal<number | null>(null);
  readonly createStatus = signal<ApplicationStatus>('INTERESTED');
  readonly createAppliedDate = signal<string>('');
  readonly createFollowUpDate = signal<string>('');
  readonly createInterviewDate = signal<string>('');
  readonly createResumeUuid = signal<string>('');
  readonly createCoverLetterUuid = signal<string>('');
  readonly createSalaryOffered = signal<string | number | null>('');
  readonly createCurrency = signal<string>('EUR');
  readonly createNotes = signal<string>('');

  readonly headerBadge = computed<string>(() => {
    const total = this.pipelineStats().totalCount;
    return total > 0 ? `${total} Tracked` : 'Pipeline CRM';
  });

  readonly availableJobsToTrack = computed<readonly JobSummary[]>(() => {
    const trackedIds = new Set(this.applications().map(a => a.jobId));
    const base = this.jobService.jobs().filter(j => !trackedIds.has(j.id));
    const deepJob = this.extraDeepLinkedJob();
    if (deepJob && !trackedIds.has(deepJob.id) && !base.some(j => j.id === deepJob.id)) {
      const mapped: JobSummary = {
        id: deepJob.id,
        title: deepJob.title,
        companyName: deepJob.companyName,
        location: deepJob.location ?? null,
        city: deepJob.city ?? null,
        country: deepJob.country,
        workArrangement: deepJob.workArrangement,
        employmentType: deepJob.employmentType ?? null,
        experienceLevel: deepJob.experienceLevel ?? null,
        salaryMin: deepJob.salaryMin ?? null,
        salaryMax: deepJob.salaryMax ?? null,
        salaryCurrency: deepJob.salaryCurrency ?? null,
        salaryPeriod: deepJob.salaryPeriod ?? null,
        sponsorStatus: deepJob.sponsorStatus,
        matchPercentage: deepJob.matchPercentage ?? null,
        sourceProvider: deepJob.sourceProvider ?? 'INDEXED',
        postedAt: deepJob.postedAt ?? new Date().toISOString(),
        isSaved: Boolean(deepJob.isSaved),
        isInterested: Boolean(deepJob.isInterested),
        isHidden: Boolean(deepJob.isHidden),
        skills: deepJob.skills ?? [],
        descriptionSnippet: null,
        originalUrl: deepJob.originalUrl ?? null
      };
      return [mapped, ...base];
    }
    return base;
  });

  readonly filteredApplications = computed<readonly JobApplicationDetailResponse[]>(() => {
    const q = this.searchQuery().trim().toLowerCase();
    const st = this.statusFilter();
    const dueOnly = this.dueFollowUpOnly();
    const intOnly = this.hasInterviewOnly();

    return this.applications().filter(app => {
      if (st && app.status !== st) {
        return false;
      }
      if (dueOnly && (!app.nextFollowUpDate || app.followUpCompleted)) {
        return false;
      }
      if (intOnly && !app.nextInterviewDate) {
        return false;
      }
      if (q) {
        const hay = [
          app.jobTitle,
          app.companyName,
          app.jobLocation ?? '',
          app.jobCountry ?? '',
          app.notes ?? ''
        ]
          .join(' ')
          .toLowerCase();
        if (!hay.includes(q)) {
          return false;
        }
      }
      return true;
    });
  });

  ngOnInit(): void {
    this.reloadWorkspace();

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(params => {
        const tabParam = params.get('tab');
        if (
          tabParam === 'kanban' ||
          tabParam === 'table' ||
          tabParam === 'calendar' ||
          tabParam === 'recruiter'
        ) {
          this.activeTab.set(tabParam);
        }

        const appUuid = params.get('applicationUuid');
        if (appUuid?.trim()) {
          this.pendingDeepLinkAppUuid.set(appUuid.trim());
          this.deepLinkedApplicationUuid.set(appUuid.trim());
          this.applyPendingDeepLinks();
        }

        const recruiterParam = params.get('recruiterId');
        const parsedRecId = recruiterParam ? Number(recruiterParam) : NaN;
        if (!Number.isNaN(parsedRecId) && parsedRecId > 0) {
          this.deepLinkedRecruiterId.set(parsedRecId);
          this.activeTab.set('recruiter');
        }

        const jobIdParam = params.get('jobId');
        const parsedJobId = jobIdParam ? Number(jobIdParam) : NaN;
        if (!Number.isNaN(parsedJobId) && parsedJobId > 0) {
          this.pendingDeepLinkJobId.set(parsedJobId);
          this.applyPendingDeepLinks();
        }
      });
  }

  reloadWorkspace(): void {
    this.loadErrorMessage.set(null);
    this.appsLoaded.set(false);
    this.jobsLoaded.set(false);

    this.applicationService
      .loadApplications()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.appsLoaded.set(true);
          this.applyPendingDeepLinks();
        },
        error: err => {
          this.appsLoaded.set(true);
          this.loadErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to load tracked job applications.')
          );
          this.applyPendingDeepLinks();
        }
      });

    this.recruiterService
      .loadReminders(false)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: () => {
          // Non-fatal on summary load
        }
      });

    this.jobService
      .searchJobs({}, 0, 50)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.jobsLoaded.set(true);
          const firstUntracked = this.availableJobsToTrack()[0];
          if (this.createJobId() === null && firstUntracked) {
            this.createJobId.set(firstUntracked.id);
          }
          this.applyPendingDeepLinks();
        },
        error: () => {
          // Non-fatal background picker load
          this.jobsLoaded.set(true);
          this.applyPendingDeepLinks();
        }
      });

    this.tailoredResumeService
      .loadTailoredResumes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: () => {
          // Non-fatal document dropdown load
        }
      });

    this.coverLetterService
      .loadCoverLetters()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: () => {
          // Non-fatal document dropdown load
        }
      });
  }

  refreshReminders(): void {
    this.recruiterService
      .loadReminders(false)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ error: () => {} });
  }

  setActiveTab(tab: ApplicationsWorkspaceTab): void {
    this.activeTab.set(tab);
  }

  openCreateModal(prefillJobId?: number): void {
    this.actionErrorMessage.set(null);
    const available = this.availableJobsToTrack();
    const targetId = prefillJobId ?? this.createJobId() ?? available[0]?.id ?? null;
    this.createJobId.set(targetId);
    this.createStatus.set('INTERESTED');
    this.createAppliedDate.set('');
    this.createFollowUpDate.set('');
    this.createInterviewDate.set('');
    this.createSalaryOffered.set('');
    this.createCurrency.set('EUR');
    this.createNotes.set('');

    const matchingCv = targetId
      ? this.tailoredResumes().find(r => r.jobId === targetId)
      : undefined;
    const matchingCl = targetId
      ? this.coverLetters().find(c => c.jobId === targetId)
      : undefined;
    this.createResumeUuid.set(matchingCv?.uuid ?? '');
    this.createCoverLetterUuid.set(matchingCl?.uuid ?? '');
    this.createModalOpen.set(true);
  }

  submitCreateApplication(): void {
    const jobId = this.createJobId();
    if (!jobId || jobId <= 0) {
      this.actionErrorMessage.set('Please select a target job to track.');
      return;
    }

    const salaryRaw = String(this.createSalaryOffered() ?? '').trim();
    const parsedSalary = salaryRaw ? Number(salaryRaw) : null;
    const currencyRaw = String(this.createCurrency() ?? '').trim().toUpperCase();

    const payload: CreateApplicationRequest = {
      jobId,
      status: this.createStatus(),
      appliedDate: dateInputToStartOfDayUtcIso(String(this.createAppliedDate() ?? '')) ?? undefined,
      nextFollowUpDate: String(this.createFollowUpDate() ?? '').trim() || undefined,
      nextInterviewDate: datetimeLocalToUtcIso(String(this.createInterviewDate() ?? '')) ?? undefined,
      tailoredResumeUuid: String(this.createResumeUuid() ?? '').trim() || undefined,
      coverLetterUuid: String(this.createCoverLetterUuid() ?? '').trim() || undefined,
      currentSalaryOffered:
        parsedSalary !== null && !Number.isNaN(parsedSalary) && parsedSalary >= 0
          ? parsedSalary
          : undefined,
      offeredCurrency:
        parsedSalary !== null && /^[A-Z]{3}$/.test(currencyRaw) ? currencyRaw : undefined,
      notes: String(this.createNotes() ?? '').trim() || undefined
    };

    this.actionErrorMessage.set(null);
    this.actionSuccessMessage.set(null);
    this.applicationService
      .createApplication(payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: created => {
          this.createModalOpen.set(false);
          this.actionSuccessMessage.set(
            `Added "${created.jobTitle}" at ${created.companyName} to your pipeline.`
          );
          this.refreshReminders();
        },
        error: err => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to add application to tracker.')
          );
        }
      });
  }

  selectApplication(uuid: string | null): void {
    this.applicationService.selectApplication(uuid);
    if (uuid) {
      this.deepLinkedApplicationUuid.set(uuid);
    }
  }

  onStageMove(event: ApplicationStageMoveEvent): void {
    this.handleTransitionStatus({
      uuid: event.uuid,
      request: { newStatus: event.newStatus }
    });
  }

  handleTransitionStatus(event: {
    uuid: string;
    request: UpdateApplicationStatusRequest;
  }): void {
    this.actionErrorMessage.set(null);
    this.actionSuccessMessage.set(null);
    this.applicationService
      .updateApplicationStatus(event.uuid, event.request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.actionSuccessMessage.set(
            `Moved ${updated.jobTitle} to ${updated.status.replace(/_/g, ' ')}.`
          );
          this.refreshReminders();
        },
        error: err => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to transition application stage.')
          );
        }
      });
  }

  handleSaveApplicationDetails(event: {
    uuid: string;
    request: UpdateApplicationRequest;
  }): void {
    this.actionErrorMessage.set(null);
    this.actionSuccessMessage.set(null);
    this.applicationService
      .updateApplication(event.uuid, event.request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.actionSuccessMessage.set(`Saved updates for ${updated.jobTitle}.`);
          this.refreshReminders();
        },
        error: err => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to save application updates.')
          );
        }
      });
  }

  handleCompleteApplicationFollowUp(uuid: string): void {
    const existing = this.applications().find(a => a.uuid === uuid);
    this.handleSaveApplicationDetails({
      uuid,
      request: {
        followUpCompleted: true,
        expectedVersion: existing?.version
      }
    });
  }

  handleCompleteRecruiterReminder(item: FollowUpReminderItemDto): void {
    if (item.recruiterId === null || item.interactionId === null) {
      return;
    }
    this.recruiterService
      .updateInteraction(item.recruiterId, item.interactionId, {
        action: 'SAVE_DRAFT',
        reminderCompleted: true
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.actionSuccessMessage.set(`Completed reminder for ${item.contactLabel}.`);
          this.refreshReminders();
        },
        error: err => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to complete recruiter reminder.')
          );
        }
      });
  }

  handleDeleteApplication(uuid: string): void {
    this.actionErrorMessage.set(null);
    this.actionSuccessMessage.set(null);
    this.applicationService
      .deleteApplication(uuid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.actionSuccessMessage.set('Removed tracked application from your pipeline.');
          this.refreshReminders();
        },
        error: err => {
          this.actionErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to delete application.')
          );
        }
      });
  }

  openRecruiterFromApplication(applicationUuid: string): void {
    this.deepLinkedApplicationUuid.set(applicationUuid);
    this.applicationService.selectApplication(null);
    this.activeTab.set('recruiter');
  }

  openRecruiterById(recruiterId: number): void {
    this.deepLinkedRecruiterId.set(recruiterId);
    this.activeTab.set('recruiter');
  }

  resetFilters(): void {
    this.searchQuery.set('');
    this.statusFilter.set('');
    this.dueFollowUpOnly.set(false);
    this.hasInterviewOnly.set(false);
  }

  private applyPendingDeepLinks(): void {
    if (!this.appsLoaded()) {
      return;
    }

    const apps = this.applications();
    const pendingAppUuid = this.pendingDeepLinkAppUuid();
    if (pendingAppUuid && apps.some(a => a.uuid === pendingAppUuid)) {
      this.applicationService.selectApplication(pendingAppUuid);
      this.pendingDeepLinkAppUuid.set(null);
    }

    if (!this.jobsLoaded()) {
      return;
    }

    const pendingJobId = this.pendingDeepLinkJobId();
    if (pendingJobId !== null) {
      const existing = apps.find(a => a.jobId === pendingJobId);
      if (existing) {
        this.applicationService.selectApplication(existing.uuid);
        this.pendingDeepLinkJobId.set(null);
      } else if (!this.isLoading()) {
        this.pendingDeepLinkJobId.set(null);
        if (!this.availableJobsToTrack().some(j => j.id === pendingJobId)) {
          this.jobService
            .getJobDetail(pendingJobId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: detail => {
                this.extraDeepLinkedJob.set(detail);
                this.openCreateModal(detail.id);
              },
              error: () => {
                this.openCreateModal(pendingJobId);
              }
            });
        } else {
          this.openCreateModal(pendingJobId);
        }
      }
    }
  }
}
