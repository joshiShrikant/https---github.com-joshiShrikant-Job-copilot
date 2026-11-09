import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import {
  NotificationItem,
  PagedNotificationsResponse
} from '../../core/models/notification.models';
import { NotificationService } from '../../core/services/notification.service';
import { NotificationsComponent } from './notifications.component';

describe('NotificationsComponent', () => {
  let fixture: ComponentFixture<NotificationsComponent>;
  let httpMock: HttpTestingController;
  let notificationService: NotificationService;
  let router: Router;

  const itemUnreadJob: NotificationItem = {
    id: 1,
    type: 'HIGH_MATCH_JOB',
    title: '92% Match: Senior Angular & Java Engineer',
    message: 'Adyen posted a sponsorship-verified role in Amsterdam.',
    referenceType: 'JOB',
    referenceId: 501,
    isRead: false,
    readAt: null,
    createdAt: '2026-11-09T10:00:00Z',
    targetRoute: '/jobs'
  };

  const itemReadReminder: NotificationItem = {
    id: 2,
    type: 'APPLICATION_FOLLOW_UP',
    title: 'Follow-up due for Personio SE',
    message: 'Scheduled follow-up date is today.',
    referenceType: 'JOB_APPLICATION',
    referenceId: 77,
    isRead: true,
    readAt: '2026-11-09T09:00:00Z',
    createdAt: '2026-11-09T08:00:00Z',
    targetRoute: '/applications'
  };

  function makePage(
    content: readonly NotificationItem[],
    unreadCount: number
  ): PagedNotificationsResponse {
    return {
      content,
      pageNumber: 0,
      pageSize: 20,
      totalElements: content.length,
      totalPages: 1,
      isFirst: true,
      isLast: true,
      hasNext: false,
      hasPrevious: false,
      unreadCount
    };
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationsComponent);
    httpMock = TestBed.inject(HttpTestingController);
    notificationService = TestBed.inject(NotificationService);
    router = TestBed.inject(Router);
    notificationService.resetState();
  });

  afterEach(() => {
    httpMock.verify();
    notificationService.resetState();
  });

  it('should load notifications, mark single read, navigate to target, filter, mark all read, and delete', () => {
    fixture.detectChanges();

    const initReq = httpMock.expectOne(
      req =>
        req.url === '/api/v1/notifications' &&
        req.params.get('unreadOnly') === 'false' &&
        req.params.get('page') === '0'
    );
    initReq.flush(makePage([itemUnreadJob, itemReadReminder], 1));
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(notificationService.unreadCount()).toBe(1);
    expect(host.querySelectorAll('[data-notif-card]').length).toBe(2);
    expect(host.querySelector('[data-notif-unread-badge="1"]')).toBeTruthy();

    // Mark notification 1 as read
    host.querySelector<HTMLButtonElement>('[data-notif-mark-read="1"]')?.click();
    const readReq = httpMock.expectOne('/api/v1/notifications/1/read');
    expect(readReq.request.method).toBe('PATCH');
    readReq.flush({ ...itemUnreadJob, isRead: true, readAt: '2026-11-09T10:05:00Z' });
    fixture.detectChanges();

    expect(notificationService.unreadCount()).toBe(0);
    expect(host.querySelector('[data-notif-unread-badge="1"]')).toBeNull();

    // Deep-link navigation
    const navSpy = spyOn(router, 'navigate').and.resolveTo(true);
    host.querySelector<HTMLButtonElement>('[data-notif-open="1"]')?.click();
    expect(navSpy).toHaveBeenCalledWith(['/jobs'], { queryParams: { jobId: 501 } });

    // Filter by unreadOnly and type
    const unreadCheckbox = host.querySelector<HTMLInputElement>('[data-notif-unread-only]')!;
    unreadCheckbox.checked = true;
    unreadCheckbox.dispatchEvent(new Event('change'));
    const unreadReq = httpMock.expectOne(
      req => req.url === '/api/v1/notifications' && req.params.get('unreadOnly') === 'true'
    );
    unreadReq.flush(makePage([itemUnreadJob], 1));
    fixture.detectChanges();

    // Mark all read
    host.querySelector<HTMLButtonElement>('[data-notif-mark-all]')?.click();
    const markAllReq = httpMock.expectOne('/api/v1/notifications/mark-all-read');
    expect(markAllReq.request.method).toBe('POST');
    markAllReq.flush({ markedCount: 1, unreadCount: 0 });
    fixture.detectChanges();
    expect(notificationService.unreadCount()).toBe(0);

    // Delete item
    host.querySelector<HTMLButtonElement>('[data-notif-delete="1"]')?.click();
    const deleteReq = httpMock.expectOne('/api/v1/notifications/1');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush({ success: true, message: 'Notification deleted' });
    fixture.detectChanges();

    expect(host.querySelector('[data-notif-empty]')).toBeTruthy();
  });

  it('should display error alert when loading notifications fails', () => {
    fixture.detectChanges();

    httpMock
      .expectOne(req => req.url === '/api/v1/notifications')
      .flush({ message: 'Notification store unavailable' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-notif-error]')?.textContent).toContain(
      'Notification store unavailable'
    );
  });
});

