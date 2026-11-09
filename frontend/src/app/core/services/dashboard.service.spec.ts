import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DashboardSummaryResponse } from '../models/dashboard.models';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let service: DashboardService;
  let httpMock: HttpTestingController;

  const mockSummary: DashboardSummaryResponse = {
    totalJobsDiscovered: 142,
    newJobsToday: 12,
    highMatchJobsCount: 9,
    sponsorshipOpportunitiesCount: 54,
    verifiedSponsorsCount: 18,
    savedJobsCount: 4,
    interestedJobsCount: 6,
    activeApplicationsCount: 3,
    unreadNotificationsCount: 2,
    readiness: {
      profileExists: true,
      profileCompletenessPercent: 80,
      skillsCount: 5,
      experiencesCount: 2,
      educationsCount: 1,
      resumesCount: 1,
      masterResumeApproved: true,
      preferencesConfigured: true,
      autopilotEnabled: true,
      minMatchScore: 75,
      preferredCountries: ['Germany', 'Netherlands'],
      preferredJobTitles: ['Senior Java Engineer']
    },
    pipelineBreakdown: [
      { status: 'SAVED', label: 'Saved', count: 1 },
      { status: 'APPLIED', label: 'Applied', count: 2 }
    ],
    recentActivity: [
      {
        type: 'APPLICATION',
        title: 'Senior Java Engineer · Adyen',
        subtitle: 'Pipeline stage: Applied',
        timestamp: '2026-11-09T08:00:00Z',
        targetRoute: '/applications'
      }
    ],
    generatedAt: '2026-11-09T08:30:00Z'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(DashboardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load executive dashboard summary via GET /api/v1/dashboard/summary and update signals', () => {
    let received: DashboardSummaryResponse | undefined;
    service.loadSummary().subscribe(res => (received = res));

    expect(service.isLoading()).toBeTrue();
    const req = httpMock.expectOne('/api/v1/dashboard/summary');
    expect(req.request.method).toBe('GET');

    req.flush(mockSummary);

    expect(service.isLoading()).toBeFalse();
    expect(received).toEqual(mockSummary);
    expect(service.summary()).toEqual(mockSummary);
  });

  it('should reset isLoading to false when request fails', () => {
    let errored = false;
    service.loadSummary().subscribe({
      error: () => (errored = true)
    });

    const req = httpMock.expectOne('/api/v1/dashboard/summary');
    req.flush({ message: 'Server unavailable' }, { status: 503, statusText: 'Service Unavailable' });

    expect(errored).toBeTrue();
    expect(service.isLoading()).toBeFalse();
    expect(service.summary()).toBeNull();
  });
});

