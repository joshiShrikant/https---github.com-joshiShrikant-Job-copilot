import {
  ApplicationStatus,
  EmploymentType,
  JobSponsorStatus,
  JobWorkArrangement
} from './job.models';

export type { ApplicationStatus };

export interface ApplicationStageMeta {
  readonly status: ApplicationStatus;
  readonly label: string;
  readonly shortLabel: string;
  readonly icon: string;
  readonly tone: 'success' | 'brand' | 'warning' | 'danger' | 'neutral';
  readonly group: 'Preparation' | 'Active' | 'Interviewing' | 'Outcome';
}

export const APPLICATION_STAGE_LIST: readonly ApplicationStageMeta[] = [
  {
    status: 'INTERESTED',
    label: 'Interested',
    shortLabel: 'Interested',
    icon: 'favorite',
    tone: 'brand',
    group: 'Preparation'
  },
  {
    status: 'SAVED',
    label: 'Saved',
    shortLabel: 'Saved',
    icon: 'bookmark',
    tone: 'neutral',
    group: 'Preparation'
  },
  {
    status: 'APPLIED',
    label: 'Applied',
    shortLabel: 'Applied',
    icon: 'send',
    tone: 'brand',
    group: 'Active'
  },
  {
    status: 'ASSESSMENT',
    label: 'Assessment',
    shortLabel: 'Assessment',
    icon: 'assignment',
    tone: 'warning',
    group: 'Active'
  },
  {
    status: 'TECH_INTERVIEW',
    label: 'Tech Interview',
    shortLabel: 'Tech Int.',
    icon: 'code',
    tone: 'warning',
    group: 'Interviewing'
  },
  {
    status: 'HR_INTERVIEW',
    label: 'HR Interview',
    shortLabel: 'HR Int.',
    icon: 'forum',
    tone: 'warning',
    group: 'Interviewing'
  },
  {
    status: 'FINAL_INTERVIEW',
    label: 'Final Round',
    shortLabel: 'Final',
    icon: 'groups',
    tone: 'brand',
    group: 'Interviewing'
  },
  {
    status: 'OFFER',
    label: 'Offer',
    shortLabel: 'Offer',
    icon: 'workspace_premium',
    tone: 'success',
    group: 'Outcome'
  },
  {
    status: 'REJECTED',
    label: 'Rejected',
    shortLabel: 'Rejected',
    icon: 'close',
    tone: 'danger',
    group: 'Outcome'
  },
  {
    status: 'WITHDRAWN',
    label: 'Withdrawn',
    shortLabel: 'Withdrawn',
    icon: 'undo',
    tone: 'neutral',
    group: 'Outcome'
  }
];

export const APPLICATION_STAGE_BY_STATUS: Record<ApplicationStatus, ApplicationStageMeta> =
  APPLICATION_STAGE_LIST.reduce(
    (acc, meta) => {
      acc[meta.status] = meta;
      return acc;
    },
    {} as Record<ApplicationStatus, ApplicationStageMeta>
  );

export interface ApplicationStatusHistoryDto {
  readonly id: number;
  readonly previousStatus: ApplicationStatus | null;
  readonly newStatus: ApplicationStatus;
  readonly transitionNotes: string | null;
  readonly transitionedAt: string;
}

export interface ApplicationDocumentSummaryDto {
  readonly uuid: string;
  readonly title: string;
  readonly versionNumber: number | null;
  readonly subtitle: string | null;
  readonly updatedAt: string | null;
}

