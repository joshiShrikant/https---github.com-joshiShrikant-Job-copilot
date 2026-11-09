export type CoverLetterTone =
  | 'PROFESSIONAL'
  | 'CONFIDENT'
  | 'ACADEMIC'
  | 'ENTHUSIASTIC'
  | 'CONCISE';

export type CoverLetterExportFormat = 'pdf' | 'docx' | 'txt';

export interface CoverLetterToneOption {
  readonly value: CoverLetterTone;
  readonly label: string;
  readonly subtitle: string;
  readonly description: string;
}

export const COVER_LETTER_TONE_OPTIONS: readonly CoverLetterToneOption[] = [
  {
    value: 'PROFESSIONAL',
    label: 'Professional',
    subtitle: 'Balanced European business tone',
    description: 'Crisp, structured 280–380 word letter balancing quantified achievements and company fit.'
  },
  {
    value: 'CONFIDENT',
    label: 'Confident',
    subtitle: 'Executive ownership voice',
    description: 'Ownership-focused narrative foregrounding architectural scope, impact, and leadership.'
  },
  {
    value: 'CONCISE',
    label: 'Concise',
    subtitle: 'High-signal 3-paragraph pitch',
    description: 'Tight 190–260 word recruiter-first summary eliminating fluff while keeping hard proof.'
  },
  {
    value: 'ENTHUSIASTIC',
    label: 'Enthusiastic',
    subtitle: 'Product & mission aligned',
    description: 'Warm, domain-curious voice connecting genuine product interest to your verified track record.'
  },
  {
    value: 'ACADEMIC',
    label: 'Academic',
    subtitle: 'Formal analytical serif register',
    description: 'Formal European research-engineering register emphasizing degrees, rigor, and methodology.'
  }
];

export interface CoverLetterMetadataDto {
  readonly salutation: string | null;
  readonly signOff: string | null;
  readonly candidateFullName: string | null;
  readonly candidateEmail: string | null;
  readonly candidateLocation: string | null;
  readonly candidateHeadline: string | null;
  readonly workAuthorizationSummary: string | null;
  readonly relocationReadinessNote: string | null;
  readonly companyConnectionSummary: string | null;
  readonly keyAlignmentPoints: readonly string[];
  readonly highlightedSkills: readonly string[];
  readonly groundingWarnings: readonly string[];
  readonly wordCount: number | null;
}

export interface CoverLetterSummaryDto {
  readonly uuid: string;
  readonly jobId: number;
  readonly jobTitle: string;
  readonly companyName: string;
  readonly jobCountry: string | null;
  readonly tailoredResumeUuid: string | null;
  readonly tailoredResumeTitle: string | null;
  readonly title: string;
  readonly tone: CoverLetterTone;
  readonly versionNumber: number;
  readonly wordCount: number;
  readonly aiProvider: string | null;
  readonly aiModelUsed: string | null;
  readonly editedByUser: boolean;
  readonly generatedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface CoverLetterResponse {
  readonly uuid: string;
  readonly jobId: number;
  readonly jobTitle: string;
  readonly companyName: string;
  readonly jobCountry: string | null;
  readonly tailoredResumeUuid: string | null;
  readonly tailoredResumeTitle: string | null;
  readonly title: string;
  readonly content: string;
  readonly tone: CoverLetterTone;
  readonly versionNumber: number;
  readonly personalNotes: string | null;
  readonly wordCount: number;
  readonly metadata: CoverLetterMetadataDto | null;
  readonly aiProvider: string | null;
  readonly aiModelUsed: string | null;
  readonly editedByUser: boolean;
  readonly generatedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface GenerateCoverLetterRequest {
  readonly jobId: number;
  readonly tailoredResumeUuid?: string | null;
  readonly tone?: CoverLetterTone;
  readonly personalNotes?: string | null;
  readonly forceRefresh?: boolean;
}

export interface UpdateCoverLetterRequest {
  readonly title?: string | null;
  readonly tone?: CoverLetterTone | null;
  readonly personalNotes?: string | null;
  readonly content: string;
}

export interface CoverLetterExportFile {
  readonly blob: Blob;
  readonly fileName: string;
  readonly format: CoverLetterExportFormat;
}

export function formatCoverLetterToneLabel(tone: CoverLetterTone | string | null | undefined): string {
  switch (tone) {
    case 'PROFESSIONAL':
      return 'Professional';
    case 'CONFIDENT':
      return 'Confident';
    case 'ACADEMIC':
      return 'Academic';
    case 'ENTHUSIASTIC':
      return 'Enthusiastic';
    case 'CONCISE':
      return 'Concise';
    default:
      return tone ? String(tone) : 'Professional';
  }
}

