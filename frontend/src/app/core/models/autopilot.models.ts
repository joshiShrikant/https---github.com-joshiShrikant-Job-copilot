import { JobSummary } from './job.models';

export type AutopilotTriggerType = 'MANUAL' | 'SCHEDULED';
export type AutopilotRunStatus = 'COMPLETED' | 'FAILED' | 'SKIPPED_DISABLED' | 'SKIPPED_PAUSED';

export interface AutopilotRunLog {
  readonly id: number;
  readonly triggerType: AutopilotTriggerType;
  readonly status: AutopilotRunStatus;
  readonly ingestionTriggered: boolean;
  readonly jobsEvaluated: number;
  readonly newMatchesFound: number;
  readonly recommendedJobsCount: number;
  readonly notificationsCreated: number;
  readonly emailDispatched: boolean;
  readonly summaryMessage: string | null;
  readonly errorMessage: string | null;
  readonly startedAt: string;
  readonly completedAt: string | null;
}

export interface AutopilotStatusResponse {
  readonly autopilotEnabled: boolean;
  readonly autopilotPaused: boolean;
  readonly emailNotificationsEnabled: boolean;
  readonly serverEmailRelayEnabled: boolean;
  readonly lastAutopilotRunAt: string | null;
  readonly minMatchScore: number;
  readonly preferredJobTitles: readonly string[];
  readonly preferredCountries: readonly string[];
  readonly preferredCities: readonly string[];
  readonly recommendedJobsCount: number;
  readonly unreadNotificationsCount: number;
  readonly latestRun: AutopilotRunLog | null;
}

export interface RecommendedJobItem {
  readonly job: JobSummary;
  readonly matchingSkills: readonly string[];
  readonly missingSkills: readonly string[];
  readonly recommendationReasons: readonly string[];
  readonly experienceCompatibility: string | null;
  readonly meetsMinMatchScore: boolean;
  readonly matchesPreferredCountry: boolean;
  readonly matchesPreferredTitle: boolean;
  readonly verifiedSponsor: boolean;
  readonly sponsorshipEvidenceSummary: string | null;
  readonly aiAnalysisSummary: string | null;
  readonly alreadyApplied: boolean;
}

export interface PagedRecommendedJobsResponse {
  readonly content: readonly RecommendedJobItem[];
  readonly pageNumber: number;
  readonly pageSize: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly isFirst: boolean;
  readonly isLast: boolean;
  readonly hasNext: boolean;
  readonly hasPrevious: boolean;
  readonly minMatchScoreUsed: number;
  readonly highMatchCount: number;
  readonly verifiedSponsorCount: number;
  readonly autopilotPaused: boolean;
}

export interface PagedAutopilotHistoryResponse {
  readonly content: readonly AutopilotRunLog[];
  readonly pageNumber: number;
  readonly pageSize: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly isFirst: boolean;
  readonly isLast: boolean;
  readonly hasNext: boolean;
  readonly hasPrevious: boolean;
}

export interface RunAutopilotRequest {
  readonly refreshExternalJobs?: boolean;
  readonly analyzeTopMatchesWithAi?: boolean;
}

export interface RecommendedJobsFilterParams {
  readonly minMatchScore?: number | null;
  readonly verifiedSponsorsOnly?: boolean;
  readonly excludeApplied?: boolean;
  readonly page?: number;
  readonly size?: number;
}

