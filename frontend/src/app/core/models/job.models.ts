import { PageResponse } from './api.models';
import type {
  SponsorConfidenceLevel,
  SponsorshipRecordStatus,
  VisaSponsorshipStatus
} from './sponsorship.models';

export type { SponsorConfidenceLevel, SponsorshipRecordStatus, VisaSponsorshipStatus };

export type JobSponsorStatus =
  | 'VERIFIED_SPONSOR'
  | 'SPONSORSHIP_MENTIONED'
  | 'RELOCATION_OFFERED'
  | 'UNCONFIRMED'
  | 'NO_SPONSORSHIP';

export type ApplicationStatus =
  | 'INTERESTED'
  | 'SAVED'
  | 'APPLIED'
  | 'ASSESSMENT'
  | 'TECH_INTERVIEW'
  | 'HR_INTERVIEW'
  | 'FINAL_INTERVIEW'
  | 'OFFER'
  | 'REJECTED'
  | 'WITHDRAWN';

export type JobWorkArrangement = 'REMOTE' | 'HYBRID' | 'ONSITE' | 'ANY';

export type EmploymentType =
  | 'FULL_TIME'
  | 'PART_TIME'
  | 'CONTRACT'
  | 'INTERNSHIP'
  | 'FREELANCE';

export type ExperienceLevel =
  | 'ENTRY'
  | 'JUNIOR'
  | 'MID'
  | 'SENIOR'
  | 'LEAD'
  | 'PRINCIPAL';

export type SalaryPeriod = 'YEARLY' | 'MONTHLY' | 'DAILY' | 'HOURLY';

export type JobSortOption =
  | 'postedAt,desc'
  | 'postedAt,asc'
  | 'matchScore,desc'
  | 'salaryMax,desc';

export interface JobSummary {
  readonly id: number;
  readonly title: string;
  readonly companyName: string;
  readonly location: string | null;
  readonly city?: string | null;
  readonly country: string;
  readonly workArrangement: JobWorkArrangement;
  readonly employmentType?: EmploymentType | null;
  readonly experienceLevel?: ExperienceLevel | null;
  readonly salaryMin: number | null;
  readonly salaryMax: number | null;
  readonly salaryCurrency: string | null;
  readonly salaryPeriod?: SalaryPeriod | null;
  readonly sponsorStatus: JobSponsorStatus;
  readonly matchPercentage: number | null;
  readonly sourceProvider: string;
  readonly postedAt: string;
  readonly isSaved: boolean;
  readonly isInterested: boolean;
  readonly isHidden?: boolean;
  readonly skills?: readonly string[];
  readonly descriptionSnippet?: string | null;
  readonly originalUrl?: string | null;
}

export type JobSummaryPageResponse = PageResponse<JobSummary>;

export interface CompanySponsorshipSummary {
  readonly id: number;
  readonly name: string;
  readonly websiteUrl: string | null;
  readonly careersUrl: string | null;
  readonly headquartersCountry: string | null;
  readonly headquartersCity: string | null;
  readonly industry: string | null;
  readonly companySize: string | null;
  readonly logoUrl: string | null;
  readonly isVerifiedSponsor: boolean;
  readonly sponsorConfidenceLevel: SponsorConfidenceLevel;
}

export interface SponsorshipRegistryRecord {
  readonly id: number;
  readonly country: string;
  readonly registrySource: string;
  readonly registryIdentifier: string;
  readonly officialRegisteredName: string;
  readonly status: SponsorshipRecordStatus;
  readonly evidenceNotes: string | null;
  readonly sourceUrl: string | null;
  readonly verifiedAt: string | null;
}

export interface SponsorshipBreakdown {
  readonly sponsorStatus: JobSponsorStatus;
  readonly detailedStatus?: VisaSponsorshipStatus;
  readonly detailedStatusLabel?: string | null;
  readonly vacancyStatus?: VisaSponsorshipStatus;
  readonly vacancyStatusLabel?: string | null;
  readonly vacancySponsorshipConfirmed?: boolean;
  readonly vacancySponsorshipAssumed?: boolean;
  readonly isVerifiedSponsor: boolean;
  readonly confidenceLevel: SponsorConfidenceLevel;
  readonly summary: string;
  readonly vacancyCaveat?: string | null;
  readonly countryGuideSummary?: string | null;
  readonly officialRegisterUrl?: string | null;
  readonly verifiedAt?: string | null;
  readonly evidenceSignals: readonly string[];
  readonly registryRecords: readonly SponsorshipRegistryRecord[];
}

