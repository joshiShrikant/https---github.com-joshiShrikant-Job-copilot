import { ApplicationStatus } from './job.models';

export type CommunicationChannel =
  | 'LINKEDIN'
  | 'EMAIL'
  | 'PHONE'
  | 'RECRUITER_PORTAL'
  | 'OTHER';

export type RecruiterMessageType =
  | 'LINKEDIN_CONNECTION'
  | 'INITIAL_OUTREACH'
  | 'FOLLOW_UP'
  | 'POST_INTERVIEW_THANKS'
  | 'OUTREACH'
  | 'OFFER_NEGOTIATION';

export type RecruiterMessageStatus = 'DRAFT' | 'APPROVED' | 'SENT';

export type RecruiterInteractionAction = 'SAVE_DRAFT' | 'APPROVE' | 'MARK_SENT';

export interface OutreachMessageTypeOption {
  readonly value: RecruiterMessageType;
  readonly label: string;
  readonly shortLabel: string;
  readonly defaultChannel: CommunicationChannel;
  readonly hint: string;
  readonly maxCharsHint?: number;
}

export const AI_OUTREACH_TYPE_OPTIONS: readonly OutreachMessageTypeOption[] = [
  {
    value: 'LINKEDIN_CONNECTION',
    label: 'LinkedIn Connection Note',
    shortLabel: 'Connection Note',
    defaultChannel: 'LINKEDIN',
    hint: 'Compact intro capped cleanly inside LinkedIn’s 300-character invite limit.',
    maxCharsHint: 300
  },
  {
    value: 'INITIAL_OUTREACH',
    label: 'Recruiter Introduction',
    shortLabel: 'Recruiter Intro',
    defaultChannel: 'EMAIL',
    hint: 'Concise value proposition referencing your verified European tech background and target role.'
  },
  {
    value: 'FOLLOW_UP',
    label: 'Application Follow-up',
    shortLabel: 'Follow-up',
    defaultChannel: 'EMAIL',
    hint: 'Polite check-in reinforcing role fit and application timeline without pressure.'
  },
  {
    value: 'POST_INTERVIEW_THANKS',
    label: 'Interview Thank-You',
    shortLabel: 'Thank-You',
    defaultChannel: 'EMAIL',
    hint: 'Thoughtful post-interview thank-you note anchored in your conversation and verified skills.'
  }
];

export const ALL_RECRUITER_MESSAGE_TYPES: readonly OutreachMessageTypeOption[] = [
  ...AI_OUTREACH_TYPE_OPTIONS,
  {
    value: 'OUTREACH',
    label: 'General Outreach',
    shortLabel: 'Outreach',
    defaultChannel: 'LINKEDIN',
    hint: 'General recruiter or hiring manager note.'
  },
  {
    value: 'OFFER_NEGOTIATION',
    label: 'Offer Discussion',
    shortLabel: 'Offer Note',
    defaultChannel: 'EMAIL',
    hint: 'Compensation, relocation, or contract clarification note.'
  }
];

export const COMMUNICATION_CHANNEL_OPTIONS: readonly {
  readonly value: CommunicationChannel;
  readonly label: string;
  readonly icon: string;
}[] = [
  { value: 'LINKEDIN', label: 'LinkedIn', icon: 'share' },
  { value: 'EMAIL', label: 'Email', icon: 'mail' },
  { value: 'PHONE', label: 'Phone / Call', icon: 'call' },
  { value: 'RECRUITER_PORTAL', label: 'ATS / Portal', icon: 'web' },
  { value: 'OTHER', label: 'Other', icon: 'chat' }
];

