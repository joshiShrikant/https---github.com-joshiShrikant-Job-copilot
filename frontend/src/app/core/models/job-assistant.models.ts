export type AssistantActionType =
  | 'ANALYZE_JOB'
  | 'EXPLAIN_MATCH_SCORE'
  | 'GENERATE_TAILORED_RESUME'
  | 'GENERATE_COVER_LETTER'
  | 'IDENTIFY_SKILL_GAPS'
  | 'PREPARE_RECRUITER_MESSAGE'
  | 'GENERATE_FOLLOW_UP_EMAIL'
  | 'SUGGEST_SALARY_EXPECTATIONS'
  | 'EXPLAIN_SPONSORSHIP'
  | 'INTERVIEW_PREP_TOPICS'
  | 'CUSTOM_QUESTION';

export interface AssistantActionOption {
  readonly actionType: AssistantActionType;
  readonly label: string;
  readonly shortLabel: string;
  readonly icon: string;
  readonly description: string;
}

export const ASSISTANT_ACTION_OPTIONS: readonly AssistantActionOption[] = [
  {
    actionType: 'ANALYZE_JOB',
    label: 'Analyze Job',
    shortLabel: 'Analyze Job',
    icon: 'manage_search',
    description: 'Summarize core scope, technical priorities, and delivery realities.'
  },
  {
    actionType: 'EXPLAIN_MATCH_SCORE',
    label: 'Explain Match Score',
    shortLabel: 'Explain Match',
    icon: 'analytics',
    description: 'Break down why your profile scores well and where tradeoffs lie.'
  },
  {
    actionType: 'IDENTIFY_SKILL_GAPS',
    label: 'Identify Skill Gaps',
    shortLabel: 'Skill Gaps',
    icon: 'fact_check',
    description: 'Compare required role skills against your verified profile and CV.'
  },
  {
    actionType: 'GENERATE_TAILORED_RESUME',
    label: 'Tailored Resume Strategy',
    shortLabel: 'Resume Plan',
    icon: 'edit_document',
    description: 'Draft targeted resume framing bullets grounded strictly in your experience.'
  },
  {
    actionType: 'GENERATE_COVER_LETTER',
    label: 'Draft Cover Letter',
    shortLabel: 'Cover Letter',
    icon: 'article',
    description: 'Compose a ready-to-edit application letter tailored to this company.'
  },
  {
    actionType: 'PREPARE_RECRUITER_MESSAGE',
    label: 'Prepare Recruiter Message',
    shortLabel: 'Recruiter Note',
    icon: 'forum',
    description: 'Draft concise recruiter or hiring manager LinkedIn/email outreach.'
  },
  {
    actionType: 'GENERATE_FOLLOW_UP_EMAIL',
    label: 'Generate Follow-Up Email',
    shortLabel: 'Follow-Up Email',
    icon: 'forward_to_inbox',
    description: 'Write a polite, concrete application or post-interview follow-up.'
  },
  {
    actionType: 'SUGGEST_SALARY_EXPECTATIONS',
    label: 'Suggest Salary Expectations',
    shortLabel: 'Salary Advice',
    icon: 'payments',
    description: 'Calibrate compensation talking points using posted range and location.'
  },
  {
    actionType: 'EXPLAIN_SPONSORSHIP',
    label: 'Explain Sponsorship Considerations',
    shortLabel: 'Visa & Relocation',
    icon: 'verified_user',
    description: 'Explain sponsor status, public registry evidence, and practical questions.'
  },
  {
    actionType: 'INTERVIEW_PREP_TOPICS',
    label: 'Interview Prep Topics',
    shortLabel: 'Prep Topics',
    icon: 'psychology',
    description: 'List high-yield architecture, stack, and behavioral interview areas.'
  }
];

export interface JobAssistantMessageResponse {
  readonly id: number;
  readonly uuid: string;
  readonly jobId: number;
  readonly actionType: AssistantActionType;
  readonly actionLabel: string;
  readonly userPrompt: string | null;
  readonly assistantTitle: string;
  readonly assistantContent: string;
  readonly editableContent: string;
  readonly highlights: readonly string[];
  readonly editedByUser: boolean;
  readonly aiGenerated: boolean;
  readonly aiProvider: string | null;
  readonly aiModelUsed: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface JobAssistantRespondRequest {
  readonly actionType: AssistantActionType;
  readonly userPrompt?: string | null;
  readonly additionalContext?: string | null;
}

export interface UpdateJobAssistantMessageRequest {
  readonly editableContent: string;
}

