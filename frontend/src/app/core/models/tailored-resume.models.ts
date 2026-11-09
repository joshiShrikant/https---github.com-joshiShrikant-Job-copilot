export type ResumeTemplate = 'MODERN' | 'MINIMAL_ATS' | 'EUROPASS' | 'CLASSIC';

export type ResumeExportFormat = 'pdf' | 'docx';

export interface TailoredResumeExportFile {
  readonly blob: Blob;
  readonly fileName: string;
  readonly format: ResumeExportFormat;
}

export interface ResumeTemplateOption {
  readonly value: ResumeTemplate;
  readonly label: string;
  readonly subtitle: string;
  readonly description: string;
}

export const RESUME_TEMPLATE_OPTIONS: readonly ResumeTemplateOption[] = [
  {
    value: 'MODERN',
    label: 'Modern Clean',
    subtitle: 'Recommended for tech roles',
    description: 'Balanced contemporary structure highlighting core technical stack and measurable impact.'
  },
  {
    value: 'MINIMAL_ATS',
    label: 'Minimal ATS',
    subtitle: 'Maximum parser clarity',
    description: 'Single-column high-legibility layout engineered for automated ATS keyword parsing.'
  },
  {
    value: 'EUROPASS',
    label: 'Europass',
    subtitle: 'EU institutional standard',
    description: 'Structured European convention foregrounding language proficiency, mobility, and credentials.'
  },
  {
    value: 'CLASSIC',
    label: 'Classic Executive',
    subtitle: 'Traditional hierarchy',
    description: 'Formal typographic hierarchy suited for staff, principal, and engineering leadership roles.'
  }
];

export interface TailoredResumeHeaderDto {
  fullName: string | null;
  headline: string | null;
  email: string | null;
  location: string | null;
  country: string | null;
  yearsOfExperience: number | null;
  workAuthorizationSummary: string | null;
  linkedinUrl: string | null;
  githubUrl: string | null;
  portfolioUrl: string | null;
}

export interface TailoredResumeExperienceDto {
  companyName: string;
  designation: string;
  location: string | null;
  startDate: string | null;
  endDate: string | null;
  current: boolean;
  originalBullets: string[];
  tailoredBullets: string[];
  matchedKeywords: string[];
  relevanceScore: number | null;
}

export interface TailoredResumeProjectDto {
  name: string;
  description: string | null;
  tailoredBullets: string[];
  technologies: string[];
  url: string | null;
}

export interface TailoredResumeEducationDto {
  institution: string;
  degree: string;
  fieldOfStudy: string;
  startDate: string | null;
  endDate: string | null;
  gradeOrGpa: string | null;
  country: string | null;
}

export interface TailoredResumeDataDto {
  header: TailoredResumeHeaderDto | null;
  tailoredSummary: string | null;
  highlightedSkills: string[];
  additionalSkills: string[];
  matchedKeywords: string[];
  missingKeywords: string[];
  tailoringRationale: string | null;
  optimizedExperiences: TailoredResumeExperienceDto[];
  projects: TailoredResumeProjectDto[];
  educations: TailoredResumeEducationDto[];
  languages: string[];
  certifications: string[];
  groundingWarnings: string[];
}

export interface GenerateTailoredResumeRequest {
  readonly jobId: number;
  readonly masterResumeUuid?: string | null;
  readonly templateName?: ResumeTemplate;
  readonly tailoringFocus?: string | null;
  readonly forceRefresh?: boolean;
}

export interface UpdateTailoredResumeRequest {
  readonly title?: string | null;
  readonly templateName?: ResumeTemplate | null;
  readonly tailoredSummary?: string | null;
  readonly highlightedSkills?: readonly string[] | null;
  readonly additionalSkills?: readonly string[] | null;
  readonly optimizedExperiences?: readonly TailoredResumeExperienceDto[] | null;
  readonly projects?: readonly TailoredResumeProjectDto[] | null;
  readonly educations?: readonly TailoredResumeEducationDto[] | null;
  readonly languages?: readonly string[] | null;
  readonly certifications?: readonly string[] | null;
}

export interface TailoredResumeSummaryDto {
  readonly uuid: string;
  readonly masterResumeUuid: string | null;
  readonly masterResumeTitle: string | null;
  readonly jobId: number;
  readonly jobTitle: string;
  readonly companyName: string;
  readonly jobCountry: string | null;
  readonly title: string;
  readonly versionNumber: number;
  readonly templateName: ResumeTemplate;
  readonly matchScore: number | null;
  readonly atsKeywordCoverage: number | null;
  readonly aiProvider: string | null;
  readonly aiModelUsed: string | null;
  readonly editedByUser: boolean;
  readonly hasPdfExport: boolean;
  readonly hasDocxExport: boolean;
  readonly generatedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface TailoredResumeResponse {
  readonly uuid: string;
  readonly masterResumeUuid: string | null;
  readonly masterResumeTitle: string | null;
  readonly jobId: number;
  readonly jobTitle: string;
  readonly companyName: string;
  readonly jobCountry: string | null;
  readonly title: string;
  readonly versionNumber: number;
  readonly templateName: ResumeTemplate;
  readonly tailoringFocus: string | null;
  readonly matchScore: number | null;
  readonly atsKeywordCoverage: number | null;
  readonly tailoredSummary: string | null;
  readonly optimizedExperiences: readonly TailoredResumeExperienceDto[];
  readonly highlightedSkills: readonly string[];
  readonly missingKeywords: readonly string[];
  readonly resumeData: TailoredResumeDataDto | null;
  readonly aiProvider: string | null;
  readonly aiModelUsed: string | null;
  readonly editedByUser: boolean;
  readonly hasPdfExport: boolean;
  readonly hasDocxExport: boolean;
  readonly generatedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export function formatResumeTemplateLabel(template: ResumeTemplate | string | null | undefined): string {
  switch (template) {
    case 'MODERN':
      return 'Modern Clean';
    case 'MINIMAL_ATS':
      return 'Minimal ATS';
    case 'EUROPASS':
      return 'Europass';
    case 'CLASSIC':
      return 'Classic Executive';
    default:
      return template ? String(template) : 'Modern Clean';
  }
}