export interface JobMatchBreakdown {
  readonly finalScore: number | null;
  readonly matchPercentage: number | null;
  readonly deterministicScore: number | null;
  readonly aiScore: number | null;
  readonly skillScore: number | null;
  readonly experienceScore: number | null;
  readonly titleScore: number | null;
  readonly locationScore: number | null;
  readonly sponsorshipScore: number | null;
  readonly salaryScore: number | null;
  readonly matchingSkills: readonly string[];
  readonly missingSkills: readonly string[];
  readonly experienceCompatibility?: string | null;
  readonly recommendationReasons?: readonly string[];
  readonly aiRationale: string | null;
  readonly aiStrongMatches?: readonly string[];
  readonly aiMissingSkills?: readonly string[];
  readonly aiExperienceGaps?: readonly string[];
  readonly aiPotentialConcerns?: readonly string[];
  readonly aiImprovementSuggestions?: readonly string[];
  readonly aiProvider?: string | null;
  readonly aiModelUsed?: string | null;
  readonly aiEvaluatedAt?: string | null;
  readonly calculatedAt: string | null;
}

export interface EvaluateAiMatchRequest {
  readonly forceRefresh?: boolean;
}

export interface MatchingWeights {
  readonly technicalSkills: number;
  readonly experience: number;
  readonly jobTitle: number;
  readonly location: number;
  readonly sponsorship: number;
  readonly salary: number;
}

export interface JobMatchSummaryItem {
  readonly jobId: number;
  readonly title: string;
  readonly companyName: string;
  readonly country: string;
  readonly matchPercentage: number;
  readonly finalScore: number;
  readonly experienceCompatibility: string | null;
}

export interface BatchMatchRecalculationResponse {
  readonly evaluatedJobsCount: number;
  readonly highMatchJobsCount: number;
  readonly minMatchThreshold: number;
  readonly calculatedAt: string;
  readonly weightsUsed: MatchingWeights;
  readonly topMatches: readonly JobMatchSummaryItem[];
}

export interface JobAiAnalysis {
  readonly id?: number;
  readonly jobId?: number;
  readonly analysisVersion?: number | null;
  readonly extractedTitle?: string | null;
  readonly extractedCompany?: string | null;
  readonly extractedCountry?: string | null;
  readonly extractedLocation?: string | null;
  readonly requiredExperienceYears?: number | null;
  readonly requiredExperienceLevel?: ExperienceLevel | null;
  readonly requiredExperienceSummary?: string | null;
  readonly requiredSkills: readonly string[];
  readonly niceToHaveSkills: readonly string[];
  readonly responsibilities: readonly string[];
  readonly benefits: readonly string[];
  readonly languageRequirements: readonly string[];
  readonly salaryMin?: number | null;
  readonly salaryMax?: number | null;
  readonly salaryCurrency?: string | null;
  readonly salaryPeriod?: SalaryPeriod | null;
  readonly workArrangement?: JobWorkArrangement | null;
  readonly employmentType?: EmploymentType | null;
  readonly sponsorshipOffered?: boolean | null;
  readonly visaSponsorshipAnalysis: string | null;
  readonly relocationAssistanceOffered?: boolean | null;
  readonly relocationAnalysis: string | null;
  readonly summary: string | null;
  readonly aiProvider?: string | null;
  readonly aiModelUsed: string | null;
  readonly analyzedAt: string | null;
}

export interface AnalyzeJobDescriptionRequest {
  readonly forceRefresh?: boolean;
}

export interface JobApplicationSummary {
  readonly uuid: string;
  readonly status: ApplicationStatus;
  readonly appliedDate: string | null;
  readonly nextFollowUpDate: string | null;
  readonly nextInterviewDate: string | null;
}

