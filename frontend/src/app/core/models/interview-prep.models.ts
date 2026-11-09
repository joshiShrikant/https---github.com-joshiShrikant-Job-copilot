export type QuestionCategory =
  | 'JAVA'
  | 'ANGULAR'
  | 'SPRING_BOOT'
  | 'JAVASCRIPT'
  | 'TYPESCRIPT'
  | 'RXJS_NGRX'
  | 'SYSTEM_DESIGN'
  | 'CODING'
  | 'DATABASE'
  | 'BEHAVIORAL'
  | 'TECHNICAL_LEAD'
  | 'TECHNICAL';

export type QuestionDifficulty = 'ENTRY' | 'MID' | 'SENIOR' | 'STAFF';

export interface QuestionCategoryOption {
  readonly value: QuestionCategory;
  readonly label: string;
}

export const QUESTION_CATEGORY_OPTIONS: readonly QuestionCategoryOption[] = [
  { value: 'ANGULAR', label: 'Angular' },
  { value: 'TYPESCRIPT', label: 'TypeScript' },
  { value: 'JAVASCRIPT', label: 'JavaScript' },
  { value: 'RXJS_NGRX', label: 'RxJS & NgRx' },
  { value: 'JAVA', label: 'Java' },
  { value: 'SPRING_BOOT', label: 'Spring Boot' },
  { value: 'SYSTEM_DESIGN', label: 'System Design' },
  { value: 'CODING', label: 'Coding & Algorithms' },
  { value: 'DATABASE', label: 'Databases & SQL' },
  { value: 'BEHAVIORAL', label: 'Behavioral (STAR)' },
  { value: 'TECHNICAL_LEAD', label: 'Tech Lead & Architecture' },
  { value: 'TECHNICAL', label: 'General Technical' }
];

export interface QuestionDifficultyOption {
  readonly value: QuestionDifficulty;
  readonly label: string;
}

export const QUESTION_DIFFICULTY_OPTIONS: readonly QuestionDifficultyOption[] = [
  { value: 'ENTRY', label: 'Entry / Junior' },
  { value: 'MID', label: 'Mid-Level' },
  { value: 'SENIOR', label: 'Senior' },
  { value: 'STAFF', label: 'Staff / Principal' }
];

export interface CompanyPrepTopic {
  readonly topic: string;
  readonly whyRelevant: string;
  readonly groundingBasis: string;
}

export interface InterviewQuestion {
  readonly id: number;
  readonly orderIndex: number;
  readonly category: QuestionCategory;
  readonly difficulty: QuestionDifficulty;
  readonly questionText: string;
  readonly idealAnswer: string;
  readonly focusTags: readonly string[];
  readonly userAnswer: string | null;
  readonly aiFeedback: string | null;
  readonly score: number | null;
  readonly strongPoints: readonly string[];
  readonly missingElements: readonly string[];
  readonly answeredAt: string | null;
  readonly evaluationProvider: string | null;
  readonly evaluationModelUsed: string | null;
}

export interface InterviewPrepSessionSummary {
  readonly id: number;
  readonly jobId: number;
  readonly jobTitle: string;
  readonly companyName: string;
  readonly country: string | null;
  readonly sessionTitle: string;
  readonly targetCategories: readonly string[];
  readonly targetDifficulty: QuestionDifficulty;
  readonly totalQuestions: number;
  readonly answeredQuestions: number;
  readonly score: number | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface InterviewPrepSessionDetail {
  readonly id: number;
  readonly jobId: number;
  readonly jobTitle: string;
  readonly companyName: string;
  readonly country: string | null;
  readonly sessionTitle: string;
  readonly targetCategories: readonly string[];
  readonly targetDifficulty: QuestionDifficulty;
  readonly totalQuestions: number;
  readonly answeredQuestions: number;
  readonly score: number | null;
  readonly overallFeedback: string | null;
  readonly companyPrepTopics: readonly CompanyPrepTopic[];
  readonly improvementRecommendations: readonly string[];
  readonly aiProvider: string | null;
  readonly aiModelUsed: string | null;
  readonly questions: readonly InterviewQuestion[];
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface CreateInterviewPrepSessionRequest {
  readonly jobId: number;
  readonly sessionTitle?: string | null;
  readonly categories?: readonly QuestionCategory[];
  readonly difficulty?: QuestionDifficulty | null;
  readonly questionCount?: number;
  readonly focusNotes?: string | null;
}

export interface SubmitInterviewAnswerRequest {
  readonly questionId: number;
  readonly userAnswer: string;
}

export interface CategoryPerformanceStat {
  readonly category: QuestionCategory;
  readonly totalQuestions: number;
  readonly answeredQuestions: number;
  readonly averageScore: number | null;
}

export interface DifficultyPerformanceStat {
  readonly difficulty: QuestionDifficulty;
  readonly totalQuestions: number;
  readonly answeredQuestions: number;
  readonly averageScore: number | null;
}

export interface InterviewSessionScorePoint {
  readonly sessionId: number;
  readonly sessionTitle: string;
  readonly companyName: string;
  readonly score: number | null;
  readonly answeredQuestions: number;
  readonly totalQuestions: number;
  readonly createdAt: string;
}

export interface InterviewPerformanceHistoryResponse {
  readonly totalSessions: number;
  readonly totalQuestions: number;
  readonly totalAnsweredQuestions: number;
  readonly averageScore: number | null;
  readonly categoryStats: readonly CategoryPerformanceStat[];
  readonly difficultyStats: readonly DifficultyPerformanceStat[];
  readonly recentScoreTrend: readonly InterviewSessionScorePoint[];
  readonly weakestCategories: readonly string[];
  readonly improvementRecommendations: readonly string[];
  readonly sessions: readonly InterviewPrepSessionSummary[];
}

export function formatQuestionCategoryLabel(category: QuestionCategory | string): string {
  const found = QUESTION_CATEGORY_OPTIONS.find(opt => opt.value === category);
  return found?.label ?? category;
}

