import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JobApplicationDetailResponse } from '../../../../core/models/application.models';
import { RecruiterResponse } from '../../../../core/models/recruiter.models';
import { RecruiterCrmComponent } from './recruiter-crm.component';

describe('RecruiterCrmComponent', () => {
  let fixture: ComponentFixture<RecruiterCrmComponent>;
  let httpMock: HttpTestingController;

  const sampleApplication: JobApplicationDetailResponse = {
    uuid: '11111111-1111-4111-8111-111111111111',
    jobId: 101,
    jobTitle: 'Staff Java Clearing Architect',
    companyId: 7,
    companyName: 'Adyen N.V.',
    jobCountry: 'Netherlands',
    jobCity: 'Amsterdam',
    jobLocation: 'Amsterdam, Netherlands',
    workArrangement: 'HYBRID',
    employmentType: 'FULL_TIME',
    sponsorStatus: 'VERIFIED_SPONSOR',
    jobSalaryMin: 95000,
    jobSalaryMax: 125000,
    jobSalaryCurrency: 'EUR',
    jobOriginalUrl: null,
    jobApplicationUrl: null,
    matchPercentage: 91,
    status: 'APPLIED',
    appliedDate: '2026-11-08T10:00:00Z',
    tailoredResume: null,
    coverLetter: null,
    currentSalaryOffered: null,
    offeredCurrency: 'EUR',
    nextFollowUpDate: null,
    followUpCompleted: false,
    overdueFollowUp: false,
    nextInterviewDate: null,
    notes: null,
    version: 1,
    recruiterInteractionsCount: 0,
    statusHistory: [],
    createdAt: '2026-11-08T10:00:00Z',
    updatedAt: '2026-11-08T10:00:00Z'
  };

  const sampleRecruiter: RecruiterResponse = {
    id: 12,
    fullName: 'Sophie van Dijk',
    roleTitle: 'Senior Technical Recruiter',
    companyId: 7,
    companyName: 'Adyen N.V.',
    companyVerifiedSponsor: true,
    email: 'sophie@adyen.example',
    linkedinUrl: 'https://www.linkedin.com/in/sophievandijk',
    phone: '+31 20 123 4567',
    notes: 'Leads core backend recruitment.',
    interactionsCount: 0,
    lastInteractionAt: null,
    nextFollowUpReminderDate: null,
    pendingRemindersCount: 0,
    interactions: [],
    createdAt: '2026-11-08T10:00:00Z',
    updatedAt: '2026-11-08T10:00:00Z'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RecruiterCrmComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    fixture = TestBed.createComponent(RecruiterCrmComponent);
    fixture.componentRef.setInput('applications', [sampleApplication]);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should generate editable AI outreach draft as unsent DRAFT, allow inline edits & approval, and require confirmation dialog before MARK_SENT', () => {
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/recruiters?page=0&size=50').flush({
      content: [sampleRecruiter],
      pageNumber: 0,
      pageSize: 50,
      totalElements: 1,
      totalPages: 1,
      isFirst: true,
      isLast: true,
      hasNext: false,
      hasPrevious: false
    });
    httpMock.expectOne('/api/v1/recruiters/reminders?includeCompleted=false').flush({
      overdueCount: 0,
      dueTodayCount: 0,
      upcomingCount: 0,
      totalPendingCount: 0,
      items: []
    });
    httpMock.expectOne('/api/v1/recruiters/12').flush(sampleRecruiter);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-crm-selected-recruiter]')?.textContent).toContain('Sophie van Dijk');

    // Select Interview Thank-You & generate AI draft
    host.querySelector<HTMLButtonElement>('[data-outreach-type="POST_INTERVIEW_THANKS"]')?.click();
    fixture.detectChanges();
    host.querySelector<HTMLButtonElement>('[data-crm-generate-btn]')?.click();

    const genReq = httpMock.expectOne('/api/v1/recruiters/12/outreach/generate');
    expect(genReq.request.method).toBe('POST');
    expect(genReq.request.body.messageType).toBe('POST_INTERVIEW_THANKS');

    const generatedInteraction = {
      id: 99,
      recruiterId: 12,
      recruiterName: 'Sophie van Dijk',
      companyName: 'Adyen N.V.',
      applicationUuid: '11111111-1111-4111-8111-111111111111',
      jobId: 101,
      jobTitle: 'Staff Java Clearing Architect',
      channel: 'EMAIL' as const,
      messageType: 'POST_INTERVIEW_THANKS' as const,
      status: 'DRAFT' as const,
      subjectLine: 'Thank you — Staff Java Clearing Architect',
      messageBody: 'Hi Sophie, thank you for discussing SEPA Instant clearing with me.',
      sentAt: null,
      approvedAt: null,
      followUpReminderDate: '2026-11-18',
      reminderCompleted: false,
      overdueReminder: false,
      aiGenerated: true,
      aiProvider: 'GEMINI',
      aiModelUsed: 'gemini-2.5-flash',
      editedByUser: false,
      createdAt: '2026-11-09T10:00:00Z',
      updatedAt: '2026-11-09T10:00:00Z'
    };

    genReq.flush({
      interaction: generatedInteraction,
      keyTalkingPoints: ['SEPA Instant clearing throughput'],
      suggestedFollowUpDays: 5,
      warnings: [],
      requiresManualApproval: true,
      autoSent: false
    });
    httpMock.expectOne('/api/v1/recruiters/reminders?includeCompleted=false').flush({
      overdueCount: 0,
      dueTodayCount: 0,
      upcomingCount: 1,
      totalPendingCount: 1,
      items: []
    });
    fixture.detectChanges();

    expect(host.querySelector('[data-crm-draft-editor]')).toBeTruthy();
    expect(host.querySelector('[data-crm-draft-editor]')?.textContent).toContain('Editable Draft');

    // Edit draft & Save Edits (remains DRAFT, sentAt still null)
    const bodyArea = host.querySelector<HTMLTextAreaElement>('[data-crm-draft-body]')!;
    bodyArea.value = 'Hi Sophie, edited personal thank-you note after our systems discussion.';
    bodyArea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('[data-crm-save-draft-btn]')?.click();
    const saveReq = httpMock.expectOne('/api/v1/recruiters/12/interactions/99');
    expect(saveReq.request.method).toBe('PUT');
    expect(saveReq.request.body.action).toBe('SAVE_DRAFT');
    expect(saveReq.request.body.explicitApprovalConfirmed).toBeFalse();
    saveReq.flush({
      ...generatedInteraction,
      messageBody: bodyArea.value,
      editedByUser: true,
      status: 'DRAFT',
      sentAt: null
    });
    httpMock.expectOne('/api/v1/recruiters/reminders?includeCompleted=false').flush({
      overdueCount: 0,
      dueTodayCount: 0,
      upcomingCount: 1,
      totalPendingCount: 1,
      items: []
    });
    fixture.detectChanges();

    // Clicking Confirm & Log as Sent MUST open ConfirmDialog first (no PUT yet!)
    host.querySelector<HTMLButtonElement>('[data-crm-mark-sent-btn]')?.click();
    fixture.detectChanges();
    httpMock.expectNone('/api/v1/recruiters/12/interactions/99');
    expect(host.textContent).toContain('Confirm manual outreach dispatch?');

    // Confirm inside dialog -> triggers PUT MARK_SENT with explicitApprovalConfirmed: true
    host.querySelector<HTMLButtonElement>('[data-dialog-action="confirm"]')?.click();
    const sendReq = httpMock.expectOne('/api/v1/recruiters/12/interactions/99');
    expect(sendReq.request.body.action).toBe('MARK_SENT');
    expect(sendReq.request.body.explicitApprovalConfirmed).toBeTrue();
    sendReq.flush({
      ...generatedInteraction,
      messageBody: bodyArea.value,
      editedByUser: true,
      status: 'SENT',
      approvedAt: '2026-11-09T10:15:00Z',
      sentAt: '2026-11-09T10:15:00Z'
    });
    httpMock.expectOne('/api/v1/recruiters/reminders?includeCompleted=false').flush({
      overdueCount: 0,
      dueTodayCount: 0,
      upcomingCount: 1,
      totalPendingCount: 1,
      items: []
    });
    fixture.detectChanges();

    expect(host.querySelector('[data-crm-draft-editor]')?.textContent).toContain('Logged as Sent');
  });
});