export interface JobDetail {
  readonly id: number;
  readonly title: string;
  readonly companyName: string;
  readonly country: string;
  readonly city?: string | null;
  readonly location: string | null;
  readonly workArrangement: JobWorkArrangement;
  readonly employmentType: EmploymentType;
  readonly experienceLevel?: ExperienceLevel | null;
  readonly minExperienceYears?: number | null;
  readonly salaryMin: number | null;
  readonly salaryMax: number | null;
  readonly salaryCurrency: string | null;
  readonly salaryPeriod?: SalaryPeriod | null;
  readonly description: string;
  readonly descriptionSnippet?: string | null;
  readonly skills: readonly string[];
  readonly sponsorStatus: JobSponsorStatus;
  readonly matchPercentage?: number | null;
  readonly sourceProvider?: string | null;
  readonly sourceJobId?: string | null;
  readonly originalUrl: string;
  readonly applicationUrl?: string | null;
  readonly postedAt: string;
  readonly expiresAt?: string | null;
  readonly isActive?: boolean;
  readonly isSaved?: boolean;
  readonly savedAt?: string | null;
  readonly isInterested?: boolean;
  readonly interestedAt?: string | null;
  readonly isHidden?: boolean;
  readonly hiddenAt?: string | null;
  readonly hasApplied?: boolean;
  readonly appliedAt?: string | null;
  readonly redirectClickedAt?: string | null;
  readonly company?: CompanySponsorshipSummary | null;
  readonly sponsorshipBreakdown?: SponsorshipBreakdown | null;
  readonly matchBreakdown?: JobMatchBreakdown | null;
  readonly aiAnalysis?: JobAiAnalysis | null;
  readonly application?: JobApplicationSummary | null;
}

export interface JobInteractionResponse {
  readonly jobId: number;
  readonly isSaved: boolean;
  readonly savedAt: string | null;
  readonly isInterested: boolean;
  readonly interestedAt: string | null;
  readonly isHidden: boolean;
  readonly hiddenAt: string | null;
  readonly hasApplied: boolean;
  readonly appliedAt: string | null;
  readonly redirectClickedAt: string | null;
}

export interface ApplyRedirectRequest {
  readonly markApplied?: boolean;
}

export interface ApplyRedirectResponse {
  readonly jobId: number;
  readonly targetUrl: string;
  readonly redirectClickedAt: string;
  readonly hasApplied: boolean;
  readonly appliedAt: string | null;
  readonly interaction: JobInteractionResponse;
}

export interface JobSearchFilters {
  readonly query: string;
  readonly country: string;
  readonly city: string;
  readonly workArrangement: Exclude<JobWorkArrangement, 'ANY'> | '';
  readonly employmentType: EmploymentType | '';
  readonly experienceLevel: ExperienceLevel | '';
  readonly sponsorStatus: JobSponsorStatus | '';
  readonly minMatchScore: number | null;
  readonly minSalary: number | null;
  readonly savedOnly: boolean;
  readonly interestedOnly: boolean;
  readonly includeHidden: boolean;
  readonly sort: JobSortOption;
}

export const DEFAULT_JOB_SEARCH_FILTERS: JobSearchFilters = {
  query: '',
  country: '',
  city: '',
  workArrangement: '',
  employmentType: '',
  experienceLevel: '',
  sponsorStatus: '',
  minMatchScore: null,
  minSalary: null,
  savedOnly: false,
  interestedOnly: false,
  includeHidden: false,
  sort: 'postedAt,desc'
};

export function createDefaultJobSearchFilters(): JobSearchFilters {
  return { ...DEFAULT_JOB_SEARCH_FILTERS };
}

export function countActiveJobFilters(filters: JobSearchFilters): number {
  let count = 0;
  if (filters.query.trim().length > 0) count++;
  if (filters.country.trim().length > 0) count++;
  if (filters.city.trim().length > 0) count++;
  if (filters.workArrangement) count++;
  if (filters.employmentType) count++;
  if (filters.experienceLevel) count++;
  if (filters.sponsorStatus) count++;
  if (filters.minMatchScore !== null && filters.minMatchScore > 0) count++;
  if (filters.minSalary !== null && filters.minSalary > 0) count++;
  if (filters.savedOnly) count++;
  if (filters.interestedOnly) count++;
  if (filters.includeHidden) count++;
  return count;
}

export interface SelectFilterOption<T extends string = string> {
  readonly value: T;
  readonly label: string;
}

export const EUROPEAN_COUNTRY_OPTIONS: readonly string[] = [
  'Germany',
  'Netherlands',
  'Luxembourg',
  'Switzerland',
  'Ireland',
  'Austria',
  'Belgium',
  'Sweden',
  'Denmark',
  'Finland',
  'France',
  'Poland',
  'Portugal',
  'Spain',
  'United Kingdom'
];

export const JOB_SPONSOR_STATUS_OPTIONS: readonly SelectFilterOption<JobSponsorStatus>[] = [
  { value: 'VERIFIED_SPONSOR', label: 'Verified Sponsor' },
  { value: 'SPONSORSHIP_MENTIONED', label: 'Visa Sponsorship Mentioned' },
  { value: 'RELOCATION_OFFERED', label: 'Relocation Offered' },
  { value: 'UNCONFIRMED', label: 'Unconfirmed Sponsor' },
  { value: 'NO_SPONSORSHIP', label: 'No Sponsorship' }
];

