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
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractApiErrorMessage } from '../../core/models/api.models';
import {
  NOTIFICATION_TYPE_OPTIONS,
  NotificationItem,
  NotificationType,
  PagedNotificationsResponse,
  formatNotificationTypeLabel,
  getNotificationIcon
} from '../../core/models/notification.models';
import { NotificationService } from '../../core/services/notification.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [FormsModule, RouterLink, PageHeaderComponent, TimeAgoPipe],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotificationsComponent implements OnInit {
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly typeOptions = NOTIFICATION_TYPE_OPTIONS;
  readonly unreadCount = this.notificationService.unreadCount;

  readonly pagedResponse = signal<PagedNotificationsResponse | null>(null);
  readonly unreadOnly = signal<boolean>(false);
  readonly selectedType = signal<NotificationType | ''>('');
  readonly page = signal<number>(0);

  readonly loading = signal<boolean>(false);
  readonly markingAll = signal<boolean>(false);
  readonly busyIds = signal<ReadonlySet<number>>(new Set());
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);

  readonly headerBadge = computed<string>(() => {
    const count = this.unreadCount();
    return count > 0 ? `${count} Unread` : 'All Caught Up';
  });

  readonly formatType = formatNotificationTypeLabel;
  readonly iconForType = getNotificationIcon;

  ngOnInit(): void {
    this.loadNotifications(0);
  }

  loadNotifications(nextPage = this.page()): void {
    this.page.set(Math.max(0, nextPage));
    this.loading.set(true);
    this.errorMessage.set(null);

    this.notificationService
      .loadNotifications({
        unreadOnly: this.unreadOnly(),
        type: this.selectedType(),
        page: this.page(),
        size: 20
      })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: response => {
          this.pagedResponse.set(response);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to load your notifications right now.')
          );
        }
      });
  }

  onUnreadOnlyChange(unreadOnly: boolean): void {
    this.unreadOnly.set(Boolean(unreadOnly));
    this.loadNotifications(0);
  }

  onTypeChange(typeValue: string): void {
    this.selectedType.set((typeValue as NotificationType | '') || '');
    this.loadNotifications(0);
  }

  markRead(item: NotificationItem): void {
    if (item.isRead || this.busyIds().has(item.id)) {
      return;
    }

    this.addBusyId(item.id);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);

    this.notificationService
      .markRead(item.id)
      .pipe(
        finalize(() => this.removeBusyId(item.id)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: updated => {
          this.pagedResponse.update(current => {
            if (!current) {
              return current;
            }
            return {
              ...current,
              unreadCount: this.notificationService.unreadCount(),
              content: current.content.map(entry => (entry.id === updated.id ? updated : entry))
            };
          });
        },
        error: (err: unknown) => {
          this.errorMessage.set(extractApiErrorMessage(err, 'Unable to mark notification as read.'));
        }
      });
  }

  markAllRead(): void {
    if (this.markingAll() || this.unreadCount() === 0) {
      return;
    }

    this.markingAll.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);

    this.notificationService
      .markAllRead()
      .pipe(
        finalize(() => this.markingAll.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: res => {
          const nowIso = new Date().toISOString();
          this.pagedResponse.update(current => {
            if (!current) {
              return current;
            }
            return {
              ...current,
              unreadCount: res.unreadCount,
              content: current.content.map(entry =>
                entry.isRead ? entry : { ...entry, isRead: true, readAt: entry.readAt ?? nowIso }
              )
            };
          });
          this.noticeMessage.set(
            res.markedCount > 0
              ? `Marked ${res.markedCount} notification(s) as read.`
              : 'All notifications are already read.'
          );
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to mark all notifications as read.')
          );
        }
      });
  }

  deleteNotification(item: NotificationItem): void {
    if (this.busyIds().has(item.id)) {
      return;
    }

    this.addBusyId(item.id);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);

    this.notificationService
      .deleteNotification(item.id)
      .pipe(
        finalize(() => this.removeBusyId(item.id)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          if (!item.isRead) {
            this.notificationService.setUnreadCount(
              Math.max(0, this.notificationService.unreadCount() - 1)
            );
          }

          let shouldLoadPreviousPage = false;
          this.pagedResponse.update(current => {
            if (!current) {
              return current;
            }
            const nextContent = current.content.filter(entry => entry.id !== item.id);
            if (nextContent.length === 0 && current.pageNumber > 0) {
              shouldLoadPreviousPage = true;
            }
            return {
              ...current,
              unreadCount: this.notificationService.unreadCount(),
              totalElements: Math.max(0, current.totalElements - 1),
              content: nextContent
            };
          });

          this.noticeMessage.set(`Deleted notification "${item.title}".`);
          if (shouldLoadPreviousPage) {
            this.loadNotifications(this.page() - 1);
          }
        },
        error: (err: unknown) => {
          this.errorMessage.set(extractApiErrorMessage(err, 'Unable to delete notification.'));
        }
      });
  }

  canOpenTarget(item: NotificationItem): boolean {
    const route = item.targetRoute?.trim();
    return Boolean(route && route !== '/notifications');
  }

  openTarget(item: NotificationItem): void {
    const route = item.targetRoute?.trim();
    if (!route || route === '/notifications') {
      return;
    }

    if (item.referenceType === 'RECRUITER_INTERACTION' || route === '/recruiters') {
      void this.router.navigate(['/applications'], {
        queryParams: { tab: 'recruiter' }
      });
      return;
    }

    if (item.referenceType === 'JOB' && item.referenceId) {
      void this.router.navigate([route], {
        queryParams: { jobId: item.referenceId }
      });
      return;
    }

    if (item.referenceType === 'INTERVIEW_SESSION' && item.referenceId) {
      void this.router.navigate([route], {
        queryParams: { sessionId: item.referenceId }
      });
      return;
    }

    void this.router.navigateByUrl(route);
  }

  private addBusyId(id: number): void {
    this.busyIds.update(set => new Set(set).add(id));
  }

  private removeBusyId(id: number): void {
    this.busyIds.update(set => {
      const next = new Set(set);
      next.delete(id);
      return next;
    });
  }
}

