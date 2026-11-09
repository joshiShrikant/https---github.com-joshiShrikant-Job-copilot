import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import {
  JobApplicationDetailResponse,
  toDateInputValue
} from '../../../../core/models/application.models';
import {
  FollowUpReminderItemDto,
  FollowUpRemindersResponse
} from '../../../../core/models/recruiter.models';

export interface CalendarMilestone {
  readonly id: string;
  readonly dateKey: string;
  readonly kind: 'INTERVIEW' | 'APP_FOLLOWUP' | 'RECRUITER_FOLLOWUP' | 'APPLIED';
  readonly label: string;
  readonly subLabel: string;
  readonly overdue: boolean;
  readonly completed: boolean;
  readonly applicationUuid: string | null;
  readonly recruiterId: number | null;
  readonly interactionId: number | null;
}

export interface CalendarDayCell {
  readonly dateKey: string;
  readonly dayNumber: number;
  readonly inCurrentMonth: boolean;
  readonly isToday: boolean;
  readonly milestones: readonly CalendarMilestone[];
}

const WEEKDAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

@Component({
  selector: 'app-applications-calendar',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './applications-calendar.component.html',
  styleUrl: './applications-calendar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApplicationsCalendarComponent {
  readonly applications = input.required<readonly JobApplicationDetailResponse[]>();
  readonly reminders = input<FollowUpRemindersResponse | null>(null);

  readonly selectApplication = output<string>();
  readonly selectRecruiter = output<number>();
  readonly markApplicationFollowUpDone = output<string>();
  readonly markRecruiterReminderDone = output<FollowUpReminderItemDto>();

  readonly weekdayLabels = WEEKDAY_LABELS;

  private readonly now = new Date();
  readonly activeYear = signal<number>(this.now.getFullYear());
  readonly activeMonthIndex = signal<number>(this.now.getMonth()); // 0..11

  readonly todayKey = toDateInputValue(this.now.toISOString());

  readonly monthHeading = computed<string>(() => {
    const dt = new Date(this.activeYear(), this.activeMonthIndex(), 1);
    return new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' }).format(dt);
  });

  readonly allMilestones = computed<readonly CalendarMilestone[]>(() => {
    const items: CalendarMilestone[] = [];
    const seenRecruiterInteractions = new Set<number>();

    for (const app of this.applications()) {
      if (app.nextInterviewDate) {
        const key = toDateInputValue(app.nextInterviewDate);
        if (key) {
          items.push({
            id: `int-${app.uuid}`,
            dateKey: key,
            kind: 'INTERVIEW',
            label: `Interview · ${app.companyName}`,
            subLabel: app.jobTitle,
            overdue: false,
            completed: false,
            applicationUuid: app.uuid,
            recruiterId: null,
            interactionId: null
          });
        }
      }
      if (app.nextFollowUpDate) {
        const key = toDateInputValue(app.nextFollowUpDate);
        if (key) {
          items.push({
            id: `fu-app-${app.uuid}`,
            dateKey: key,
            kind: 'APP_FOLLOWUP',
            label: `Follow-up · ${app.companyName}`,
            subLabel: app.jobTitle,
            overdue: app.overdueFollowUp,
            completed: app.followUpCompleted,
            applicationUuid: app.uuid,
            recruiterId: null,
            interactionId: null
          });
        }
      }
      if (app.appliedDate) {
        const key = toDateInputValue(app.appliedDate);
        if (key) {
          items.push({
            id: `app-${app.uuid}`,
            dateKey: key,
            kind: 'APPLIED',
            label: `Applied · ${app.companyName}`,
            subLabel: app.jobTitle,
            overdue: false,
            completed: true,
            applicationUuid: app.uuid,
            recruiterId: null,
            interactionId: null
          });
        }
      }
    }

    const reminderItems = this.reminders()?.items ?? [];
    for (const rem of reminderItems) {
      if (rem.sourceType === 'RECRUITER_INTERACTION' && rem.interactionId !== null) {
        if (seenRecruiterInteractions.has(rem.interactionId)) {
          continue;
        }
        seenRecruiterInteractions.add(rem.interactionId);
        items.push({
          id: `fu-rec-${rem.interactionId}`,
          dateKey: rem.reminderDate,
          kind: 'RECRUITER_FOLLOWUP',
          label: `Recruiter · ${rem.contactLabel}`,
          subLabel: rem.roleTitle ?? 'Recruiter outreach follow-up',
          overdue: rem.urgency === 'OVERDUE',
          completed: rem.completed,
          applicationUuid: rem.applicationUuid,
          recruiterId: rem.recruiterId,
          interactionId: rem.interactionId
        });
      }
    }

    return items.sort((a, b) => a.dateKey.localeCompare(b.dateKey));
  });

  readonly calendarDays = computed<readonly CalendarDayCell[]>(() => {
    const year = this.activeYear();
    const month = this.activeMonthIndex();
    const firstOfMonth = new Date(year, month, 1);
    const jsDay = firstOfMonth.getDay(); // 0 Sun..6 Sat
    const mondayOffset = (jsDay + 6) % 7;

    const milestoneMap = new Map<string, CalendarMilestone[]>();
    for (const item of this.allMilestones()) {
      const existing = milestoneMap.get(item.dateKey) ?? [];
      existing.push(item);
      milestoneMap.set(item.dateKey, existing);
    }

    const cells: CalendarDayCell[] = [];
    for (let idx = 0; idx < 42; idx++) {
      const cellDate = new Date(year, month, 1 - mondayOffset + idx);
      const cellKey = this.formatDateKey(cellDate);
      cells.push({
        dateKey: cellKey,
        dayNumber: cellDate.getDate(),
        inCurrentMonth: cellDate.getMonth() === month,
        isToday: cellKey === this.todayKey,
        milestones: milestoneMap.get(cellKey) ?? []
      });
    }
    return cells;
  });

  readonly agendaMilestones = computed<readonly CalendarMilestone[]>(() =>
    this.allMilestones().filter(m => m.kind !== 'APPLIED')
  );

  prevMonth(): void {
    const month = this.activeMonthIndex();
    if (month === 0) {
      this.activeYear.update(y => y - 1);
      this.activeMonthIndex.set(11);
    } else {
      this.activeMonthIndex.set(month - 1);
    }
  }

  nextMonth(): void {
    const month = this.activeMonthIndex();
    if (month === 11) {
      this.activeYear.update(y => y + 1);
      this.activeMonthIndex.set(0);
    } else {
      this.activeMonthIndex.set(month + 1);
    }
  }

  jumpToToday(): void {
    const today = new Date();
    this.activeYear.set(today.getFullYear());
    this.activeMonthIndex.set(today.getMonth());
  }

  onMilestoneClick(milestone: CalendarMilestone): void {
    if (milestone.applicationUuid) {
      this.selectApplication.emit(milestone.applicationUuid);
    } else if (milestone.recruiterId !== null) {
      this.selectRecruiter.emit(milestone.recruiterId);
    }
  }

  onCompleteAgendaItem(milestone: CalendarMilestone): void {
    if (milestone.kind === 'APP_FOLLOWUP' && milestone.applicationUuid) {
      this.markApplicationFollowUpDone.emit(milestone.applicationUuid);
      return;
    }
    if (milestone.kind === 'RECRUITER_FOLLOWUP' && milestone.interactionId !== null) {
      const match = this.reminders()?.items.find(
        i => i.sourceType === 'RECRUITER_INTERACTION' && i.interactionId === milestone.interactionId
      );
      if (match) {
        this.markRecruiterReminderDone.emit(match);
      }
    }
  }

  private formatDateKey(d: Date): string {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}

