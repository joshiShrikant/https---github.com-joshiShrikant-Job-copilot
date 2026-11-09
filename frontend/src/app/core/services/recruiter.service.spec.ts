import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  FollowUpRemindersResponse,
  OutreachDraftResponse,
  RecruiterInteractionDto,
  RecruiterResponse
} from '../models/recruiter.models';
import { RecruiterService } from './recruiter.service';

describe('RecruiterService', () => {
  let service: RecruiterService;
  let httpMock: HttpTestingController;

  const baseRecruiter: RecruiterResponse = {
    id: 12,
    fullName: 'Sophie van Dijk',
    roleTitle: 'Senior Technical Recruiter',
    companyId: 7,
    companyName: 'Adyen N.V.',
    companyVerifiedSponsor: true,
    email: 'sophie@adyen.example',
    linkedinUrl: 'https://www.linkedin.com/in/sophievandijk',
    phone: '+31 20 123 4567',
    notes: 'Leads NL backend hiring',
    interactionsCount: 0,
    lastInteractionAt: null,
    nextFollowUpReminderDate: null,
    pendingRemindersCount: 0,
    interactions: [],
    createdAt: '2026-11-08T10:00:00Z',
    updatedAt: '2026-11-08T10:00:00Z'
  };

  const draftInteraction: RecruiterInteractionDto = {
    id: 44,
    recruiterId: 12,
    recruiterName: 'Sophie van Dijk',
    companyName: 'Adyen N.V.',
    applicationUuid: 'app-uuid-1',
    jobId: 101,
    jobTitle: 'Staff Java Clearing Architect',
    channel: 'LINKEDIN',
    messageType: 'LINKEDIN_CONNECTION',
    status: 'DRAFT',
    subjectLine: 'Connection Request',
    messageBody: 'Hi Sophie, excited about Staff Java Clearing roles at Adyen.',
    sentAt: null,
    approvedAt: null,
    followUpReminderDate: '2026-11-18',
    reminderCompleted: false,
    overdueReminder: false,
    aiGenerated: true,
    aiProvider: 'GEMINI',
    aiModelUsed: 'gemini-2.5-flash',
    editedByUser: false,
    createdAt: '2026-11-09T09:00:00Z',
    updatedAt: '2026-11-09T09:00:00Z'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [RecruiterService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(RecruiterService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load recruiters, reminders, generate editable unsent outreach, edit, approve, and mark sent', () => {
    service.loadRecruiters('Sophie').subscribe();
    const listReq = httpMock.expectOne(
      r => r.url === '/api/v1/recruiters' && r.params.get('query') === 'Sophie'
    );
    expect(listReq.request.method).toBe('GET');
    listReq.flush({
      content: [baseRecruiter],
      pageNumber: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
      isFirst: true,
      isLast: true,
      hasNext: false,
      hasPrevious: false
    });
    expect(service.recruiters().length).toBe(1);

    const remindersPayload: FollowUpRemindersResponse = {
      overdueCount: 0,
      dueTodayCount: 1,
      upcomingCount: 0,
      totalPendingCount: 1,
      items: []
    };
    service.loadReminders(false).subscribe();
    const remReq = httpMock.expectOne('/api/v1/recruiters/reminders?includeCompleted=false');
    remReq.flush(remindersPayload);
    expect(service.reminders()?.dueTodayCount).toBe(1);

    service.getRecruiter(12).subscribe();
    httpMock.expectOne('/api/v1/recruiters/12').flush(baseRecruiter);

    const draftResponse: OutreachDraftResponse = {
      interaction: draftInteraction,
      keyTalkingPoints: ['8 yrs Java & Spring Boot'],
      suggestedFollowUpDays: 5,
      warnings: [],
      requiresManualApproval: true,
      autoSent: false
    };
    service
      .generateOutreach(12, {
        channel: 'LINKEDIN',
        messageType: 'LINKEDIN_CONNECTION',
        applicationUuid: 'app-uuid-1'
      })
      .subscribe();
    const genReq = httpMock.expectOne('/api/v1/recruiters/12/outreach/generate');
    expect(genReq.request.method).toBe('POST');
    genReq.flush(draftResponse);

    expect(service.selectedRecruiter()?.interactions?.[0].status).toBe('DRAFT');
    expect(service.selectedRecruiter()?.interactions?.[0].sentAt).toBeNull();

    service
      .updateInteraction(12, 44, {
        action: 'MARK_SENT',
        explicitApprovalConfirmed: true
      })
      .subscribe();
    const putReq = httpMock.expectOne('/api/v1/recruiters/12/interactions/44');
    expect(putReq.request.method).toBe('PUT');
    expect(putReq.request.body.explicitApprovalConfirmed).toBeTrue();
    putReq.flush({ ...draftInteraction, status: 'SENT', sentAt: '2026-11-09T10:00:00Z' });

    expect(service.selectedRecruiter()?.interactions?.[0].status).toBe('SENT');
  });
});

