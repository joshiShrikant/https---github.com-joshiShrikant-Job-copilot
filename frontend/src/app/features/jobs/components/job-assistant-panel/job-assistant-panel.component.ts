import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, catchError, finalize, of, switchMap, tap } from 'rxjs';
import { extractApiErrorMessage } from '../../../../core/models/api.models';
import {
  ASSISTANT_ACTION_OPTIONS,
  AssistantActionType,
  JobAssistantMessageResponse
} from '../../../../core/models/job-assistant.models';
import { JobAssistantService } from '../../../../core/services/job-assistant.service';
import { TimeAgoPipe } from '../../../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'app-job-assistant-panel',
  standalone: true,
  imports: [FormsModule, RouterLink, TimeAgoPipe],
  templateUrl: './job-assistant-panel.component.html',
  styleUrl: './job-assistant-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JobAssistantPanelComponent {
  private readonly assistantService = inject(JobAssistantService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);

  private readonly historyTrigger$ = new Subject<number | null>();

  readonly jobId = input<number | null>(null);
  readonly jobTitle = input<string>('');
  readonly companyName = input<string>('');

  readonly quickActions = ASSISTANT_ACTION_OPTIONS;

  readonly messages = signal<readonly JobAssistantMessageResponse[]>([]);
  readonly loadingHistory = signal<boolean>(false);
  readonly sendingAction = signal<AssistantActionType | null>(null);
  readonly savingMessageId = signal<number | null>(null);
  readonly deletingMessageId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);

  readonly customQuestion = signal<string>('');
  readonly additionalContext = signal<string>('');
  readonly contextOpen = signal<boolean>(false);

  readonly editingMessageId = signal<number | null>(null);
  readonly editDraftContent = signal<string>('');
  readonly copiedMessageId = signal<number | null>(null);

  constructor() {
    this.historyTrigger$
      .pipe(
        tap(() => {
          this.errorMessage.set(null);
          this.noticeMessage.set(null);
          this.editingMessageId.set(null);
          this.editDraftContent.set('');
          this.messages.set([]);
        }),
        switchMap(id => {
          if (!id || id <= 0) {
            this.loadingHistory.set(false);
            return of<readonly JobAssistantMessageResponse[] | null>(null);
          }
          this.loadingHistory.set(true);
          return this.assistantService.getHistory(id).pipe(
            catchError((err: unknown) => {
              this.errorMessage.set(
                extractApiErrorMessage(err, 'Unable to load assistant history for this job.')
              );
              return of<readonly JobAssistantMessageResponse[] | null>(null);
            }),
            finalize(() => this.loadingHistory.set(false))
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(history => {
        if (history) {
          this.messages.set(history);
        }
      });

    effect(() => {
      const currentJobId = this.jobId();
      this.historyTrigger$.next(currentJobId);
    });
  }

  runQuickAction(actionType: AssistantActionType): void {
    const id = this.jobId();
    if (!id || this.sendingAction() !== null) {
      return;
    }

    const extra = this.additionalContext().trim();
    this.invokeAssistant(id, {
      actionType,
      additionalContext: extra || undefined
    });
  }

  submitCustomQuestion(): void {
    const id = this.jobId();
    const prompt = this.customQuestion().trim();
    if (!id || !prompt || this.sendingAction() !== null) {
      return;
    }

    const extra = this.additionalContext().trim();
    this.invokeAssistant(
      id,
      {
        actionType: 'CUSTOM_QUESTION',
        userPrompt: prompt,
        additionalContext: extra || undefined
      },
      true
    );
  }

  startEdit(message: JobAssistantMessageResponse): void {
    this.editingMessageId.set(message.id);
    this.editDraftContent.set(message.editableContent ?? message.assistantContent);
    this.errorMessage.set(null);
  }

  cancelEdit(): void {
    this.editingMessageId.set(null);
    this.editDraftContent.set('');
  }

  saveEdit(message: JobAssistantMessageResponse): void {
    const id = this.jobId();
    const nextText = this.editDraftContent().trim();
    if (!id || !nextText || this.savingMessageId() !== null) {
      return;
    }

    this.savingMessageId.set(message.id);
    this.errorMessage.set(null);

    this.assistantService
      .updateMessage(id, message.id, { editableContent: nextText })
      .pipe(
        finalize(() => this.savingMessageId.set(null)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: updated => {
          this.messages.update(list => list.map(m => (m.id === updated.id ? updated : m)));
          this.editingMessageId.set(null);
          this.editDraftContent.set('');
          this.noticeMessage.set(`Saved edits to "${updated.assistantTitle}".`);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to save edited assistant output.')
          );
        }
      });
  }

  deleteTurn(message: JobAssistantMessageResponse): void {
    const id = this.jobId();
    if (!id || this.deletingMessageId() !== null) {
      return;
    }

    this.deletingMessageId.set(message.id);
    this.errorMessage.set(null);

    this.assistantService
      .deleteMessage(id, message.id)
      .pipe(
        finalize(() => this.deletingMessageId.set(null)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.messages.update(list => list.filter(m => m.id !== message.id));
          if (this.editingMessageId() === message.id) {
            this.cancelEdit();
          }
        },
        error: (err: unknown) => {
          this.errorMessage.set(extractApiErrorMessage(err, 'Unable to remove assistant turn.'));
        }
      });
  }

  copyContent(message: JobAssistantMessageResponse): void {
    const text = message.editableContent || message.assistantContent;
    const clipboard = this.document.defaultView?.navigator?.clipboard;
    if (clipboard?.writeText) {
      void clipboard.writeText(text).then(() => {
        this.copiedMessageId.set(message.id);
      });
    } else {
      this.copiedMessageId.set(message.id);
    }
  }

  private invokeAssistant(
    jobId: number,
    payload: {
      readonly actionType: AssistantActionType;
      readonly userPrompt?: string;
      readonly additionalContext?: string;
    },
    clearQuestion = false
  ): void {
    this.sendingAction.set(payload.actionType);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);

    this.assistantService
      .respond(jobId, payload)
      .pipe(
        finalize(() => this.sendingAction.set(null)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: created => {
          this.messages.update(list => [...list, created]);
          if (clearQuestion) {
            this.customQuestion.set('');
          }
          this.noticeMessage.set(`Generated ${created.actionLabel}. Edit or copy directly below.`);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(
              err,
              'AI Application Assistant is currently unavailable. Please retry when AI service is configured.'
            )
          );
        }
      });
  }
}

