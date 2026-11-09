import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UserProfileResponse } from '../models/profile.models';
import { AuthService } from './auth.service';
import { ProfileService } from './profile.service';

describe('ProfileService', () => {
  let service: ProfileService;
  let authService: AuthService;
  let httpMock: HttpTestingController;

  const mockProfile: UserProfileResponse = {
    uuid: 'user-uuid-1',
    firstName: 'Alex',
    lastName: 'Mueller',
    fullName: 'Alex Mueller',
    email: 'alex@eurojobcopilot.com',
    currentDesignation: 'Senior Java Engineer',
    yearsOfExperience: 6.5,
    currentCountry: 'India',
    currentCity: 'Bengaluru',
    expectedSalaryMin: 85000,
    expectedSalaryCurrency: 'EUR',
    noticePeriodDays: 30,
    workAuthorizationStatus: 'NEEDS_SPONSORSHIP',
    relocationPreference: 'WILLING_TO_RELOCATE',
    linkedinUrl: 'https://www.linkedin.com/in/alex-mueller',
    githubUrl: 'https://github.com/alex-mueller',
    portfolioUrl: 'https://alexmueller.dev',
    bio: 'Backend systems engineer.',
    preferredJobTitles: ['Senior Java Engineer'],
    preferredCountries: ['Germany', 'Netherlands'],
    skills: [
      {
        id: 1,
        skillName: 'Java',
        normalizedSkill: 'java',
        yearsExperience: 6,
        proficiencyLevel: 'EXPERT',
        primary: true
      }
    ],
    educations: [
      {
        id: 2,
        institution: 'TU Munich',
        degree: 'M.Sc.',
        fieldOfStudy: 'Computer Science',
        startDate: '2016-10-01',
        endDate: '2018-09-30',
        gradeOrGpa: '1.4',
        country: 'Germany'
      }
    ],
    experiences: [
      {
        id: 3,
        companyName: 'Adyen',
        designation: 'Software Engineer',
        location: 'Amsterdam',
        startDate: '2020-01-01',
        endDate: null,
        current: true,
        description: 'Built payment gateways.',
        skillsUsed: ['Java', 'PostgreSQL']
      }
    ],
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-02T10:00:00Z'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(ProfileService);
    authService = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load full profile and compute 100% completeness', () => {
    let loaded: UserProfileResponse | null | undefined;
    service.loadProfile().subscribe(res => (loaded = res));

    const req = httpMock.expectOne('/api/v1/profile');
    expect(req.request.method).toBe('GET');
    req.flush(mockProfile);

    expect(loaded).toEqual(mockProfile);
    expect(service.profileExists()).toBeTrue();
    expect(service.skills().length).toBe(1);
    expect(service.educations().length).toBe(1);
    expect(service.experiences().length).toBe(1);
    expect(service.completenessPercent()).toBe(100);
  });

  it('should gracefully handle 404 on new user onboarding and fetch sub-resources cleanly', () => {
    authService
      .login({ email: 'newuser@eurojobcopilot.com', password: 'Password1!' })
      .subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush({
      accessToken: 'jwt-new',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: {
        uuid: 'u-new',
        email: 'newuser@eurojobcopilot.com',
        firstName: 'Priya',
        lastName: 'Sharma',
        role: 'ROLE_USER',
        accountStatus: 'ACTIVE',
        emailVerified: true
      }
    });

    let loaded: UserProfileResponse | null | undefined;
    service.loadProfile().subscribe(res => (loaded = res));

    httpMock
      .expectOne('/api/v1/profile')
      .flush({ message: 'User profile has not been created yet' }, { status: 404, statusText: 'Not Found' });

    httpMock.expectOne('/api/v1/profile/skills').flush([]);
    httpMock.expectOne('/api/v1/profile/education').flush([]);
    httpMock.expectOne('/api/v1/profile/experience').flush([]);

    expect(loaded).toBeNull();
    expect(service.profileExists()).toBeFalse();
    expect(service.profile()?.firstName).toBe('Priya');
    expect(service.profile()?.email).toBe('newuser@eurojobcopilot.com');
    expect(service.completenessPercent()).toBe(0);
  });

  it('should upsert profile via PUT and sync updated name into AuthService', () => {
    authService.login({ email: 'alex@eurojobcopilot.com', password: 'Password1!' }).subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush({
      accessToken: 'jwt-save',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: {
        uuid: 'user-uuid-1',
        email: 'alex@eurojobcopilot.com',
        firstName: 'Alex',
        lastName: 'Mueller',
        role: 'ROLE_USER',
        accountStatus: 'ACTIVE',
        emailVerified: true
      }
    });

    service
      .saveProfile({
        firstName: 'Alexander',
        lastName: 'Mueller',
        currentDesignation: 'Staff Backend Engineer',
        currentCountry: 'Germany'
      })
      .subscribe();

    const putReq = httpMock.expectOne('/api/v1/profile');
    expect(putReq.request.method).toBe('PUT');
    putReq.flush({
      ...mockProfile,
      firstName: 'Alexander',
      fullName: 'Alexander Mueller'
    });

    expect(service.profileExists()).toBeTrue();
    expect(authService.userFullName()).toBe('Alexander Mueller');
  });

  it('should manage skills, education, and work experience CRUD signals cleanly', () => {
    service
      .addSkill({
        skillName: 'Angular',
        yearsExperience: 4,
        proficiencyLevel: 'ADVANCED',
        primary: true
      })
      .subscribe();
    httpMock.expectOne('/api/v1/profile/skills').flush({
      id: 10,
      skillName: 'Angular',
      normalizedSkill: 'angular',
      yearsExperience: 4,
      proficiencyLevel: 'ADVANCED',
      primary: true
    });
    expect(service.skills().length).toBe(1);

    service
      .updateSkill(10, {
        skillName: 'Angular 20',
        yearsExperience: 5,
        proficiencyLevel: 'EXPERT',
        primary: true
      })
      .subscribe();
    const updateSkillReq = httpMock.expectOne('/api/v1/profile/skills/10');
    expect(updateSkillReq.request.method).toBe('PUT');
    updateSkillReq.flush({
      id: 10,
      skillName: 'Angular 20',
      normalizedSkill: 'angular 20',
      yearsExperience: 5,
      proficiencyLevel: 'EXPERT',
      primary: true
    });
    expect(service.skills()[0].skillName).toBe('Angular 20');

    service.deleteSkill(10).subscribe();
    const delSkillReq = httpMock.expectOne('/api/v1/profile/skills/10');
    expect(delSkillReq.request.method).toBe('DELETE');
    delSkillReq.flush(null);
    expect(service.skills().length).toBe(0);

    service
      .addEducation({
        institution: 'RWTH Aachen',
        degree: 'B.Sc.',
        fieldOfStudy: 'CS',
        startDate: '2014-10-01',
        endDate: '2017-09-30',
        gradeOrGpa: '1.5',
        country: 'Germany'
      })
      .subscribe();
    httpMock.expectOne('/api/v1/profile/education').flush({
      id: 20,
      institution: 'RWTH Aachen',
      degree: 'B.Sc.',
      fieldOfStudy: 'CS',
      startDate: '2014-10-01',
      endDate: '2017-09-30',
      gradeOrGpa: '1.5',
      country: 'Germany'
    });
    expect(service.educations().length).toBe(1);

    service.deleteEducation(20).subscribe();
    httpMock.expectOne('/api/v1/profile/education/20').flush(null);
    expect(service.educations().length).toBe(0);

    service
      .addWorkExperience({
        companyName: 'Booking.com',
        designation: 'Backend Engineer',
        location: 'Amsterdam',
        startDate: '2021-01-01',
        endDate: null,
        current: true,
        description: 'Search APIs',
        skillsUsed: ['Java']
      })
      .subscribe();
    httpMock.expectOne('/api/v1/profile/experience').flush({
      id: 30,
      companyName: 'Booking.com',
      designation: 'Backend Engineer',
      location: 'Amsterdam',
      startDate: '2021-01-01',
      endDate: null,
      current: true,
      description: 'Search APIs',
      skillsUsed: ['Java']
    });
    expect(service.experiences().length).toBe(1);

    service.deleteWorkExperience(30).subscribe();
    httpMock.expectOne('/api/v1/profile/experience/30').flush(null);
    expect(service.experiences().length).toBe(0);
  });
});

