import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, forkJoin, map, of, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  EducationDto,
  UpsertEducationRequest,
  UpsertProfileRequest,
  UpsertSkillRequest,
  UpsertWorkExperienceRequest,
  UserProfileResponse,
  UserSkillDto,
  WorkExperienceDto
} from '../models/profile.models';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  private readonly profileBaseUrl = `${environment.apiUrl}/profile`;

  private readonly _profile = signal<UserProfileResponse | null>(null);
  private readonly _profileExists = signal<boolean>(true);
  private readonly _skills = signal<readonly UserSkillDto[]>([]);
  private readonly _educations = signal<readonly EducationDto[]>([]);
  private readonly _experiences = signal<readonly WorkExperienceDto[]>([]);
  private readonly _isLoading = signal<boolean>(false);

  readonly profile = this._profile.asReadonly();
  readonly profileExists = this._profileExists.asReadonly();
  readonly skills = this._skills.asReadonly();
  readonly educations = this._educations.asReadonly();
  readonly experiences = this._experiences.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();

  readonly completenessPercent = computed(() => {
    const p = this._profile();
    let score = 0;
    if (p && p.currentDesignation && p.currentCountry) {
      score += 25;
    }
    if (p && (p.bio || p.linkedinUrl || p.githubUrl)) {
      score += 15;
    }
    if (this._skills().length > 0) {
      score += 20;
    }
    if (this._experiences().length > 0) {
      score += 20;
    }
    if (this._educations().length > 0) {
      score += 20;
    }
    return score;
  });

  loadProfile(): Observable<UserProfileResponse | null> {
    this._isLoading.set(true);

    return this.http.get<UserProfileResponse>(this.profileBaseUrl).pipe(
      tap(response => {
        this._profileExists.set(true);
        this.applyFullProfile(response);
      }),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this._profileExists.set(false);
          this.initializeDefaultProfileFromAuth();
          return forkJoin({
            skills: this.getSkills().pipe(catchError(() => of([] as UserSkillDto[]))),
            educations: this.getEducations().pipe(catchError(() => of([] as EducationDto[]))),
            experiences: this.getWorkExperiences().pipe(
              catchError(() => of([] as WorkExperienceDto[]))
            )
          }).pipe(
            tap(({ skills, educations, experiences }) => {
              this._skills.set(skills);
              this._educations.set(educations);
              this._experiences.set(experiences);
            }),
            map(() => null)
          );
        }
        return throwError(() => error);
      }),
      finalize(() => this._isLoading.set(false))
    );
  }

  saveProfile(request: UpsertProfileRequest): Observable<UserProfileResponse> {
    return this.http.put<UserProfileResponse>(this.profileBaseUrl, request).pipe(
      tap(response => {
        this._profileExists.set(true);
        this.applyFullProfile(response);
        this.authService.syncCurrentUserName(response.firstName, response.lastName);
      })
    );
  }

  getSkills(): Observable<UserSkillDto[]> {
    return this.http
      .get<UserSkillDto[]>(`${this.profileBaseUrl}/skills`)
      .pipe(tap(list => this._skills.set(list)));
  }

  addSkill(request: UpsertSkillRequest): Observable<UserSkillDto> {
    return this.http
      .post<UserSkillDto>(`${this.profileBaseUrl}/skills`, request)
      .pipe(tap(created => this._skills.update(items => [created, ...items])));
  }

  updateSkill(skillId: number, request: UpsertSkillRequest): Observable<UserSkillDto> {
    return this.http
      .put<UserSkillDto>(`${this.profileBaseUrl}/skills/${skillId}`, request)
      .pipe(
        tap(updated =>
          this._skills.update(items => items.map(item => (item.id === skillId ? updated : item)))
        )
      );
  }

  deleteSkill(skillId: number): Observable<void> {
    return this.http
      .delete<void>(`${this.profileBaseUrl}/skills/${skillId}`)
      .pipe(tap(() => this._skills.update(items => items.filter(item => item.id !== skillId))));
  }

  getEducations(): Observable<EducationDto[]> {
    return this.http
      .get<EducationDto[]>(`${this.profileBaseUrl}/education`)
      .pipe(tap(list => this._educations.set(list)));
  }

  addEducation(request: UpsertEducationRequest): Observable<EducationDto> {
    return this.http
      .post<EducationDto>(`${this.profileBaseUrl}/education`, request)
      .pipe(tap(created => this._educations.update(items => [created, ...items])));
  }

  updateEducation(educationId: number, request: UpsertEducationRequest): Observable<EducationDto> {
    return this.http
      .put<EducationDto>(`${this.profileBaseUrl}/education/${educationId}`, request)
      .pipe(
        tap(updated =>
          this._educations.update(items =>
            items.map(item => (item.id === educationId ? updated : item))
          )
        )
      );
  }

  deleteEducation(educationId: number): Observable<void> {
    return this.http
      .delete<void>(`${this.profileBaseUrl}/education/${educationId}`)
      .pipe(
        tap(() => this._educations.update(items => items.filter(item => item.id !== educationId)))
      );
  }

  getWorkExperiences(): Observable<WorkExperienceDto[]> {
    return this.http
      .get<WorkExperienceDto[]>(`${this.profileBaseUrl}/experience`)
      .pipe(tap(list => this._experiences.set(list)));
  }

  addWorkExperience(request: UpsertWorkExperienceRequest): Observable<WorkExperienceDto> {
    return this.http
      .post<WorkExperienceDto>(`${this.profileBaseUrl}/experience`, request)
      .pipe(tap(created => this._experiences.update(items => [created, ...items])));
  }

  updateWorkExperience(
    experienceId: number,
    request: UpsertWorkExperienceRequest
  ): Observable<WorkExperienceDto> {
    return this.http
      .put<WorkExperienceDto>(`${this.profileBaseUrl}/experience/${experienceId}`, request)
      .pipe(
        tap(updated =>
          this._experiences.update(items =>
            items.map(item => (item.id === experienceId ? updated : item))
          )
        )
      );
  }

  deleteWorkExperience(experienceId: number): Observable<void> {
    return this.http
      .delete<void>(`${this.profileBaseUrl}/experience/${experienceId}`)
      .pipe(
        tap(() => this._experiences.update(items => items.filter(item => item.id !== experienceId)))
      );
  }

  private applyFullProfile(response: UserProfileResponse): void {
    this._profile.set(response);
    this._skills.set(response.skills ?? []);
    this._educations.set(response.educations ?? []);
    this._experiences.set(response.experiences ?? []);
  }

  private initializeDefaultProfileFromAuth(): void {
    const authUser = this.authService.currentUser();
    this._profile.set({
      uuid: authUser?.uuid ?? '',
      firstName: authUser?.firstName ?? '',
      lastName: authUser?.lastName ?? '',
      fullName: authUser ? `${authUser.firstName} ${authUser.lastName}`.trim() : '',
      email: authUser?.email ?? '',
      currentDesignation: null,
      yearsOfExperience: 0,
      currentCountry: null,
      currentCity: null,
      expectedSalaryMin: null,
      expectedSalaryCurrency: 'EUR',
      noticePeriodDays: 30,
      workAuthorizationStatus: 'NEEDS_SPONSORSHIP',
      relocationPreference: 'WILLING_TO_RELOCATE',
      linkedinUrl: null,
      githubUrl: null,
      portfolioUrl: null,
      bio: null,
      preferredJobTitles: [],
      preferredCountries: [],
      skills: [],
      educations: [],
      experiences: [],
      createdAt: null,
      updatedAt: null
    });
  }
}

