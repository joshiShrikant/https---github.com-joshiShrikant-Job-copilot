export type NotificationType =
  | 'HIGH_MATCH_JOB'
  | 'APPLICATION_FOLLOW_UP'
  | 'INTERVIEW_REMINDER'
  | 'SPONSORSHIP_VERIFIED'
  | 'RESUME_TAILORED'
  | 'AUTOPILOT_DIGEST'
  | 'SYSTEM_ALERT';

export type NotificationReferenceType =
  | 'JOB'
  | 'JOB_APPLICATION'
  | 'RECRUITER_INTERACTION'
  | 'RESUME'
  | 'INTERVIEW_SESSION'
  | 'COMPANY';

export interface NotificationItem {
  readonly id: number;
  readonly type: NotificationType;
  readonly title: string;
  readonly message: string;
  readonly referenceType: NotificationReferenceType | null;
  readonly referenceId: number | null;
  readonly isRead: boolean;
  readonly readAt: string | null;
  readonly createdAt: string;
  readonly targetRoute: string | null;
}

export interface PagedNotificationsResponse {
  readonly content: readonly NotificationItem[];
  readonly pageNumber: number;
  readonly pageSize: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly isFirst: boolean;
  readonly isLast: boolean;
  readonly hasNext: boolean;
  readonly hasPrevious: boolean;
  readonly unreadCount: number;
}

export interface UnreadCountResponse {
  readonly unreadCount: number;
}

export interface MarkAllReadResponse {
  readonly markedCount: number;
  readonly unreadCount: number;
}

export interface NotificationFilterParams {
  readonly unreadOnly?: boolean;
  readonly type?: NotificationType | '';
  readonly page?: number;
  readonly size?: number;
}

export interface NotificationTypeOption {
  readonly value: NotificationType | '';
  readonly label: string;
}

export const NOTIFICATION_TYPE_OPTIONS: readonly NotificationTypeOption[] = [
  { value: '', label: 'All Types' },
  { value: 'HIGH_MATCH_JOB', label: 'High-Match Jobs' },
  { value: 'SPONSORSHIP_VERIFIED', label: 'Verified Sponsors' },
  { value: 'APPLICATION_FOLLOW_UP', label: 'Application Follow-Ups' },
  { value: 'INTERVIEW_REMINDER', label: 'Interview Reminders' },
  { value: 'AUTOPILOT_DIGEST', label: 'Autopilot Digests' },
  { value: 'RESUME_TAILORED', label: 'Tailored Resumes' },
  { value: 'SYSTEM_ALERT', label: 'System Alerts' }
];

export function formatNotificationTypeLabel(type: NotificationType): string {
  switch (type) {
    case 'HIGH_MATCH_JOB':
      return 'High-Match Job';
    case 'SPONSORSHIP_VERIFIED':
      return 'Verified Sponsor';
    case 'APPLICATION_FOLLOW_UP':
      return 'Follow-Up Due';
    case 'INTERVIEW_REMINDER':
      return 'Interview Reminder';
    case 'AUTOPILOT_DIGEST':
      return 'Autopilot Digest';
    case 'RESUME_TAILORED':
      return 'Resume Ready';
    case 'SYSTEM_ALERT':
    default:
      return 'System Alert';
  }
}

export function getNotificationIcon(type: NotificationType): string {
  switch (type) {
    case 'HIGH_MATCH_JOB':
      return 'auto_awesome';
    case 'SPONSORSHIP_VERIFIED':
      return 'verified_user';
    case 'APPLICATION_FOLLOW_UP':
      return 'event_upcoming';
    case 'INTERVIEW_REMINDER':
      return 'psychology';
    case 'AUTOPILOT_DIGEST':
      return 'bolt';
    case 'RESUME_TAILORED':
      return 'description';
    case 'SYSTEM_ALERT':
    default:
      return 'notifications';
  }
}

