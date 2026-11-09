import { DatePipe, DecimalPipe } from '@angular/common';
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
import { catchError, finalize, forkJoin, of } from 'rxjs';
import { extractApiErrorMessage } from '../../core/models/api.models';
import {
  InterviewPerformanceHistoryResponse,
  InterviewPrepSessionDetail,
  InterviewPrepSessionSummary,
  InterviewQuestion,
  QUESTION_CATEGORY_OPTIONS,
  QUESTION_DIFFICULTY_OPTIONS,
  QuestionCategory,
  QuestionDifficulty,
  formatQuestionCategoryLabel
} from '../../core/models/interview-prep.models';
import { JobDetail, JobSummary } from '../../core/models/job.models';
import { InterviewPrepService } from '../../core/services/interview-prep.service';
import { JobService } from '../../core/services/job.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';

interface ScoreTrendSvgPoint {
  readonly sessionId: number;
  readonly sessionTitle: string;
  readonly score: number;
  readonly x: number;
  readonly y: number;
}

@Component({
  selector: 'app-interview-prep',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    PageHeaderComponent,
    ConfirmDialogComponent,
    TimeAgoPipe
  ],
  templateUrl: './interview-prep.component.html',
  styleUrl: './interview-prep.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InterviewPrepComponent implements OnInit {
  private readonly interviewPrepService = inject(InterviewPrepService);
  private readonly jobService = inject(JobService);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly destroyRef = inject(DestroyRef);

  readonly categoryOptions = QUESTION_CATEGORY_OPTIONS;
  readonly difficultyOptions = QUESTION_DIFFICULTY_OPTIONS;
  readonly formatCategory = formatQuestionCategoryLabel;

  readonly sessions = signal<readonly InterviewPrepSessionSummary[]>([]);
  readonly activeSession = signal<InterviewPrepSessionDetail | null>(null);
  readonly history = signal<InterviewPerformanceHistoryResponse | null>(null);
  readonly extraDeepLinkedJob = signal<JobDetail | null>(null);

  // Builder form signals
  readonly selectedJobId = signal<number | null>(null);
  readonly sessionTitle = signal<string>('');
  readonly selectedCategories = signal<readonly QuestionCategory[]>([
    'ANGULAR',
    'JAVA',
    'SPRING_BOOT',
    'SYSTEM_DESIGN'
  ]);
  readonly difficulty = signal<QuestionDifficulty>('SENIOR');
  readonly questionCount = signal<number>(6);
  readonly focusNotes = signal<string>('');

  // Practice mode & async states
  readonly answerDrafts = signal<Record<number, string>>({});
  readonly expandedIdealIds = signal<ReadonlySet<number>>(new Set());
  readonly loadingWorkspace = signal<boolean>(false);
  readonly loadingSession = signal<boolean>(false);
  readonly creatingSession = signal<boolean>(false);
  readonly submittingQuestionId = signal<number | null>(null);
  readonly deletingSessionId = signal<number | null>(null);
  readonly pendingDeleteSession = signal<InterviewPrepSessionSummary | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);

  private deepLinkJobId: number | null = null;
  private deepLinkSessionId: number | null = null;
  private fetchingDeepLinkedJobId: number | null = null;

  readonly availableJobs = computed<readonly JobSummary[]>(() => {
    const base = this.jobService.jobs();
    const extra = this.extraDeepLinkedJob();
    if (!extra || base.some(item => item.id === extra.id)) {
      return base;
    }
    const mapped: JobSummary = {
      id: extra.id,
      title: extra.title,
      companyName: extra.companyName,
      location: extra.location ?? null,
      city: extra.city ?? null,
      country: extra.country,
      workArrangement: extra.workArrangement,
      employmentType: extra.employmentType ?? null,
      experienceLevel: extra.experienceLevel ?? null,
      salaryMin: extra.salaryMin ?? null,
      salaryMax: extra.salaryMax ?? null,
      salaryCurrency: extra.salaryCurrency ?? null,
      salaryPeriod: extra.salaryPeriod ?? null,
      sponsorStatus: extra.sponsorStatus,
      matchPercentage: extra.matchPercentage ?? null,
      sourceProvider: extra.sourceProvider ?? 'INDEXED',
      postedAt: extra.postedAt ?? new Date().toISOString(),
      isSaved: Boolean(extra.isSaved),
      isInterested: Boolean(extra.isInterested),
      isHidden: Boolean(extra.isHidden),
      skills: extra.skills ?? [],
      descriptionSnippet: extra.descriptionSnippet ?? null,
      originalUrl: extra.originalUrl ?? null
    };
    return [mapped, ...base];
  });

  readonly headerBadge = computed<string>(() => {
    const h = this.history();
    if (!h) {
      return 'AI Prep Lab';
    }
    if (h.averageScore !== null && h.averageScore !== undefined) {
      return `${h.totalSessions} Sessions · Avg ${Math.round(h.averageScore)}%`;
    }
    return `${h.totalSessions} Sessions`;
  });

  readonly deleteDialogMessage = computed<string>(() => {
    const target = this.pendingDeleteSession();
    return target
      ? `Delete interview session "${target.sessionTitle}" for ${target.companyName} and remove all recorded answers and grades?`
      : '';
  });

  readonly trendChartData = computed<{
    readonly points: readonly ScoreTrendSvgPoint[];
    readonly polyline: string;
  }>(() => {
    const scored = (this.history()?.recentScoreTrend ?? []).filter(
      pt => pt.score !== null && pt.score !== undefined
    );
    if (scored.length === 0) {
      return { points: [], polyline: '' };
    }

    const width = 460;
    const height = 130;
    const padX = 24;
    const padY = 18;
    const plotW = width - padX * 2;
    const plotH = height - padY * 2;

    const points: ScoreTrendSvgPoint[] = scored.map((pt, idx) => {
      const score = Math.max(0, Math.min(100, Number(pt.score ?? 0)));
      const x = scored.length === 1 ? width / 2 : padX + (idx / (scored.length - 1)) * plotW;
      const y = height - padY - (score / 100) * plotH;
      return {
        sessionId: pt.sessionId,
        sessionTitle: pt.sessionTitle,
        score,
        x: Math.round(x * 10) / 10,
        y: Math.round(y * 10) / 10
      };
    });

    const polyline = points.map(p => `${p.x},${p.y}`).join(' ');
    return { points, polyline };
  });

  ngOnInit(): void {
    this.route?.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(params => {
        const rawJobId = params.get('jobId');
        const parsedJobId = rawJobId ? Number(rawJobId) : NaN;
        if (Number.isFinite(parsedJobId) && parsedJobId > 0) {
          this.deepLinkJobId = Math.trunc(parsedJobId);
          this.selectedJobId.set(this.deepLinkJobId);
          this.ensureDeepLinkedJobLoaded(this.deepLinkJobId);
        }

        const rawSessionId = params.get('sessionId');
        const parsedSessionId = rawSessionId ? Number(rawSessionId) : NaN;
        if (Number.isFinite(parsedSessionId) && parsedSessionId > 0) {
          this.deepLinkSessionId = Math.trunc(parsedSessionId);
        }
      });

    this.loadWorkspace();
  }

  loadWorkspace(): void {
    this.loadingWorkspace.set(true);
    this.errorMessage.set(null);

    forkJoin({
      jobs: this.jobService.searchJobs({}, 0, 50).pipe(catchError(() => of(null))),
      sessions: this.interviewPrepService.listSessions(),
      history: this.interviewPrepService.getHistory().pipe(catchError(() => of(null)))
    })
      .pipe(
        finalize(() => this.loadingWorkspace.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: res => {
          this.sessions.set(res.sessions);
          if (res.history) {
            this.history.set(res.history);
          }

          if (this.deepLinkJobId !== null) {
            this.selectedJobId.set(this.deepLinkJobId);
            this.ensureDeepLinkedJobLoaded(this.deepLinkJobId);
          } else if (this.selectedJobId() === null && this.availableJobs().length > 0) {
            this.selectedJobId.set(this.availableJobs()[0].id);
          }

          const currentActiveId = this.activeSession()?.id;
          if (this.deepLinkSessionId !== null) {
            const targetId = this.deepLinkSessionId;
            this.deepLinkSessionId = null;
            this.openSession(targetId);
          } else if (
            currentActiveId &&
            res.sessions.some(item => item.id === currentActiveId)
          ) {
            // Keep currently loaded session
          } else if (res.sessions.length > 0) {
            const preferred =
              this.deepLinkJobId !== null
                ? res.sessions.find(s => s.jobId === this.deepLinkJobId)
                : res.sessions[0];
            if (preferred) {
              this.openSession(preferred.id);
            }
          }
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to load interview preparation workspace.')
          );
        }
      });
  }

  toggleCategory(category: QuestionCategory): void {
    const current = this.selectedCategories();
    if (current.includes(category)) {
      if (current.length <= 1) {
        return;
      }
      this.selectedCategories.set(current.filter(item => item !== category));
    } else {
      this.selectedCategories.set([...current, category]);
    }
  }

  hasCategory(category: QuestionCategory): boolean {
    return this.selectedCategories().includes(category);
  }

  onQuestionCountChange(value: number | string): void {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      this.questionCount.set(Math.max(3, Math.min(12, Math.trunc(parsed))));
    }
  }

  createSession(): void {
    const jobId = this.selectedJobId();
    if (!jobId || jobId <= 0) {
      this.errorMessage.set('Please select a target European job to generate interview questions.');
      return;
    }

    if (this.creatingSession()) {
      return;
    }

    const safeCount = Math.max(3, Math.min(12, Math.trunc(Number(this.questionCount()) || 6)));
    this.questionCount.set(safeCount);

    this.creatingSession.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);

    this.interviewPrepService
      .createSession({
        jobId,
        sessionTitle: this.sessionTitle().trim() || undefined,
        categories: [...this.selectedCategories()],
        difficulty: this.difficulty(),
        questionCount: safeCount,
        focusNotes: this.focusNotes().trim() || undefined
      })
      .pipe(
        finalize(() => this.creatingSession.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: created => {
          this.applySessionDetail(created);
          this.sessionTitle.set('');
          this.focusNotes.set('');
          this.noticeMessage.set(
            `Generated "${created.sessionTitle}" with ${created.questions.length} targeted practice questions.`
          );
          this.refreshSessionsAndHistory();
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(
              err,
              'AI Interview Preparation is currently unavailable. Please retry once AI is configured.'
            )
          );
        }
      });
  }

  openSession(sessionId: number): void {
    if (sessionId <= 0) {
      return;
    }
    this.loadingSession.set(true);
    this.errorMessage.set(null);

    this.interviewPrepService
      .getSession(sessionId)
      .pipe(
        finalize(() => this.loadingSession.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: detail => {
          this.applySessionDetail(detail);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to load interview preparation session.')
          );
        }
      });
  }

  draftForQuestion(questionId: number): string {
    return this.answerDrafts()[questionId] ?? '';
  }

  onAnswerDraftInput(questionId: number, value: string): void {
    this.answerDrafts.update(current => ({
      ...current,
      [questionId]: value
    }));
  }

  submitAnswer(question: InterviewQuestion): void {
    const session = this.activeSession();
    const draft = (this.answerDrafts()[question.id] ?? '').trim();
    if (!session || !draft) {
      this.errorMessage.set('Please enter your practice answer before requesting AI grading.');
      return;
    }

    if (this.submittingQuestionId() !== null) {
      return;
    }

    this.submittingQuestionId.set(question.id);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);

    this.interviewPrepService
      .submitAnswer(session.id, {
        questionId: question.id,
        userAnswer: draft
      })
      .pipe(
        finalize(() => this.submittingQuestionId.set(null)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: updated => {
          this.applySessionDetail(updated);
          const graded = updated.questions.find(q => q.id === question.id);
          this.noticeMessage.set(
            graded?.score !== null && graded?.score !== undefined
              ? `Graded Q${question.orderIndex + 1} (${graded.score}/100). Session average updated.`
              : `Submitted answer for Q${question.orderIndex + 1}.`
          );
          this.refreshSessionsAndHistory();
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(
              err,
              'AI answer grading is currently unavailable. Your draft answer has been preserved.'
            )
          );
        }
      });
  }

  toggleIdealAnswer(questionId: number): void {
    this.expandedIdealIds.update(current => {
      const next = new Set(current);
      if (next.has(questionId)) {
        next.delete(questionId);
      } else {
        next.add(questionId);
      }
      return next;
    });
  }

  requestDeleteSession(session: InterviewPrepSessionSummary): void {
    this.pendingDeleteSession.set(session);
  }

  confirmDeleteSession(): void {
    const target = this.pendingDeleteSession();
    if (!target || this.deletingSessionId() !== null) {
      return;
    }

    this.deletingSessionId.set(target.id);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);

    this.interviewPrepService
      .deleteSession(target.id)
      .pipe(
        finalize(() => {
          this.deletingSessionId.set(null);
          this.pendingDeleteSession.set(null);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.sessions.update(list => list.filter(item => item.id !== target.id));
          if (this.activeSession()?.id === target.id) {
            this.activeSession.set(null);
          }
          this.noticeMessage.set(`Deleted interview session "${target.sessionTitle}".`);
          this.refreshSessionsAndHistory();
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to delete interview session.')
          );
        }
      });
  }

  scoreBadgeClass(score: number | null): string {
    if (score === null || score === undefined) {
      return 'score-neutral';
    }
    if (score >= 80) {
      return 'score-strong';
    }
    if (score >= 60) {
      return 'score-good';
    }
    return 'score-needs-work';
  }

  private applySessionDetail(detail: InterviewPrepSessionDetail): void {
    this.activeSession.set(detail);
    this.answerDrafts.update(current => {
      const next = { ...current };
      for (const q of detail.questions) {
        if (q.userAnswer !== null && q.userAnswer !== undefined) {
          next[q.id] = q.userAnswer;
        } else if (!(q.id in next)) {
          next[q.id] = '';
        }
      }
      return next;
    });
  }

  private refreshSessionsAndHistory(): void {
    forkJoin({
      sessions: this.interviewPrepService.listSessions(),
      history: this.interviewPrepService.getHistory().pipe(catchError(() => of(null)))
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: res => {
          this.sessions.set(res.sessions);
          if (res.history) {
            this.history.set(res.history);
          }
        },
        error: () => {
          // Non-fatal background refresh
        }
      });
  }

  private ensureDeepLinkedJobLoaded(jobId: number): void {
    if (
      this.fetchingDeepLinkedJobId === jobId ||
      this.availableJobs().some(item => item.id === jobId)
    ) {
      return;
    }
    this.fetchingDeepLinkedJobId = jobId;
    this.jobService
      .getJobDetail(jobId)
      .pipe(
        catchError(() => of(null)),
        finalize(() => {
          if (this.fetchingDeepLinkedJobId === jobId) {
            this.fetchingDeepLinkedJobId = null;
          }
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(detail => {
        if (detail) {
          this.extraDeepLinkedJob.set(detail);
        }
      });
  }
}
