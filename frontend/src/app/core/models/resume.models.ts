import {
  ProficiencyLevel,
  RelocationPreference,
  WorkAuthorizationStatus
} from './profile.models';

export type ResumeType = 'MASTER' | 'TAILORED' | string;
export type StorageProvider = 'LOCAL' | 'S3' | string;
export type ResumeParseStatus = 'NOT_PARSED' | 'PARSED' | 'APPROVED' | string;
export type ProfileSyncMode = 'MERGE' | 'REPLACE';

export interface ParsedResumeProfileDto {
  fullName: string | null;
  currentDesignation: string | null;
  yearsOfExperience: number | null;
  currentCountry: string | null;
  currentCity: string | null;
  linkedinUrl: string | null;
  githubUrl: string | null;
  portfolioUrl: string | null;
  bio: string | null;
  workAuthorizationStatus: WorkAuthorizationStatus | null;
  relocationPreference: RelocationPreference | null;
}

export interface ParsedResumeSkillDto {
  skillName: string;
  yearsExperience: number | null;
  proficiencyLevel: ProficiencyLevel | null;
  primary: boolean;
}

export interface ParsedResumeExperienceDto {
  companyName: string;
  designation: string;
  location: string | null;
  startDate: string | null;
  endDate: string | null;
  current: boolean;
  description: string | null;
  skillsUsed: string[];
}

export interface ParsedResumeEducationDto {
  institution: string;
  degree: string;
  fieldOfStudy: string;
  startDate: string | null;
  endDate: string | null;
  gradeOrGpa: string | null;
  country: string | null;
}

export interface ParsedResumeProjectDto {
  name: string;
  description: string | null;
  technologies: string[];
  url: string | null;
}

export interface ParsedResumeJsonDto {
  profile: ParsedResumeProfileDto | null;
  summary: string | null;
  skills: ParsedResumeSkillDto[];
  experiences: ParsedResumeExperienceDto[];
  educations: ParsedResumeEducationDto[];
  projects: ParsedResumeProjectDto[];
  languages: string[];
  certifications: string[];
  warnings?: string[];
}

export interface MasterResumeSyncSummaryDto {
  readonly syncMode: ProfileSyncMode;
  readonly profileCreated: boolean;
  readonly profileUpdated: boolean;
  readonly skillsAdded: number;
  readonly skillsUpdated: number;
  readonly experiencesAdded: number;
  readonly experiencesUpdated: number;
  readonly educationsAdded: number;
  readonly educationsUpdated: number;
  readonly syncedAt: string;
}

export interface ApproveMasterResumeRequest {
  readonly resumeUuid: string;
  readonly approvedParsedData: ParsedResumeJsonDto;
  readonly syncMode: ProfileSyncMode;
}

export interface ResumeParseResponse {
  readonly resumeUuid: string;
  readonly title: string;
  readonly fileName: string;
  readonly mimeType: string;
  readonly versionNumber: number;
  readonly isMasterApproved: boolean;
  readonly parseStatus: ResumeParseStatus;
  readonly parsedAt: string | null;
  readonly approvedAt: string | null;
  readonly aiProvider: string | null;
  readonly aiModelUsed: string | null;
  readonly extractedCharCount: number | null;
  readonly parsedData: ParsedResumeJsonDto | null;
  readonly warnings: readonly string[];
}

export interface ResumeResponse {
  readonly uuid: string;
  readonly title: string;
  readonly fileName: string;
  readonly fileSizeBytes: number;
  readonly mimeType: string;
  readonly resumeType: ResumeType;
  readonly storageProvider: StorageProvider;
  readonly versionNumber: number;
  readonly isMasterApproved: boolean;
  readonly parseStatus?: ResumeParseStatus;
  readonly parsedAt?: string | null;
  readonly approvedAt?: string | null;
  readonly aiProvider?: string | null;
  readonly aiModelUsed?: string | null;
  readonly hasParsedContent?: boolean;
  readonly profileSyncSummary?: MasterResumeSyncSummaryDto | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export const MAX_RESUME_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

export const ALLOWED_RESUME_EXTENSIONS: readonly string[] = ['.pdf', '.docx'];

export const ALLOWED_RESUME_MIME_TYPES: readonly string[] = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
];
