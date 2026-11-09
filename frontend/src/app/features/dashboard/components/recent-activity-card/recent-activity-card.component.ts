import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardActivityItem } from '../../../../core/models/dashboard.models';

@Component({
  selector: 'app-recent-activity-card',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './recent-activity-card.component.html',
  styleUrl: './recent-activity-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RecentActivityCardComponent {
  readonly items = input.required<readonly DashboardActivityItem[]>();

  iconForType(type: string): string {
    switch (type) {
      case 'APPLICATION':
        return 'view_kanban';
      case 'SAVED_JOB':
        return 'bookmark';
      case 'RESUME':
        return 'description';
      case 'NOTIFICATION':
        return 'notifications';
      default:
        return 'bolt';
    }
  }
}

