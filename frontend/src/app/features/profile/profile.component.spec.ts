import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UpsertProfileRequest, UserProfileResponse } from '../../core/models/profile.models';
import { AuthService } from '../../core/services/auth.service';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent', () => {
  let httpMock: HttpTestingController;
  let authService: AuthService;

  const sampleProfile: UserProfileResponse = {
    uuid: 'u-1',
    firstName: 'Alex',
    lastName: 'Mueller',
    fullName: 'Alex Mueller',
    email: 'alex@eurojobcopilot.com',
    currentDesignation: 'Senior Backend Engineer',
    yearsOfExperience: 7,
    currentCountry: 'India',
    currentCity: 'Bengaluru',
    expectedSalaryMin: 90000,
    expectedSalaryCurrency: 'EUR',
    noticePeriodDays: 30,
    workAuthorizationStatus: 'NEEDS_SPONSORSHIP',
    relocationPreference: 'WILLING_TO_RELOCATE',
    linkedinUrl: 'https://www.linkedin.com/in/alex-mueller',
    githubUrl: null,
    portfolioUrl: null,
    bio: 'Java & Angular engineer.',
    preferredJobTitles: ['Senior Backend Engineer'],
    preferredCountries: ['Netherlands', 'Germany'],
    skills: [
      {
        id: 1,
        skillName: 'Java',
        normalizedSkill: 'java',
        yearsExperience: 7,
        proficiencyLevel: 'EXPERT',
        primary: true
      }
    ],
    educations: [],
    experiences: [],
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-02T10:00:00Z'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function authenticate(): void {
    authService.login({ email: 'alex@eurojobcopilot.com', password: 'Password1!' }).subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush({
      accessToken: 'jwt-profile-test',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      user: {
        uuid: 'u-1',
        email: 'alex@eurojobcopilot.com',
        firstName: 'Alex',
        lastName: 'Mueller',
        role: 'ROLE_USER',
        accountStatus: 'ACTIVE',
        emailVerified: true
      }
    });
  }

  it('should show onboarding info banner when GET /api/v1/profile returns 404 and save profile with null optional URLs', () => {
    authenticate();
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();

    httpMock
      .expectOne('/api/v1/profile')
      .flush({ message: 'Not created' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne('/api/v1/resumes').flush([]);
    httpMock.expectOne('/api/v1/profile/skills').flush([]);
    httpMock.expectOne('/api/v1/profile/education').flush([]);
    httpMock.expectOne('/api/v1/profile/experience').flush([]);

    fixture.detectChanges();
    TestBed.flushEffects();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.status-banner.info')?.textContent).toContain(
      'Your candidate profile has not been saved yet'
    );
    const submitBtn = host.querySelector<HTMLButtonElement>('form button[type="submit"]');
    expect(submitBtn?.textContent).toContain('Create Profile');

    const designationInput = host.querySelector<HTMLInputElement>('#profile-designation');
    if (designationInput) {
      designationInput.value = 'Staff Java Engineer';
      designationInput.dispatchEvent(new Event('input'));
    }

    submitBtn?.click();
    fixture.detectChanges();

    const putReq = httpMock.expectOne('/api/v1/profile');
    expect(putReq.request.method).toBe('PUT');
    const body = putReq.request.body as UpsertProfileRequest;
    expect(body.firstName).toBe('Alex');
    expect(body.currentDesignation).toBe('Staff Java Engineer');
    expect(body.linkedinUrl).toBeNull();
    expect(body.githubUrl).toBeNull();
    expect(body.portfolioUrl).toBeNull();

    putReq.flush({ ...sampleProfile, currentDesignation: 'Staff Java Engineer' });
    fixture.detectChanges();

    expect(host.querySelector('.status-banner.success')?.textContent).toContain(
      'Your candidate profile has been saved.'
    );
  });

  it('should commit uncommitted titleDraft and comma-separated countryDraft when submitting profile', () => {
    authenticate();
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/profile').flush({
      ...sampleProfile,
      preferredJobTitles: [],
      preferredCountries: []
    });
    httpMock.expectOne('/api/v1/resumes').flush([]);
    fixture.detectChanges();
    TestBed.flushEffects();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const titleInput = host.querySelector<HTMLInputElement>('#profile-target-title-input');
    const countryInput = host.querySelector<HTMLInputElement>('#profile-target-country-input');

    if (titleInput && countryInput) {
      titleInput.value = 'Senior Angular Developer, Frontend Lead';
      titleInput.dispatchEvent(new Event('input'));
      countryInput.value = 'Germany, Netherlands';
      countryInput.dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('app-profile-basics-section form button[type="submit"]')?.click();
    fixture.detectChanges();

    const putReq = httpMock.expectOne('/api/v1/profile');
    expect(putReq.request.method).toBe('PUT');
    const body = putReq.request.body as UpsertProfileRequest;
    expect(body.preferredJobTitles).toEqual(['Senior Angular Developer', 'Frontend Lead']);
    expect(body.preferredCountries).toEqual(['Germany', 'Netherlands']);

    putReq.flush({
      ...sampleProfile,
      preferredJobTitles: ['Senior Angular Developer', 'Frontend Lead'],
      preferredCountries: ['Germany', 'Netherlands']
    });
    fixture.detectChanges();
  });

  it('should switch to Skills and Experience tabs and create entries cleanly', () => {
    authenticate();
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/profile').flush(sampleProfile);
    httpMock.expectOne('/api/v1/resumes').flush([]);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const tabs = Array.from(host.querySelectorAll<HTMLButtonElement>('[role="tab"]'));

    // Switch to Skills tab
    tabs[1].click();
    fixture.detectChanges();
    expect(host.querySelector('app-skills-section')).toBeTruthy();

    host.querySelector<HTMLButtonElement>('app-skills-section .btn-primary')?.click();
    fixture.detectChanges();

    const skillNameInput = host.querySelector<HTMLInputElement>('#skill-name-input');
    if (skillNameInput) {
      skillNameInput.value = 'TypeScript';
      skillNameInput.dispatchEvent(new Event('input'));
    }
    host.querySelector<HTMLButtonElement>('app-skills-section form button[type="submit"]')?.click();

    const addSkillReq = httpMock.expectOne('/api/v1/profile/skills');
    expect(addSkillReq.request.method).toBe('POST');
    addSkillReq.flush({
      id: 99,
      skillName: 'TypeScript',
      normalizedSkill: 'typescript',
      yearsExperience: 2,
      proficiencyLevel: 'INTERMEDIATE',
      primary: false
    });
    fixture.detectChanges();
    expect(host.querySelector('app-skills-section')?.textContent).toContain('TypeScript');

    // Switch to Experience tab
    tabs[2].click();
    fixture.detectChanges();
    expect(host.querySelector('app-experience-section')).toBeTruthy();

    host.querySelector<HTMLButtonElement>('app-experience-section .btn-primary')?.click();
    fixture.detectChanges();

    const currentCheckbox = host.querySelector<HTMLInputElement>(
      'app-experience-section input[type="checkbox"]'
    );
    const endDateInput = host.querySelector<HTMLInputElement>('#exp-end-date');
    expect(endDateInput?.disabled).toBeFalse();

    currentCheckbox?.click();
    fixture.detectChanges();
    expect(endDateInput?.disabled).toBeTrue();

    // Switch to Education tab and verify invalid date range blocks submission
    tabs[3].click();
    fixture.detectChanges();
    host.querySelector<HTMLButtonElement>('app-education-section .btn-primary')?.click();
    fixture.detectChanges();

    const inst = host.querySelector<HTMLInputElement>('#edu-institution');
    const deg = host.querySelector<HTMLInputElement>('#edu-degree');
    const field = host.querySelector<HTMLInputElement>('#edu-field');
    const country = host.querySelector<HTMLInputElement>('#edu-country');
    const startDate = host.querySelector<HTMLInputElement>('#edu-start-date');
    const endDate = host.querySelector<HTMLInputElement>('#edu-end-date');

    if (inst && deg && field && country && startDate && endDate) {
      inst.value = 'TU Berlin';
      inst.dispatchEvent(new Event('input'));
      deg.value = 'M.Sc.';
      deg.dispatchEvent(new Event('input'));
      field.value = 'CS';
      field.dispatchEvent(new Event('input'));
      country.value = 'Germany';
      country.dispatchEvent(new Event('input'));
      startDate.value = '2022-09-01';
      startDate.dispatchEvent(new Event('input'));
      endDate.value = '2021-06-01';
      endDate.dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();

    host
      .querySelector<HTMLButtonElement>('app-education-section form button[type="submit"]')
      ?.click();
    fixture.detectChanges();

    httpMock.expectNone('/api/v1/profile/education');
    expect(host.querySelector('app-education-section')?.textContent).toContain(
      'End date cannot precede the start date.'
    );
  });
});