export interface RecruiterInteractionDto {
  readonly id: number;
  readonly recruiterId: number;
  readonly recruiterName: string;
  readonly companyName: string | null;
  readonly applicationUuid: string | null;
  readonly jobId: number | null;
  readonly jobTitle: string | null;
  readonly channel: CommunicationChannel;
  readonly messageType: RecruiterMessageType;
  readonly status: RecruiterMessageStatus;
  readonly subjectLine: string | null;
  readonly messageBody: string;
  readonly sentAt: string | null;
  readonly approvedAt: string | null;
  readonly followUpReminderDate: string | null;
  readonly reminderCompleted: boolean;
  readonly overdueReminder: boolean;
  readonly aiGenerated: boolean;
  readonly aiProvider: string | null;
  readonly aiModelUsed: string | null;
  readonly editedByUser: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface RecruiterResponse {
  readonly id: number;
  readonly fullName: string;
  readonly roleTitle: string | null;
  readonly companyId: number | null;
  readonly companyName: string | null;
  readonly companyVerifiedSponsor: boolean;
  readonly email: string | null;
  readonly linkedinUrl: string | null;
  readonly phone: string | null;
  readonly notes: string | null;
  readonly interactionsCount: number;
  readonly lastInteractionAt: string | null;
  readonly nextFollowUpReminderDate: string | null;
  readonly pendingRemindersCount: number;
  readonly interactions: readonly RecruiterInteractionDto[] | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface PagedRecruitersResponse {
  readonly content: readonly RecruiterResponse[];
  readonly pageNumber: number;
  readonly pageSize: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly isFirst: boolean;
  readonly isLast: boolean;
  readonly hasNext: boolean;
  readonly hasPrevious: boolean;
}

export interface CreateRecruiterRequest {
  readonly fullName: string;
  readonly roleTitle?: string | null;
  readonly companyId?: number | null;
  readonly companyName?: string | null;
  readonly email?: string | null;
  readonly linkedinUrl?: string | null;
  readonly phone?: string | null;
  readonly notes?: string | null;
}

export interface UpdateRecruiterRequest extends CreateRecruiterRequest {
  readonly clearCompany?: boolean;
}

export interface GenerateOutreachRequest {
  readonly channel: CommunicationChannel;
  readonly messageType: RecruiterMessageType;
  readonly applicationUuid?: string | null;
  readonly jobId?: number | null;
  readonly personalContext?: string | null;
  readonly followUpReminderDate?: string | null;
}

export interface OutreachDraftResponse {
  readonly interaction: RecruiterInteractionDto;
  readonly keyTalkingPoints: readonly string[];
  readonly suggestedFollowUpDays: number | null;
  readonly warnings: readonly string[];
  readonly requiresManualApproval: boolean;
  readonly autoSent: boolean;
}

export interface CreateRecruiterInteractionRequest {
  readonly applicationUuid?: string | null;
  readonly channel: CommunicationChannel;
  readonly messageType: RecruiterMessageType;
  readonly subjectLine?: string | null;
  readonly messageBody: string;
  readonly followUpReminderDate?: string | null;
  readonly markAsSent?: boolean;
  readonly explicitApprovalConfirmed?: boolean;
  readonly sentAt?: string | null;
}

export interface UpdateRecruiterInteractionRequest {
  readonly applicationUuid?: string | null;
  readonly clearApplication?: boolean;
  readonly channel?: CommunicationChannel;
  readonly messageType?: RecruiterMessageType;
  readonly subjectLine?: string | null;
  readonly messageBody?: string | null;
  readonly followUpReminderDate?: string | null;
  readonly clearFollowUpReminderDate?: boolean;
  readonly reminderCompleted?: boolean | null;
  readonly action: RecruiterInteractionAction;
  readonly explicitApprovalConfirmed?: boolean;
  readonly sentAt?: string | null;
}

export interface FollowUpReminderItemDto {
  readonly sourceType: 'APPLICATION' | 'RECRUITER_INTERACTION';
  readonly urgency: 'OVERDUE' | 'DUE_TODAY' | 'UPCOMING' | 'COMPLETED';
  readonly reminderDate: string;
  readonly completed: boolean;
  readonly applicationUuid: string | null;
  readonly applicationStatus: ApplicationStatus | null;
  readonly recruiterId: number | null;
  readonly interactionId: number | null;
  readonly contactLabel: string;
  readonly roleTitle: string | null;
  readonly channel: CommunicationChannel | null;
  readonly messageType: RecruiterMessageType | null;
  readonly messageStatus: RecruiterMessageStatus | null;
}

export interface FollowUpRemindersResponse {
  readonly overdueCount: number;
  readonly dueTodayCount: number;
  readonly upcomingCount: number;
  readonly totalPendingCount: number;
  readonly items: readonly FollowUpReminderItemDto[];
}

export function formatMessageTypeLabel(type: RecruiterMessageType): string {
  return (
    ALL_RECRUITER_MESSAGE_TYPES.find(item => item.value === type)?.label ?? type.replace(/_/g, ' ')
  );
}

export function formatChannelLabel(channel: CommunicationChannel): string {
  return COMMUNICATION_CHANNEL_OPTIONS.find(item => item.value === channel)?.label ?? channel;
}

export function getMessageStatusMeta(status: RecruiterMessageStatus): {
  label: string;
  tone: 'neutral' | 'warning' | 'success';
  icon: string;
} {
  switch (status) {
    case 'APPROVED':
      return { label: 'Approved (Unsent)', tone: 'warning', icon: 'verified' };
    case 'SENT':
      return { label: 'Logged as Sent', tone: 'success', icon: 'mark_email_read' };
    case 'DRAFT':
    default:
      return { label: 'Editable Draft', tone: 'neutral', icon: 'edit_note' };
  }
}