export interface JobApplicationDetailResponse {
  readonly uuid: string;
  readonly jobId: number;
  readonly jobTitle: string;
  readonly companyId: number | null;
  readonly companyName: string;
  readonly jobCountry: string | null;
  readonly jobCity: string | null;
  readonly jobLocation: string | null;
  readonly workArrangement: JobWorkArrangement | null;
  readonly employmentType: EmploymentType | null;
  readonly sponsorStatus: JobSponsorStatus | null;
  readonly jobSalaryMin: number | null;
  readonly jobSalaryMax: number | null;
  readonly jobSalaryCurrency: string | null;
  readonly jobOriginalUrl: string | null;
  readonly jobApplicationUrl: string | null;
  readonly matchPercentage: number | null;
  readonly status: ApplicationStatus;
  readonly appliedDate: string | null;
  readonly tailoredResume: ApplicationDocumentSummaryDto | null;
  readonly coverLetter: ApplicationDocumentSummaryDto | null;
  readonly currentSalaryOffered: number | null;
  readonly offeredCurrency: string | null;
  readonly nextFollowUpDate: string | null;
  readonly followUpCompleted: boolean;
  readonly overdueFollowUp: boolean;
  readonly nextInterviewDate: string | null;
  readonly notes: string | null;
  readonly version: number;
  readonly recruiterInteractionsCount: number;
  readonly statusHistory: readonly ApplicationStatusHistoryDto[];
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface CreateApplicationRequest {
  readonly jobId: number;
  readonly status?: ApplicationStatus;
  readonly tailoredResumeUuid?: string | null;
  readonly coverLetterUuid?: string | null;
  readonly appliedDate?: string | null;
  readonly nextFollowUpDate?: string | null;
  readonly nextInterviewDate?: string | null;
  readonly currentSalaryOffered?: number | null;
  readonly offeredCurrency?: string | null;
  readonly notes?: string | null;
  readonly transitionNotes?: string | null;
}

export interface UpdateApplicationRequest {
  readonly tailoredResumeUuid?: string | null;
  readonly clearTailoredResume?: boolean;
  readonly coverLetterUuid?: string | null;
  readonly clearCoverLetter?: boolean;
  readonly appliedDate?: string | null;
  readonly clearAppliedDate?: boolean;
  readonly nextFollowUpDate?: string | null;
  readonly clearNextFollowUpDate?: boolean;
  readonly followUpCompleted?: boolean | null;
  readonly nextInterviewDate?: string | null;
  readonly clearNextInterviewDate?: boolean;
  readonly currentSalaryOffered?: number | null;
  readonly clearSalaryOffer?: boolean;
  readonly offeredCurrency?: string | null;
  readonly notes?: string | null;
  readonly expectedVersion?: number | null;
}

export interface UpdateApplicationStatusRequest {
  readonly newStatus: ApplicationStatus;
  readonly transitionNotes?: string | null;
  readonly appliedDate?: string | null;
  readonly nextInterviewDate?: string | null;
  readonly nextFollowUpDate?: string | null;
}

export interface ApplicationFilters {
  readonly status: ApplicationStatus | '';
  readonly query: string;
  readonly dueFollowUpOnly: boolean;
  readonly hasInterviewOnly: boolean;
}

export function getApplicationStageMeta(status: ApplicationStatus): ApplicationStageMeta {
  return APPLICATION_STAGE_BY_STATUS[status] ?? APPLICATION_STAGE_LIST[0];
}

export function formatSalaryOffer(
  amount: number | null | undefined,
  currency: string | null | undefined
): string | null {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return null;
  }
  const normalizedCurrency = (currency ?? 'EUR').trim().toUpperCase() || 'EUR';
  try {
    return new Intl.NumberFormat('en-IE', {
      style: 'currency',
      currency: normalizedCurrency,
      maximumFractionDigits: 0
    }).format(amount);
  } catch {
    return `${normalizedCurrency} ${Math.round(amount).toLocaleString('en-IE')}`;
  }
}

export function toDateInputValue(isoOrDate: string | null | undefined): string {
  if (!isoOrDate) {
    return '';
  }
  const trimmed = isoOrDate.trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
    return trimmed;
  }
  const parsed = new Date(trimmed);
  if (Number.isNaN(parsed.getTime())) {
    return '';
  }
  const year = parsed.getFullYear();
  const month = String(parsed.getMonth() + 1).padStart(2, '0');
  const day = String(parsed.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function toDatetimeLocalValue(iso: string | null | undefined): string {
  if (!iso) {
    return '';
  }
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) {
    return '';
  }
  const year = parsed.getFullYear();
  const month = String(parsed.getMonth() + 1).padStart(2, '0');
  const day = String(parsed.getDate()).padStart(2, '0');
  const hours = String(parsed.getHours()).padStart(2, '0');
  const mins = String(parsed.getMinutes()).padStart(2, '0');
  return `${year}-${month}-${day}T${hours}:${mins}`;
}

export function datetimeLocalToUtcIso(localValue: string | null | undefined): string | null {
  if (!localValue?.trim()) {
    return null;
  }
  const parsed = new Date(localValue.trim());
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  return parsed.toISOString();
}

export function dateInputToStartOfDayUtcIso(dateValue: string | null | undefined): string | null {
  if (!dateValue?.trim()) {
    return null;
  }
  const trimmed = dateValue.trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
    return `${trimmed}T09:00:00Z`;
  }
  const parsed = new Date(trimmed);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
}

export function normalizeSafeHttpUrl(rawUrl: string | null | undefined): string | null {
  if (!rawUrl?.trim()) {
    return null;
  }
  try {
    const parsed = new URL(rawUrl.trim());
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.toString() : null;
  } catch {
    return null;
  }
}