export const JOB_WORK_ARRANGEMENT_OPTIONS: readonly SelectFilterOption<
  Exclude<JobWorkArrangement, 'ANY'>
>[] = [
  { value: 'REMOTE', label: 'Remote' },
  { value: 'HYBRID', label: 'Hybrid' },
  { value: 'ONSITE', label: 'On-site' }
];

export const JOB_EMPLOYMENT_TYPE_OPTIONS: readonly SelectFilterOption<EmploymentType>[] = [
  { value: 'FULL_TIME', label: 'Full-time' },
  { value: 'PART_TIME', label: 'Part-time' },
  { value: 'CONTRACT', label: 'Contract' },
  { value: 'INTERNSHIP', label: 'Internship' },
  { value: 'FREELANCE', label: 'Freelance' }
];

export const JOB_EXPERIENCE_LEVEL_OPTIONS: readonly SelectFilterOption<ExperienceLevel>[] = [
  { value: 'ENTRY', label: 'Entry' },
  { value: 'JUNIOR', label: 'Junior' },
  { value: 'MID', label: 'Mid-level' },
  { value: 'SENIOR', label: 'Senior' },
  { value: 'LEAD', label: 'Lead' },
  { value: 'PRINCIPAL', label: 'Principal' }
];

export const JOB_MIN_MATCH_OPTIONS: readonly { readonly value: number | null; readonly label: string }[] = [
  { value: null, label: 'Any Match Score' },
  { value: 50, label: '50%+ Match' },
  { value: 65, label: '65%+ Match' },
  { value: 80, label: '80%+ Strong Match' }
];

export const JOB_SORT_OPTIONS: readonly SelectFilterOption<JobSortOption>[] = [
  { value: 'postedAt,desc', label: 'Newest First' },
  { value: 'matchScore,desc', label: 'Best Match Score' },
  { value: 'salaryMax,desc', label: 'Highest Salary' },
  { value: 'postedAt,asc', label: 'Oldest First' }
];

export function formatWorkArrangementLabel(value: JobWorkArrangement | null | undefined): string {
  switch (value) {
    case 'REMOTE':
      return 'Remote';
    case 'HYBRID':
      return 'Hybrid';
    case 'ONSITE':
      return 'On-site';
    case 'ANY':
    default:
      return 'Flexible';
  }
}

export function formatEmploymentTypeLabel(value: EmploymentType | null | undefined): string {
  switch (value) {
    case 'FULL_TIME':
      return 'Full-time';
    case 'PART_TIME':
      return 'Part-time';
    case 'CONTRACT':
      return 'Contract';
    case 'INTERNSHIP':
      return 'Internship';
    case 'FREELANCE':
      return 'Freelance';
    default:
      return 'Full-time';
  }
}

export function formatExperienceLevelLabel(value: ExperienceLevel | null | undefined): string {
  switch (value) {
    case 'ENTRY':
      return 'Entry';
    case 'JUNIOR':
      return 'Junior';
    case 'MID':
      return 'Mid-level';
    case 'SENIOR':
      return 'Senior';
    case 'LEAD':
      return 'Lead';
    case 'PRINCIPAL':
      return 'Principal';
    default:
      return '';
  }
}

export function formatSponsorStatusLabel(value: JobSponsorStatus | null | undefined): string {
  switch (value) {
    case 'VERIFIED_SPONSOR':
      return 'Verified Sponsor';
    case 'SPONSORSHIP_MENTIONED':
      return 'Visa Mentioned';
    case 'RELOCATION_OFFERED':
      return 'Relocation Offered';
    case 'UNCONFIRMED':
      return 'Unconfirmed Sponsor';
    case 'NO_SPONSORSHIP':
      return 'No Sponsorship';
    default:
      return 'Sponsor Unverified';
  }
}

export function formatSponsorshipRecordStatusLabel(
  value: SponsorshipRecordStatus | null | undefined
): string {
  switch (value) {
    case 'RECOGNIZED_SPONSOR':
      return 'Recognized Sponsor';
    case 'VISA_CAPABLE':
      return 'Visa Capable';
    case 'SUSPENDED':
      return 'Suspended';
    case 'REVOKED':
      return 'Revoked';
    default:
      return 'Registry Record';
  }
}
