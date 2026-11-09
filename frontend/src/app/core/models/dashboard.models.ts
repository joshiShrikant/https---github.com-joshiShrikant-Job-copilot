export interface DashboardActivityItem {
  readonly type: 'APPLICATION' | 'SAVED_JOB' | 'RESUME' | 'NOTIFICATION' | string;
  readonly title: string;
  readonly subtitle: string;
  readonly timestamp: string;
  readonly targetRoute: string;
}

export interface PipelineStageCount {
  readonly status: string;
  readonly label: string;
  readonly count: number;
}

export interface DashboardReadiness {
  readonly profileExists: boolean;
  readonly profileCompletenessPercent: number;
  readonly skillsCount: number;
  readonly experiencesCount: number;
  readonly educationsCount: number;
  readonly resumesCount: number;
  readonly masterResumeApproved: boolean;
  readonly preferencesConfigured: boolean;
  readonly autopilotEnabled: boolean;
  readonly minMatchScore: number;
  readonly preferredCountries: readonly string[];
  readonly preferredJobTitles: readonly string[];
}

export interface DashboardSummaryResponse {
  readonly totalJobsDiscovered: number;
  readonly newJobsToday: number;
  readonly highMatchJobsCount: number;
  readonly sponsorshipOpportunitiesCount: number;
  readonly verifiedSponsorsCount: number;
  readonly savedJobsCount: number;
  readonly interestedJobsCount: number;
  readonly activeApplicationsCount: number;
  readonly unreadNotificationsCount: number;
  readonly readiness: DashboardReadiness;
  readonly pipelineBreakdown: readonly PipelineStageCount[];
  readonly recentActivity: readonly DashboardActivityItem[];
  readonly generatedAt: string;
}

