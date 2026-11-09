export type WorkArrangement = 'ANY' | 'REMOTE' | 'HYBRID' | 'ONSITE';

export interface WorkArrangementOption {
  readonly value: WorkArrangement;
  readonly label: string;
  readonly description: string;
}

export const WORK_ARRANGEMENT_OPTIONS: readonly WorkArrangementOption[] = [
  { value: 'ANY', label: 'Any Arrangement', description: 'Include remote, hybrid, and on-site roles' },
  { value: 'REMOTE', label: 'Remote Only', description: 'Prioritize distributed / remote-first positions' },
  { value: 'HYBRID', label: 'Hybrid', description: 'Combine office collaboration with remote flexibility' },
  { value: 'ONSITE', label: 'On-Site', description: 'In-office engineering roles at European hubs' }
];

export type AlertCadence = 'INSTANT' | 'DAILY' | 'WEEKLY';
export type WorkspaceDensity = 'COMFORTABLE' | 'COMPACT';

export interface WorkspacePreferences {
  readonly alertCadence: AlertCadence;
  readonly density: WorkspaceDensity;
}

export interface JobPreferencesResponse {
  readonly preferredJobTitles: readonly string[];
  readonly preferredCountries: readonly string[];
  readonly preferredCities: readonly string[];
  readonly minSalary: number | null;
  readonly salaryCurrency: string;
  readonly requiresSponsorship: boolean;
  readonly requiresRelocation: boolean;
  readonly workArrangement: WorkArrangement;
  readonly englishSpeakingOnly: boolean;
  readonly minMatchScore: number;
  readonly autopilotEnabled: boolean;
  readonly autopilotPaused?: boolean;
  readonly emailNotificationsEnabled?: boolean;
  readonly lastAutopilotRunAt?: string | null;
  readonly updatedAt: string | null;
}

export interface UpdateJobPreferencesRequest {
  readonly preferredJobTitles?: readonly string[] | null;
  readonly preferredCountries?: readonly string[] | null;
  readonly preferredCities?: readonly string[] | null;
  readonly minSalary?: number | null;
  readonly salaryCurrency?: string | null;
  readonly requiresSponsorship?: boolean | null;
  readonly requiresRelocation?: boolean | null;
  readonly workArrangement?: WorkArrangement | null;
  readonly englishSpeakingOnly?: boolean | null;
  readonly minMatchScore?: number | null;
  readonly autopilotEnabled?: boolean | null;
  readonly autopilotPaused?: boolean | null;
  readonly emailNotificationsEnabled?: boolean | null;
}

