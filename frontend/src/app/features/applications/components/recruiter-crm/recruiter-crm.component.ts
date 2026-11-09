import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  NgZone,
  OnInit,
  computed,
  effect,
  inject,
  input,
  output,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import {
  JobApplicationDetailResponse,
  normalizeSafeHttpUrl,
  toDateInputValue
} from '../../../../core/models/application.models';
import { extractApiErrorMessage } from '../../../../core/models/api.models';
import {
  AI_OUTREACH_TYPE_OPTIONS,
  ALL_RECRUITER_MESSAGE_TYPES,
  COMMUNICATION_CHANNEL_OPTIONS,
  CommunicationChannel,
  CreateRecruiterInteractionRequest,
  CreateRecruiterRequest,
  FollowUpReminderItemDto,
  GenerateOutreachRequest,
  RecruiterInteractionDto,
  RecruiterMessageType,
  RecruiterResponse,
  UpdateRecruiterInteractionRequest,
  formatChannelLabel,
  formatMessageTypeLabel,
  getMessageStatusMeta
} from '../../../../core/models/recruiter.models';
import { RecruiterService } from '../../../../core/services/recruiter.service';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-recruiter-crm',
  standalone: true,
  imports: [DatePipe, FormsModule, ConfirmDialogComponent],
  templateUrl: './recruiter-crm.component.html',
  styleUrl: './recruiter-crm.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RecruiterCrmComponent implements OnInit {
  private readonly recruiterService = inject(RecruiterService);
  private readonly ngZone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);

  readonly applications = input<readonly JobApplicationDetailResponse[]>([]);
  readonly preselectedApplicationUuid = input<string | null>(null);
  readonly preselectedRecruiterId = input<number | null>(null);

  readonly openApplication = output<string>();
  readonly completeApplicationFollowUp = output<string>();
  readonly remindersUpdated = output<void>();

  readonly recruiters = this.recruiterService.recruiters;
  readonly selectedRecruiter = this.recruiterService.selectedRecruiter;
  readonly reminders = this.recruiterService.reminders;
  readonly lastOutreachDraft = this.recruiterService.lastOutreachDraft;
  readonly isLoadingList = this.recruiterService.isLoadingList;
  readonly isLoadingDetail = this.recruiterService.isLoadingDetail;
  readonly isSavingRecruiter = this.recruiterService.isSavingRecruiter;
  readonly isGeneratingOutreach = this.recruiterService.isGeneratingOutreach;
  readonly isUpdatingInteraction = this.recruiterService.isUpdatingInteraction;
  readonly deletingRecruiterId = this.recruiterService.deletingRecruiterId;
  readonly deletingInteractionId = this.recruiterService.deletingInteractionId;

  readonly outreachTypes = AI_OUTREACH_TYPE_OPTIONS;
  readonly allMessageTypes = ALL_RECRUITER_MESSAGE_TYPES;
  readonly channels = COMMUNICATION_CHANNEL_OPTIONS;

  readonly searchQuery = signal<string>('');
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  // Recruiter create/edit form state
  readonly recruiterFormOpen = signal<boolean>(false);
  readonly editingRecruiterId = signal<number | null>(null);
  readonly recFullName = signal<string>('');
  readonly recRoleTitle = signal<string>('');
  readonly recCompanyName = signal<string>('');
  readonly recEmail = signal<string>('');
  readonly recLinkedinUrl = signal<string>('');
  readonly recPhone = signal<string>('');
  readonly recNotes = signal<string>('');

  // AI outreach generator state
  readonly aiMessageType = signal<RecruiterMessageType>('LINKEDIN_CONNECTION');
  readonly aiChannel = signal<CommunicationChannel>('LINKEDIN');
  readonly aiApplicationUuid = signal<string>('');
  readonly aiPersonalContext = signal<string>('');
  readonly aiFollowUpDate = signal<string>('');

  // Active editable interaction / draft state
  readonly editingInteractionId = signal<number | null>(null);
  readonly draftChannel = signal<CommunicationChannel>('LINKEDIN');
  readonly draftMessageType = signal<RecruiterMessageType>('LINKEDIN_CONNECTION');
  readonly draftApplicationUuid = signal<string>('');
  readonly draftSubjectLine = signal<string>('');
  readonly draftMessageBody = signal<string>('');
  readonly draftFollowUpDate = signal<string>('');
  readonly draftReminderCompleted = signal<boolean>(false);
  readonly copiedFeedback = signal<boolean>(false);

  // Manual log/compose interaction state
  readonly manualFormOpen = signal<boolean>(false);
  readonly manualChannel = signal<CommunicationChannel>('EMAIL');
  readonly manualMessageType = signal<RecruiterMessageType>('INITIAL_OUTREACH');
  readonly manualApplicationUuid = signal<string>('');
  readonly manualSubjectLine = signal<string>('');
  readonly manualMessageBody = signal<string>('');
  readonly manualFollowUpDate = signal<string>('');
  readonly manualMarkAsSent = signal<boolean>(false);
  readonly manualExplicitConfirm = signal<boolean>(false);

  // Confirmation dialogs
  readonly deleteRecruiterConfirmTarget = signal<RecruiterResponse | null>(null);
  readonly markSentConfirmTarget = signal<RecruiterInteractionDto | null>(null);

  private copyTimerId: ReturnType<typeof setTimeout> | null = null;
  private initialized = false;

  readonly safeLinkedinUrl = computed<string | null>(() =>
    normalizeSafeHttpUrl(this.selectedRecruiter()?.linkedinUrl)
  );

  readonly activeEditingInteraction = computed<RecruiterInteractionDto | null>(() => {
    const id = this.editingInteractionId();
    const rec = this.selectedRecruiter();
    if (!id || !rec?.interactions) {
      return null;
    }
    return rec.interactions.find(i => i.id === id) ?? null;
  });

  readonly activeDraftCharCount = computed<number>(() => this.draftMessageBody().length);

  readonly isConnectionLimitExceeded = computed<boolean>(
    () => this.draftMessageType() === 'LINKEDIN_CONNECTION' && this.activeDraftCharCount() > 300
  );

  constructor() {
    this.destroyRef.onDestroy(() => {
      if (this.copyTimerId !== null) {
        clearTimeout(this.copyTimerId);
      }
    });

    effect(() => {
      const preAppUuid = this.preselectedApplicationUuid();
      if (preAppUuid) {
        this.aiApplicationUuid.set(preAppUuid);
        this.manualApplicationUuid.set(preAppUuid);
      }
    });

    effect(() => {
      const preRecruiterId = this.preselectedRecruiterId();
      if (this.initialized && preRecruiterId !== null && preRecruiterId > 0) {
        this.selectRecruiter(preRecruiterId);
      }
    });
  }

  ngOnInit(): void {
    this.initialized = true;
    this.loadDirectory();
    this.loadReminders();
    const preRecId = this.preselectedRecruiterId();
    if (preRecId !== null && preRecId > 0) {
      this.selectRecruiter(preRecId);
    }
  }

  loadDirectory(): void {
    this.errorMessage.set(null);
    this.recruiterService
      .loadRecruiters(this.searchQuery(), null, 0, 50)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => {
          const currentSelected = this.selectedRecruiter();
          const targetId = this.preselectedRecruiterId() ?? currentSelected?.id ?? page.content[0]?.id;
          if (targetId && (!currentSelected || currentSelected.id !== targetId)) {
            this.selectRecruiter(targetId);
          }
        },
        error: err => {
          this.errorMessage.set(extractApiErrorMessage(err, 'Unable to load recruiter contacts.'));
        }
      });
  }

  loadReminders(): void {
    this.recruiterService
      .loadReminders(false)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: () => {
          // Reminders panel stays graceful on partial reload failure
        }
      });
  }

  selectRecruiter(id: number): void {
    this.errorMessage.set(null);
    this.recruiterService
      .getRecruiter(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: detail => {
          const latestDraft = detail.interactions?.find(i => i.status !== 'SENT') ?? null;
          if (latestDraft) {
            this.loadInteractionIntoEditor(latestDraft);
          } else {
            this.editingInteractionId.set(null);
          }
        },
        error: err => {
          this.errorMessage.set(extractApiErrorMessage(err, 'Unable to load recruiter details.'));
        }
      });
  }

  openCreateRecruiterForm(): void {
    this.editingRecruiterId.set(null);
    this.recFullName.set('');
    this.recRoleTitle.set('');
    this.recCompanyName.set('');
    this.recEmail.set('');
    this.recLinkedinUrl.set('');
    this.recPhone.set('');
    this.recNotes.set('');
    this.recruiterFormOpen.set(true);
  }

  openEditRecruiterForm(rec: RecruiterResponse): void {
    this.editingRecruiterId.set(rec.id);
    this.recFullName.set(rec.fullName);
    this.recRoleTitle.set(rec.roleTitle ?? '');
    this.recCompanyName.set(rec.companyName ?? '');
    this.recEmail.set(rec.email ?? '');
    this.recLinkedinUrl.set(rec.linkedinUrl ?? '');
    this.recPhone.set(rec.phone ?? '');
    this.recNotes.set(rec.notes ?? '');
    this.recruiterFormOpen.set(true);
  }

  saveRecruiter(): void {
    const fullName = this.recFullName().trim();
    if (!fullName) {
      this.errorMessage.set('Recruiter full name is required.');
      return;
    }

    const linkedin = this.recLinkedinUrl().trim();
    if (linkedin && !normalizeSafeHttpUrl(linkedin)) {
      this.errorMessage.set('LinkedIn URL must start with http:// or https://.');
      return;
    }

    const payload: CreateRecruiterRequest = {
      fullName,
      roleTitle: this.recRoleTitle().trim() || undefined,
      companyName: this.recCompanyName().trim() || undefined,
      email: this.recEmail().trim() || undefined,
      linkedinUrl: linkedin || undefined,
      phone: this.recPhone().trim() || undefined,
      notes: this.recNotes().trim() || undefined
    };

    this.errorMessage.set(null);
    this.successMessage.set(null);
    const editId = this.editingRecruiterId();

    const obs =
      editId !== null
        ? this.recruiterService.updateRecruiter(editId, payload)
        : this.recruiterService.createRecruiter(payload);

    obs.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: saved => {
        this.recruiterFormOpen.set(false);
        this.editingRecruiterId.set(null);
        this.successMessage.set(
          editId !== null
            ? `Updated recruiter contact ${saved.fullName}.`
            : `Added ${saved.fullName} to your Recruiter CRM.`
        );
      },
      error: err => {
        this.errorMessage.set(extractApiErrorMessage(err, 'Unable to save recruiter contact.'));
      }
    });
  }

  confirmDeleteRecruiter(): void {
    const target = this.deleteRecruiterConfirmTarget();
    if (!target) {
      return;
    }
    this.deleteRecruiterConfirmTarget.set(null);
    this.recruiterService
      .deleteRecruiter(target.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.editingInteractionId.set(null);
          this.successMessage.set(`Removed recruiter ${target.fullName}.`);
          this.loadReminders();
          this.remindersUpdated.emit();
        },
        error: err => {
          this.errorMessage.set(extractApiErrorMessage(err, 'Unable to delete recruiter.'));
        }
      });
  }

  selectAiOutreachType(type: RecruiterMessageType): void {
    this.aiMessageType.set(type);
    const meta = this.outreachTypes.find(t => t.value === type);
    if (meta) {
      this.aiChannel.set(meta.defaultChannel);
    }
  }

  generateAiOutreachDraft(): void {
    const rec = this.selectedRecruiter();
    if (!rec) {
      return;
    }
    const request: GenerateOutreachRequest = {
      channel: this.aiChannel(),
      messageType: this.aiMessageType(),
      applicationUuid: this.aiApplicationUuid().trim() || undefined,
      personalContext: this.aiPersonalContext().trim() || undefined,
      followUpReminderDate: this.aiFollowUpDate().trim() || undefined
    };

    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.recruiterService
      .generateOutreach(rec.id, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: draft => {
          this.loadInteractionIntoEditor(draft.interaction);
          this.successMessage.set(
            'Generated editable AI outreach draft (saved as DRAFT, unsent — review and approve manually).'
          );
          this.loadReminders();
          this.remindersUpdated.emit();
        },
        error: err => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to generate AI outreach draft right now.')
          );
        }
      });
  }

  loadInteractionIntoEditor(interaction: RecruiterInteractionDto): void {
    this.editingInteractionId.set(interaction.id);
    this.draftChannel.set(interaction.channel);
    this.draftMessageType.set(interaction.messageType);
    this.draftApplicationUuid.set(interaction.applicationUuid ?? '');
    this.draftSubjectLine.set(interaction.subjectLine ?? '');
    this.draftMessageBody.set(interaction.messageBody);
    this.draftFollowUpDate.set(toDateInputValue(interaction.followUpReminderDate));
    this.draftReminderCompleted.set(interaction.reminderCompleted);
  }

  saveDraftEdits(action: 'SAVE_DRAFT' | 'APPROVE'): void {
    const active = this.activeEditingInteraction();
    if (!active) {
      return;
    }
    const body = this.draftMessageBody().trim();
    if (!body) {
      this.errorMessage.set('Message body cannot be empty.');
      return;
    }

    const followUp = this.draftFollowUpDate().trim();
    const appUuid = this.draftApplicationUuid().trim();

    const payload: UpdateRecruiterInteractionRequest = {
      channel: this.draftChannel(),
      messageType: this.draftMessageType(),
      applicationUuid: appUuid || undefined,
      clearApplication: !appUuid && Boolean(active.applicationUuid),
      subjectLine: this.draftSubjectLine().trim() || undefined,
      messageBody: body,
      followUpReminderDate: followUp || undefined,
      clearFollowUpReminderDate: !followUp && Boolean(active.followUpReminderDate),
      reminderCompleted: this.draftReminderCompleted(),
      action,
      explicitApprovalConfirmed: false
    };

    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.recruiterService
      .updateInteraction(active.recruiterId, active.id, payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.loadInteractionIntoEditor(updated);
          this.successMessage.set(
            action === 'APPROVE'
              ? 'Draft approved and ready for manual copy/send (still unsent until you confirm dispatch).'
              : 'Draft edits saved cleanly (status remains DRAFT, unsent).'
          );
          this.loadReminders();
          this.remindersUpdated.emit();
        },
        error: err => {
          this.errorMessage.set(extractApiErrorMessage(err, 'Unable to update outreach draft.'));
        }
      });
  }

  requestMarkSent(interaction: RecruiterInteractionDto): void {
    if (this.editingInteractionId() === interaction.id && !this.draftMessageBody().trim()) {
      this.errorMessage.set('Message body cannot be empty.');
      return;
    }
    this.markSentConfirmTarget.set(interaction);
  }

  confirmMarkSent(): void {
    const target = this.markSentConfirmTarget();
    if (!target) {
      return;
    }
    this.markSentConfirmTarget.set(null);
    const isEditingSame = this.editingInteractionId() === target.id;
    const payload: UpdateRecruiterInteractionRequest = {
      channel: isEditingSame ? this.draftChannel() : target.channel,
      messageType: isEditingSame ? this.draftMessageType() : target.messageType,
      subjectLine: isEditingSame
        ? this.draftSubjectLine().trim() || undefined
        : target.subjectLine ?? undefined,
      messageBody: isEditingSame ? this.draftMessageBody().trim() : target.messageBody,
      followUpReminderDate: isEditingSame
        ? this.draftFollowUpDate().trim() || undefined
        : target.followUpReminderDate ?? undefined,
      reminderCompleted: isEditingSame ? this.draftReminderCompleted() : target.reminderCompleted,
      action: 'MARK_SENT',
      explicitApprovalConfirmed: true
    };

    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.recruiterService
      .updateInteraction(target.recruiterId, target.id, payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          if (isEditingSame) {
            this.loadInteractionIntoEditor(updated);
          }
          this.successMessage.set('Explicitly confirmed and logged communication as SENT.');
          this.loadReminders();
          this.remindersUpdated.emit();
        },
        error: err => {
          this.errorMessage.set(extractApiErrorMessage(err, 'Unable to mark outreach as sent.'));
        }
      });
  }

  createManualInteraction(): void {
    const rec = this.selectedRecruiter();
    if (!rec) {
      return;
    }
    const body = this.manualMessageBody().trim();
    if (!body) {
      this.errorMessage.set('Please enter a message or note body.');
      return;
    }
    if (this.manualMarkAsSent() && !this.manualExplicitConfirm()) {
      this.errorMessage.set(
        'Please confirm that you manually sent this message externally before logging it as Sent.'
      );
      return;
    }

    const request: CreateRecruiterInteractionRequest = {
      applicationUuid: this.manualApplicationUuid().trim() || undefined,
      channel: this.manualChannel(),
      messageType: this.manualMessageType(),
      subjectLine: this.manualSubjectLine().trim() || undefined,
      messageBody: body,
      followUpReminderDate: this.manualFollowUpDate().trim() || undefined,
      markAsSent: this.manualMarkAsSent(),
      explicitApprovalConfirmed: this.manualMarkAsSent() ? this.manualExplicitConfirm() : false
    };

    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.recruiterService
      .createInteraction(rec.id, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: created => {
          this.manualFormOpen.set(false);
          this.manualSubjectLine.set('');
          this.manualMessageBody.set('');
          this.manualFollowUpDate.set('');
          this.manualMarkAsSent.set(false);
          this.manualExplicitConfirm.set(false);
          this.loadInteractionIntoEditor(created);
          this.successMessage.set(
            created.status === 'SENT'
              ? 'Logged externally sent recruiter communication.'
              : 'Saved manual outreach draft (unsent).'
          );
          this.loadReminders();
          this.remindersUpdated.emit();
        },
        error: err => {
          this.errorMessage.set(extractApiErrorMessage(err, 'Unable to record interaction.'));
        }
      });
  }

  deleteInteraction(interaction: RecruiterInteractionDto): void {
    this.errorMessage.set(null);
    this.recruiterService
      .deleteInteraction(interaction.recruiterId, interaction.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          if (this.editingInteractionId() === interaction.id) {
            this.editingInteractionId.set(null);
          }
          this.loadReminders();
          this.remindersUpdated.emit();
        },
        error: err => {
          this.errorMessage.set(extractApiErrorMessage(err, 'Unable to delete interaction.'));
        }
      });
  }

  completeReminderItem(item: FollowUpReminderItemDto): void {
    if (item.sourceType === 'APPLICATION' && item.applicationUuid) {
      this.completeApplicationFollowUp.emit(item.applicationUuid);
      return;
    }
    if (
      item.sourceType === 'RECRUITER_INTERACTION' &&
      item.recruiterId !== null &&
      item.interactionId !== null
    ) {
      this.recruiterService
        .updateInteraction(item.recruiterId, item.interactionId, {
          action: 'SAVE_DRAFT',
          reminderCompleted: true
        })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            this.loadReminders();
            this.remindersUpdated.emit();
          },
          error: err => {
            this.errorMessage.set(extractApiErrorMessage(err, 'Unable to complete reminder.'));
          }
        });
    }
  }

  copyActiveDraftToClipboard(): void {
    const subject = this.draftSubjectLine().trim();
    const body = this.draftMessageBody().trim();
    if (!body) {
      return;
    }
    const textToCopy = subject ? `Subject: ${subject}\n\n${body}` : body;
    const clip = typeof navigator !== 'undefined' ? navigator.clipboard : undefined;
    if (clip?.writeText) {
      void clip.writeText(textToCopy);
    }
    this.copiedFeedback.set(true);
    if (this.copyTimerId !== null) {
      clearTimeout(this.copyTimerId);
    }
    this.ngZone.runOutsideAngular(() => {
      this.copyTimerId = setTimeout(() => {
        this.ngZone.run(() => {
          this.copiedFeedback.set(false);
          this.copyTimerId = null;
        });
      }, 2000);
    });
  }

  messageTypeLabel(type: RecruiterMessageType): string {
    return formatMessageTypeLabel(type);
  }

  channelLabel(channel: CommunicationChannel): string {
    return formatChannelLabel(channel);
  }

  statusMeta(interaction: RecruiterInteractionDto) {
    return getMessageStatusMeta(interaction.status);
  }
}

