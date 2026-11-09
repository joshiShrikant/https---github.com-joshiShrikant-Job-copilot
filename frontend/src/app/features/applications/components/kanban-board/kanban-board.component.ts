import {
  CdkDrag,
  CdkDragDrop,
  CdkDropList,
  CdkDropListGroup
} from '@angular/cdk/drag-drop';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import {
  APPLICATION_STAGE_LIST,
  ApplicationStatus,
  JobApplicationDetailResponse,
  formatSalaryOffer
} from '../../../../core/models/application.models';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';

export interface ApplicationStageMoveEvent {
  readonly uuid: string;
  readonly newStatus: ApplicationStatus;
}

@Component({
  selector: 'app-kanban-board',
  standalone: true,
  imports: [CdkDropListGroup, CdkDropList, CdkDrag, DatePipe, StatusBadgeComponent],
  templateUrl: './kanban-board.component.html',
  styleUrl: './kanban-board.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class KanbanBoardComponent {
  readonly applications = input.required<readonly JobApplicationDetailResponse[]>();
  readonly transitioningUuid = input<string | null>(null);

  readonly selectApplication = output<string>();
  readonly moveStageRequested = output<ApplicationStageMoveEvent>();

  readonly stages = APPLICATION_STAGE_LIST;

  readonly columns = computed(() => {
    const grouped = new Map<ApplicationStatus, JobApplicationDetailResponse[]>();
    for (const stage of APPLICATION_STAGE_LIST) {
      grouped.set(stage.status, []);
    }
    for (const app of this.applications()) {
      const bucket = grouped.get(app.status);
      if (bucket) {
        bucket.push(app);
      }
    }
    return APPLICATION_STAGE_LIST.map(stage => ({
      meta: stage,
      items: grouped.get(stage.status) ?? []
    }));
  });

  dropListId(status: ApplicationStatus): string {
    return `kanban-col-${status}`;
  }

  onCardDrop(
    targetStatus: ApplicationStatus,
    event: CdkDragDrop<JobApplicationDetailResponse[]>
  ): void {
    const dragged = event.item.data as JobApplicationDetailResponse | undefined;
    if (!dragged || dragged.status === targetStatus) {
      return;
    }
    this.moveStageRequested.emit({ uuid: dragged.uuid, newStatus: targetStatus });
  }

  onQuickStageChange(uuid: string, currentStatus: ApplicationStatus, event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    const nextStatus = select?.value as ApplicationStatus | undefined;
    if (!nextStatus || nextStatus === currentStatus) {
      return;
    }
    this.moveStageRequested.emit({ uuid, newStatus: nextStatus });
  }

  formatOffer(app: JobApplicationDetailResponse): string | null {
    return formatSalaryOffer(app.currentSalaryOffered, app.offeredCurrency);
  }
}
