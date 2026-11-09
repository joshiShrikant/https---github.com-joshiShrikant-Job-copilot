import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import {
  APPLICATION_STAGE_LIST,
  ApplicationStatus,
  JobApplicationDetailResponse,
  formatSalaryOffer
} from '../../../../core/models/application.models';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';
import { ApplicationStageMoveEvent } from '../kanban-board/kanban-board.component';

type SortField = 'updatedAt' | 'appliedDate' | 'nextInterviewDate' | 'nextFollowUpDate' | 'companyName';

@Component({
  selector: 'app-applications-table',
  standalone: true,
  imports: [DatePipe, StatusBadgeComponent],
  templateUrl: './applications-table.component.html',
  styleUrl: './applications-table.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApplicationsTableComponent {
  readonly applications = input.required<readonly JobApplicationDetailResponse[]>();
  readonly transitioningUuid = input<string | null>(null);

  readonly selectApplication = output<string>();
  readonly moveStageRequested = output<ApplicationStageMoveEvent>();

  readonly stages = APPLICATION_STAGE_LIST;
  readonly sortField = signal<SortField>('updatedAt');
  readonly sortAsc = signal<boolean>(false);

  readonly sortedRows = computed<readonly JobApplicationDetailResponse[]>(() => {
    const field = this.sortField();
    const asc = this.sortAsc();
    const dir = asc ? 1 : -1;

    return [...this.applications()].sort((a, b) => {
      if (field === 'companyName') {
        return a.companyName.localeCompare(b.companyName) * dir;
      }
      const valA = a[field] ? new Date(a[field]!).getTime() : 0;
      const valB = b[field] ? new Date(b[field]!).getTime() : 0;
      return (valA - valB) * dir;
    });
  });

  toggleSort(field: SortField): void {
    if (this.sortField() === field) {
      this.sortAsc.update(v => !v);
    } else {
      this.sortField.set(field);
      this.sortAsc.set(field === 'companyName' || field === 'nextInterviewDate' || field === 'nextFollowUpDate');
    }
  }

  onStageChange(app: JobApplicationDetailResponse, event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    const nextStatus = select?.value as ApplicationStatus | undefined;
    if (!nextStatus || nextStatus === app.status) {
      return;
    }
    this.moveStageRequested.emit({ uuid: app.uuid, newStatus: nextStatus });
  }

  formatOffer(app: JobApplicationDetailResponse): string {
    return formatSalaryOffer(app.currentSalaryOffered, app.offeredCurrency) ?? '—';
  }
}

