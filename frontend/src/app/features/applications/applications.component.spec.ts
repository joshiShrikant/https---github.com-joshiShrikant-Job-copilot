import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { JobApplicationDetailResponse } from '../../core/models/application.models';
import { CoverLetterSummaryDto } from '../../core/models/cover-letter.models';
import { JobSummaryPageResponse } from '../../core/models/job.models';
import { TailoredResumeSummaryDto } from '../../core/models/tailored-resume.models';
import { ApplicationsComponent } from './applications.component';

describe('ApplicationsComponent', () => {
  let fixture: ComponentFixture<ApplicationsComponent>;
  let httpMock: HttpTestingController;
  let queryParamSubject: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  const appOne: JobApplicationDetailResponse = {
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
    jobOriginalUrl: 'https://careers.adyen.com/101',
    jobApplicationUrl: 'https://careers.adyen.com/101/apply',
    matchPercentage: 91,
    status: 'SAVED',
    appliedDate: null,
    tailoredResume: {
      uuid: 'cv-uuid-1',
      title: 'Adyen Staff Java CV',
      versionNumber: 2,
      subtitle: 'MODERN',
      updatedAt: '2026-11-08T09:00:00Z'
    },
    coverLetter: {
      uuid: 'cl-uuid-1',
      title: 'Adyen Cover Letter',
      versionNumber: 1,
      subtitle: 'PROFESSIONAL',
      updatedAt: '2026-11-08T09:30:00Z'
    },
    currentSalaryOffered: null,
    offeredCurrency: 'EUR',
    nextFollowUpDate: '2026-11-20',
    followUpCompleted: false,
    overdueFollowUp: false,
    nextInterviewDate: '2026-11-18T14:00:00Z',
    notes: 'Prepared SEPA Instant examples.',
    version: 1,
    recruiterInteractionsCount: 1,
    statusHistory: [
      {
        id: 1,
        previousStatus: null,
        newStatus: 'SAVED',
        transitionNotes: 'Added to tracker',
        transitionedAt: '2026-11-07T09:00:00Z'
      }
    ],
    createdAt: '2026-11-07T09:00:00Z',
    updatedAt: '2026-11-08T09:30:00Z'
  };

  const jobsPage: JobSummaryPageResponse = {
    content: [
      {
        id: 101,
        title: 'Staff Java Clearing Architect',
        companyName: 'Adyen N.V.',
        location: 'Amsterdam, Netherlands',
        city: 'Amsterdam',
        country: 'Netherlands',
        workArrangement: 'HYBRID',
        salaryMin: 95000,
        salaryMax: 125000,
        salaryCurrency: 'EUR',
        sponsorStatus: 'VERIFIED_SPONSOR',
        matchPercentage: 91,
        sourceProvider: 'ARBEITNOW',
        postedAt: '2026-11-08T10:00:00Z',
        isSaved: true,
        isInterested: true
      },
      {
        id: 202,
        title: 'Principal Cloud Platform Engineer',
        companyName: 'SAP SE',
        location: 'Walldorf, Germany',
        city: 'Walldorf',
        country: 'Germany',
        workArrangement: 'HYBRID',
        salaryMin: 100000,
        salaryMax: 130000,
        salaryCurrency: 'EUR',
        sponsorStatus: 'RELOCATION_OFFERED',
        matchPercentage: 87,
        sourceProvider: 'ARBEITNOW',
        postedAt: '2026-11-08T11:00:00Z',
        isSaved: false,
        isInterested: false
      }
    ],
    pageNumber: 0,
    pageSize: 50,
    totalElements: 2,
    totalPages: 1,
    isFirst: true,
    isLast: true,
    hasNext: false,
    hasPrevious: false
  };

  const tailoredResumes: TailoredResumeSummaryDto[] = [
    {
      uuid: 'cv-uuid-1',
      masterResumeUuid: 'master-1',
      masterResumeTitle: 'Master Backend CV',
      jobId: 101,
      jobTitle: 'Staff Java Clearing Architect',
      companyName: 'Adyen N.V.',
      jobCountry: 'Netherlands',
      title: 'Adyen Staff Java CV',
      templateName: 'MODERN',
      versionNumber: 2,
      matchScore: 91,
      atsKeywordCoverage: 89,
      aiProvider: 'GEMINI',
      aiModelUsed: 'gemini-2.5-flash',
      editedByUser: false,
      hasPdfExport: true,
      hasDocxExport: false,
      generatedAt: '2026-11-08T09:00:00Z',
      createdAt: '2026-11-08T09:00:00Z',
      updatedAt: '2026-11-08T09:00:00Z'
    }
  ];

  const coverLetters: CoverLetterSummaryDto[] = [
    {
      uuid: 'cl-uuid-1',
      jobId: 101,
      jobTitle: 'Staff Java Clearing Architect',
      companyName: 'Adyen N.V.',
      jobCountry: 'Netherlands',
      tailoredResumeUuid: 'cv-uuid-1',
      tailoredResumeTitle: 'Adyen Staff Java CV',
      title: 'Adyen Cover Letter',
      tone: 'PROFESSIONAL',
      versionNumber: 1,
      wordCount: 310,
      aiProvider: 'GEMINI',
      aiModelUsed: 'gemini-2.5-flash',
      editedByUser: false,
      generatedAt: '2026-11-08T09:30:00Z',
      createdAt: '2026-11-08T09:30:00Z',
      updatedAt: '2026-11-08T09:30:00Z'
    }
  ];

  beforeEach(async () => {
    queryParamSubject = new BehaviorSubject(convertToParamMap({}));
    await TestBed.configureTestingModule({
      imports: [ApplicationsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: queryParamSubject.asObservable() }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ApplicationsComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialLoads(apps: JobApplicationDetailResponse[] = [appOne]): void {
    httpMock.expectOne('/api/v1/applications').flush(apps);
    httpMock.expectOne('/api/v1/recruiters/reminders?includeCompleted=false').flush({
      overdueCount: 0,
      dueTodayCount: 0,
      upcomingCount: 1,
      totalPendingCount: 1,
      items: []
    });
    httpMock.expectOne('/api/v1/jobs?page=0&size=50').flush(jobsPage);
    httpMock.expectOne('/api/v1/tailored-documents/resumes').flush(tailoredResumes);
    httpMock.expectOne('/api/v1/tailored-documents/cover-letters').flush(coverLetters);
    fixture.detectChanges();
  }

  it('should render all 10 Kanban columns, transition stage, inspect timeline & offer, switch to Table & Calendar views, and track new application', () => {
    fixture.detectChanges();
    flushInitialLoads();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('[data-kanban-column]').length).toBe(10);
    expect(host.querySelector('[data-kpi-total]')?.textContent?.trim()).toBe('1');
    expect(host.querySelector('[data-kanban-card="11111111-1111-4111-8111-111111111111"]')).toBeTruthy();

    // Quick stage change on Kanban card
    const moveSelect = host.querySelector<HTMLSelectElement>(
      '[data-kanban-move-select="11111111-1111-4111-8111-111111111111"]'
    )!;
    moveSelect.value = 'TECH_INTERVIEW';
    moveSelect.dispatchEvent(new Event('change'));

    const patchReq = httpMock.expectOne(
      '/api/v1/applications/11111111-1111-4111-8111-111111111111/status'
    );
    expect(patchReq.request.method).toBe('PATCH');
    expect(patchReq.request.body.newStatus).toBe('TECH_INTERVIEW');
    const movedApp: JobApplicationDetailResponse = {
      ...appOne,
      status: 'TECH_INTERVIEW',
      version: 2,
      statusHistory: [
        {
          id: 2,
          previousStatus: 'SAVED',
          newStatus: 'TECH_INTERVIEW',
          transitionNotes: 'Moved to Tech Interview',
          transitionedAt: '2026-11-09T11:00:00Z'
        },
        ...appOne.statusHistory
      ]
    };
    patchReq.flush(movedApp);
    httpMock.expectOne('/api/v1/recruiters/reminders?includeCompleted=false').flush({
      overdueCount: 0,
      dueTodayCount: 0,
      upcomingCount: 1,
      totalPendingCount: 1,
      items: []
    });
    fixture.detectChanges();

    expect(
      host.querySelector('[data-kanban-column="TECH_INTERVIEW"] [data-kanban-card="11111111-1111-4111-8111-111111111111"]')
    ).toBeTruthy();

    // Open Detail Drawer & verify timeline + save salary offer
    host
      .querySelector<HTMLButtonElement>('[data-kanban-open="11111111-1111-4111-8111-111111111111"]')
      ?.click();
    fixture.detectChanges();

    expect(host.querySelector('[data-application-drawer]')).toBeTruthy();
    expect(host.querySelectorAll('[data-timeline-item]').length).toBe(2);

    const salaryInput = host.querySelector<HTMLInputElement>('[data-drawer-salary]')!;
    salaryInput.value = '118000';
    salaryInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('[data-drawer-save-btn]')?.click();
    const putReq = httpMock.expectOne('/api/v1/applications/11111111-1111-4111-8111-111111111111');
    expect(putReq.request.method).toBe('PUT');
    expect(putReq.request.body.currentSalaryOffered).toBe(118000);
    putReq.flush({ ...movedApp, currentSalaryOffered: 118000, offeredCurrency: 'EUR', version: 3 });
    httpMock.expectOne('/api/v1/recruiters/reminders?includeCompleted=false').flush({
      overdueCount: 0,
      dueTodayCount: 0,
      upcomingCount: 1,
      totalPendingCount: 1,
      items: []
    });
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('[data-drawer-close]')?.click();
    fixture.detectChanges();
    expect(host.querySelector('[data-application-drawer]')).toBeNull();

    // Switch to Table View
    host.querySelector<HTMLButtonElement>('[data-tab-btn="table"]')?.click();
    fixture.detectChanges();
    expect(host.querySelector('[data-applications-table]')).toBeTruthy();
    expect(host.querySelector('[data-table-row="11111111-1111-4111-8111-111111111111"]')).toBeTruthy();

    // Switch to Calendar View
    host.querySelector<HTMLButtonElement>('[data-tab-btn="calendar"]')?.click();
    fixture.detectChanges();
    expect(host.querySelector('[data-applications-calendar]')).toBeTruthy();

    // Track new application from modal
    host.querySelector<HTMLButtonElement>('[data-apps-track-btn]')?.click();
    fixture.detectChanges();
    expect(host.querySelector('[data-apps-create-modal]')).toBeTruthy();
    host.querySelector<HTMLButtonElement>('[data-create-submit]')?.click();

    const createReq = httpMock.expectOne('/api/v1/applications');
    expect(createReq.request.method).toBe('POST');
    expect(createReq.request.body.jobId).toBe(202);
    createReq.flush({
      ...appOne,
      uuid: '22222222-2222-4222-8222-222222222222',
      jobId: 202,
      jobTitle: 'Principal Cloud Platform Engineer',
      companyName: 'SAP SE',
      status: 'INTERESTED',
      tailoredResume: null,
      coverLetter: null
    });
    httpMock.expectOne('/api/v1/recruiters/reminders?includeCompleted=false').flush({
      overdueCount: 0,
      dueTodayCount: 0,
      upcomingCount: 1,
      totalPendingCount: 1,
      items: []
    });
    fixture.detectChanges();

    expect(host.querySelector('[data-apps-create-modal]')).toBeNull();
    expect(host.querySelector('[data-kpi-total]')?.textContent?.trim()).toBe('2');

    // Open Create modal -> cancel -> open Drawer & delete via ConfirmDialog
    host
      .querySelector<HTMLButtonElement>('[data-kanban-open="22222222-2222-4222-8222-222222222222"]')
      ?.click();
    fixture.detectChanges();
    expect(host.querySelector('[data-application-drawer]')).toBeTruthy();

    host.querySelector<HTMLButtonElement>('[data-drawer-delete-btn]')?.click();
    fixture.detectChanges();
    httpMock.expectNone('/api/v1/applications/22222222-2222-4222-8222-222222222222');

    host.querySelector<HTMLButtonElement>('[data-dialog-action="confirm"]')?.click();
    const delReq = httpMock.expectOne('/api/v1/applications/22222222-2222-4222-8222-222222222222');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush({ success: true, message: 'Deleted' });
    httpMock.expectOne('/api/v1/recruiters/reminders?includeCompleted=false').flush({
      overdueCount: 0,
      dueTodayCount: 0,
      upcomingCount: 1,
      totalPendingCount: 1,
      items: []
    });
    fixture.detectChanges();

    expect(host.querySelector('[data-application-drawer]')).toBeNull();
    expect(host.querySelector('[data-kpi-total]')?.textContent?.trim()).toBe('1');
  });

  it('should pre-open Create modal cleanly when ?jobId points to an untracked job', () => {
    queryParamSubject.next(convertToParamMap({ jobId: '202' }));
    fixture.detectChanges();
    flushInitialLoads();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-apps-create-modal]')).toBeTruthy();
    expect(fixture.componentInstance.createJobId()).toBe(202);
  });

  it('should fetch fallback job detail when ?jobId is absent from initial jobs page', () => {
    queryParamSubject.next(convertToParamMap({ jobId: '999' }));
    fixture.detectChanges();
    flushInitialLoads();

    const detailReq = httpMock.expectOne('/api/v1/jobs/999');
    expect(detailReq.request.method).toBe('GET');
    detailReq.flush({
      id: 999,
      title: 'Principal Distributed Systems Engineer',
      companyName: 'JetBrains',
      companyId: 88,
      location: 'Munich, Germany',
      city: 'Munich',
      country: 'Germany',
      workArrangement: 'HYBRID',
      employmentType: 'FULL_TIME',
      experienceLevel: 'SENIOR',
      salaryMin: 110000,
      salaryMax: 145000,
      salaryCurrency: 'EUR',
      salaryPeriod: 'YEARLY',
      sponsorStatus: 'VERIFIED_SPONSOR',
      matchPercentage: 92,
      sourceProvider: 'ARBEITNOW',
      postedAt: '2026-11-08T12:00:00Z',
      isSaved: false,
      isInterested: false,
      isHidden: false,
      skills: ['Java', 'Kotlin', 'Distributed Systems'],
      descriptionText: 'Core IDE & distributed platform role.',
      requirementsText: '10+ yrs systems engineering.',
      originalUrl: 'https://jetbrains.example/999',
      applicationUrl: 'https://jetbrains.example/999/apply'
    });
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-apps-create-modal]')).toBeTruthy();
    expect(fixture.componentInstance.createJobId()).toBe(999);
    expect(host.querySelector('[data-create-job-select]')?.textContent).toContain('JetBrains');
  });
});
