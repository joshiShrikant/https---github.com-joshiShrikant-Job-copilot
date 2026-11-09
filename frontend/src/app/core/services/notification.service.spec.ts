import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should synchronize unreadCount signal across load, markRead, markAllRead, and delete', () => {
    service.loadUnreadCount().subscribe();
    httpMock.expectOne('/api/v1/notifications/unread-count').flush({ unreadCount: 4 });
    expect(service.unreadCount()).toBe(4);

    service.loadNotifications({ unreadOnly: true, type: 'HIGH_MATCH_JOB', page: 0, size: 10 }).subscribe();
    const listReq = httpMock.expectOne(r => r.url === '/api/v1/notifications');
    expect(listReq.request.params.get('unreadOnly')).toBe('true');
    expect(listReq.request.params.get('type')).toBe('HIGH_MATCH_JOB');
    listReq.flush({
      content: [],
      pageNumber: 0,
      pageSize: 10,
      totalElements: 3,
      totalPages: 1,
      isFirst: true,
      isLast: true,
      hasNext: false,
      hasPrevious: false,
      unreadCount: 3
    });
    expect(service.unreadCount()).toBe(3);

    service.markRead(9).subscribe();
    const readReq = httpMock.expectOne('/api/v1/notifications/9/read');
    expect(readReq.request.method).toBe('PATCH');
    readReq.flush({ id: 9, isRead: true });
    expect(service.unreadCount()).toBe(2);

    service.markAllRead().subscribe();
    httpMock.expectOne('/api/v1/notifications/mark-all-read').flush({ markedCount: 2, unreadCount: 0 });
    expect(service.unreadCount()).toBe(0);

    service.deleteNotification(9).subscribe();
    const delReq = httpMock.expectOne('/api/v1/notifications/9');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush({ success: true, message: 'Deleted' });
  });
});

