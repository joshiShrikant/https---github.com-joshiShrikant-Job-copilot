import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { JobApplicationDetailResponse } from '../models/application.models';
import { ApplicationService } from './application.service';

describe('ApplicationService', () => {
  let service: ApplicationService;
  let httpMock: HttpTestingController;

  const sampleApp: JobApplicationDetailResponse = {
    uuid: 'app-uuid-1',
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
    status: 'APPLIED',
    appliedDate: '2026-11-08T10:00:00Z',
    tailoredResume: {
      uuid: 'cv-uuid-1',
      title: 'Adyen Staff Java CV',
      versionNumber: 2,
      subtitle: 'MODERN',
      updatedAt: '2026-11-08T09:00:00Z'
    },
    coverLetter: null,
    currentSalaryOffered: null,
    offeredCurrency: 'EUR',
    nextFollowUpDate: '2026-11-18',
    followUpCompleted: false,
    overdueFollowUp: false,
    nextInterviewDate: null,
    notes: 'Referred by engineering manager.',
    version: 1,
    recruiterInteractionsCount: 1,
    statusHistory: [
      {
        id: 1,
        previousStatus: 'SAVED',
        newStatus: 'APPLIED',
        transitionNotes: 'Submitted application via referral link',
        transitionedAt: '2026-11-08T10:00:00Z'
      }
    ],
    createdAt: '2026-11-07T09:00:00Z',
    updatedAt: '2026-11-08T10:00:00Z'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ApplicationService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ApplicationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load filtered applications and compute pipeline stats', () => {
    service
      .loadApplications({
        status: 'APPLIED',
        query: 'Adyen',
        dueFollowUpOnly: true,
        hasInterviewOnly: false
      })
      .subscribe();

    const req = httpMock.expectOne(
      r =>
        r.url === '/api/v1/applications' &&
        r.params.get('status') === 'APPLIED' &&
        r.params.get('query') === 'Adyen' &&
        r.params.get('dueFollowUpOnly') === 'true'
    );
    expect(req.request.method).toBe('GET');
    req.flush([sampleApp]);

    expect(service.applications().length).toBe(1);
    expect(service.pipelineStats().totalCount).toBe(1);
    expect(service.pipelineStats().pendingFollowUps).toBe(1);
  });

  it('should create, transition stage, update offer, and delete applications cleanly', () => {
    service.createApplication({ jobId: 101, status: 'APPLIED' }).subscribe();
    const createReq = httpMock.expectOne('/api/v1/applications');
    expect(createReq.request.method).toBe('POST');
    createReq.flush(sampleApp);

    expect(service.selectedApplication()?.uuid).toBe('app-uuid-1');

    service
      .updateApplicationStatus('app-uuid-1', {
        newStatus: 'TECH_INTERVIEW',
        transitionNotes: 'Passed screening'
      })
      .subscribe();
    const patchReq = httpMock.expectOne('/api/v1/applications/app-uuid-1/status');
    expect(patchReq.request.method).toBe('PATCH');
    patchReq.flush({ ...sampleApp, status: 'TECH_INTERVIEW', version: 2 });

    expect(service.applications()[0].status).toBe('TECH_INTERVIEW');

    service
      .updateApplication('app-uuid-1', {
        currentSalaryOffered: 118000,
        offeredCurrency: 'EUR',
        followUpCompleted: true,
        expectedVersion: 2
      })
      .subscribe();
    const putReq = httpMock.expectOne('/api/v1/applications/app-uuid-1');
    expect(putReq.request.method).toBe('PUT');
    putReq.flush({
      ...sampleApp,
      status: 'OFFER',
      currentSalaryOffered: 118000,
      followUpCompleted: true,
      version: 3
    });

    expect(service.pipelineStats().offersCount).toBe(1);

    service.deleteApplication('app-uuid-1').subscribe();
    const delReq = httpMock.expectOne('/api/v1/applications/app-uuid-1');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush({ success: true, message: 'Deleted' });

    expect(service.applications().length).toBe(0);
    expect(service.selectedUuid()).toBeNull();
  });
});

