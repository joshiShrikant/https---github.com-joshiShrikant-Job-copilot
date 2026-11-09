export type WorkAuthorizationStatus =
  | 'EU_CITIZEN'
  | 'PERMANENT_RESIDENT'
  | 'BLUE_CARD_HOLDER'
  | 'NEEDS_SPONSORSHIP'
  | 'OTHER';

export type RelocationPreference =
  | 'WILLING_TO_RELOCATE'
  | 'RELOCATING_SOON'
  | 'ALREADY_IN_EUROPE'
  | 'REMOTE_ONLY'
  | 'NOT_INTERESTED';

export type ProficiencyLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';

export interface SelectOption<T extends string> {
  readonly value: T;
  readonly label: string;
}

export const WORK_AUTHORIZATION_OPTIONS: readonly SelectOption<WorkAuthorizationStatus>[] = [
  { value: 'NEEDS_SPONSORSHIP', label: 'Needs Visa Sponsorship' },
  { value: 'BLUE_CARD_HOLDER', label: 'EU Blue Card Holder' },
  { value: 'PERMANENT_RESIDENT', label: 'EU Permanent Resident' },
  { value: 'EU_CITIZEN', label: 'EU / EEA / Swiss Citizen' },
  { value: 'OTHER', label: 'Other Work Authorization' }
];

export const RELOCATION_PREFERENCE_OPTIONS: readonly SelectOption<RelocationPreference>[] = [
  { value: 'WILLING_TO_RELOCATE', label: 'Willing to Relocate' },
  { value: 'RELOCATING_SOON', label: 'Relocating Soon' },
  { value: 'ALREADY_IN_EUROPE', label: 'Already in Europe' },
  { value: 'REMOTE_ONLY', label: 'Remote Only' },
  { value: 'NOT_INTERESTED', label: 'Not Interested in Relocation' }
];

export const PROFICIENCY_LEVEL_OPTIONS: readonly SelectOption<ProficiencyLevel>[] = [
  { value: 'BEGINNER', label: 'Beginner' },
  { value: 'INTERMEDIATE', label: 'Intermediate' },
  { value: 'ADVANCED', label: 'Advanced' },
  { value: 'EXPERT', label: 'Expert' }
];

export const COMMON_SALARY_CURRENCIES: readonly string[] = [
  'EUR',
  'GBP',
  'CHF',
  'SEK',
  'DKK',
  'PLN',
  'USD'
];

export interface UserSkillDto {
  readonly id: number;
  readonly skillName: string;
  readonly normalizedSkill: string;
  readonly yearsExperience: number;
  readonly proficiencyLevel: ProficiencyLevel;
  readonly primary: boolean;
}

export interface UpsertSkillRequest {
  readonly skillName: string;
  readonly yearsExperience: number;
  readonly proficiencyLevel: ProficiencyLevel;
  readonly primary: boolean;
}

export interface EducationDto {
  readonly id: number;
  readonly institution: string;
  readonly degree: string;
  readonly fieldOfStudy: string;
  readonly startDate: string;
  readonly endDate: string | null;
  readonly gradeOrGpa: string | null;
  readonly country: string;
}

export interface UpsertEducationRequest {
  readonly institution: string;
  readonly degree: string;
  readonly fieldOfStudy: string;
  readonly startDate: string;
  readonly endDate: string | null;
  readonly gradeOrGpa: string | null;
  readonly country: string;
}

export interface WorkExperienceDto {
  readonly id: number;
  readonly companyName: string;
  readonly designation: string;
  readonly location: string | null;
  readonly startDate: string;
  readonly endDate: string | null;
  readonly current: boolean;
  readonly description: string | null;
  readonly skillsUsed: readonly string[];
}

export interface UpsertWorkExperienceRequest {
  readonly companyName: string;
  readonly designation: string;
  readonly location: string | null;
  readonly startDate: string;
  readonly endDate: string | null;
  readonly current: boolean;
  readonly description: string | null;
  readonly skillsUsed: readonly string[];
}

export interface UserProfileResponse {
  readonly uuid: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly fullName: string;
  readonly email: string;
  readonly currentDesignation: string | null;
  readonly yearsOfExperience: number | null;
  readonly currentCountry: string | null;
  readonly currentCity: string | null;
  readonly expectedSalaryMin: number | null;
  readonly expectedSalaryCurrency: string | null;
  readonly noticePeriodDays: number | null;
  readonly workAuthorizationStatus: WorkAuthorizationStatus | null;
  readonly relocationPreference: RelocationPreference | null;
  readonly linkedinUrl: string | null;
  readonly githubUrl: string | null;
  readonly portfolioUrl: string | null;
  readonly bio: string | null;
  readonly preferredJobTitles: readonly string[];
  readonly preferredCountries: readonly string[];
  readonly skills: readonly UserSkillDto[];
  readonly educations: readonly EducationDto[];
  readonly experiences: readonly WorkExperienceDto[];
  readonly createdAt: string | null;
  readonly updatedAt: string | null;
}

export interface UpsertProfileRequest {
  readonly firstName?: string | null;
  readonly lastName?: string | null;
  readonly currentDesignation?: string | null;
  readonly yearsOfExperience?: number | null;
  readonly currentCountry?: string | null;
  readonly currentCity?: string | null;
  readonly expectedSalaryMin?: number | null;
  readonly expectedSalaryCurrency?: string | null;
  readonly noticePeriodDays?: number | null;
  readonly workAuthorizationStatus?: WorkAuthorizationStatus | null;
  readonly relocationPreference?: RelocationPreference | null;
  readonly linkedinUrl?: string | null;
  readonly githubUrl?: string | null;
  readonly portfolioUrl?: string | null;
  readonly bio?: string | null;
  readonly preferredJobTitles?: readonly string[] | null;
  readonly preferredCountries?: readonly string[] | null;
}

