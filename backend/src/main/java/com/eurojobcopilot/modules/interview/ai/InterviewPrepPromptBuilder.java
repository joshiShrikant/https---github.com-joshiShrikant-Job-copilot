package com.eurojobcopilot.modules.interview.ai;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.interview.entity.InterviewPrepSession;
import com.eurojobcopilot.modules.interview.entity.InterviewQuestion;
import com.eurojobcopilot.modules.interview.entity.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class InterviewPrepPromptBuilder {

    public static final String GENERATE_TASK_NAME = "ai-interview-prep-generate";
    public static final String EVALUATE_TASK_NAME = "ai-interview-answer-evaluate";

    private static final String GENERATION_SYSTEM_PROMPT = """
            You are EuroJob Copilot's European Senior Technical Interview Coach.

            CRITICAL GROUNDING & ANTI-HALLUCINATION RULES:
            1. Tailor questions directly to the Verified Job Posting Evidence, Verified Employer Profile, and Verified Candidate Background below.
            2. Cover the requested categories accurately using the exact allowed enum names:
               JAVA, ANGULAR, SPRING_BOOT, JAVASCRIPT, TYPESCRIPT, RXJS_NGRX, SYSTEM_DESIGN, CODING, DATABASE, BEHAVIORAL, TECHNICAL_LEAD, TECHNICAL.
               Allowed difficulty enum names: ENTRY, MID, SENIOR, STAFF.
            3. For "companyPrepTopics", NEVER invent insider leaks, rumored proprietary interview questions, or unverified company policies. Every topic MUST cite its verified source in "groundingBasis" (e.g., "Verified JD stack & responsibilities", "Company directory industry/country profile").
            4. Write concrete, senior-calibrated questions with thorough, accurate "idealAnswer" explanations and 2-4 crisp "focusTags".
            5. Respond ONLY as a single valid JSON object matching the schema below without markdown fences.

            JSON Schema:
            {
              "sessionTitle": "Concise session title",
              "companyPrepTopics": [
                {
                  "topic": "Focus area grounded in job & company data",
                  "whyRelevant": "Why interviewers for this specific role/company will care",
                  "groundingBasis": "Observed source field backing this topic"
                }
              ],
              "questions": [
                {
                  "category": "ONE_OF_THE_ALLOWED_ENUMS",
                  "difficulty": "ENTRY|MID|SENIOR|STAFF",
                  "questionText": "Full realistic interview question",
                  "idealAnswer": "Comprehensive benchmark technical or STAR answer",
                  "focusTags": ["Tag1", "Tag2"]
                }
              ],
              "overallPrepAdvice": "Brief strategy note grounded in candidate strengths and job gaps",
              "improvementRecommendations": ["2-4 concrete preparation focus steps"]
            }
            """;

    private static final String EVALUATION_SYSTEM_PROMPT = """
            You are EuroJob Copilot's Senior European Engineering Interview Evaluator.

            CRITICAL GRADING RULES:
            1. Grade the candidate's submitted answer objectively from 0 to 100 against the exact question, ideal benchmark answer, role requirements, and difficulty tier.
            2. Highlight genuine "strongPoints" present in the candidate's answer and concrete "missingElements" needed for a stronger answer at the target seniority level.
            3. NEVER fabricate achievements the candidate didn't mention in their answer, and keep feedback constructive, crisp, and actionable.
            4. Respond ONLY as a single valid JSON object matching the schema below without markdown fences.

            JSON Schema:
            {
              "score": 82.5,
              "aiFeedback": "Balanced, detailed coaching feedback referencing exact parts of the user's answer",
              "strongPoints": ["Concrete strength demonstrated in answer"],
              "missingElements": ["Specific missing concept, tradeoff, metric, or edge case"],
              "sessionOverallFeedback": "Updated concise overall session readiness assessment",
              "improvementRecommendations": ["2-4 focused next study topics based on answer gaps"]
            }
            """;

    public AiJsonPromptRequest buildGenerationPrompt(InterviewSessionContextSnapshot ctx) {
        String categories = ctx.requestedCategories().stream()
                .map(QuestionCategory::name)
                .collect(Collectors.joining(", "));

        StringBuilder sb = new StringBuilder();
        sb.append("Generate ").append(ctx.questionCount()).append(" realistic interview questions.\n");
        sb.append("Target Difficulty: ").append(ctx.targetDifficulty().name()).append('\n');
        sb.append("Requested Categories: ").append(categories).append('\n');
        if (hasText(ctx.customSessionTitle())) {
            sb.append("Preferred Session Title: ").append(ctx.customSessionTitle().trim()).append('\n');
        }
        if (hasText(ctx.focusNotes())) {
            sb.append("Candidate Focus Notes: ").append(ctx.focusNotes().trim()).append('\n');
        }

        sb.append("\n=== JOB & VERIFIED EMPLOYER EVIDENCE ===\n");
        sb.append("Role: ").append(safe(ctx.jobTitle())).append('\n');
        sb.append("Company: ").append(safe(ctx.companyName())).append('\n');
        sb.append("Location: ").append(safe(ctx.city())).append(", ").append(safe(ctx.country())).append('\n');
        sb.append("Employer Industry: ").append(safe(ctx.companyIndustry())).append('\n');
        sb.append("Employer Website: ").append(safe(ctx.companyWebsite())).append('\n');
        sb.append("Verified Visa Sponsor Flag: ").append(ctx.companyVerifiedSponsor()).append('\n');
        sb.append("Role Experience Level: ").append(safe(ctx.experienceLevel())).append('\n');
        if (ctx.jobSkills() != null && !ctx.jobSkills().isEmpty()) {
            sb.append("Job Skills: ").append(String.join(", ", ctx.jobSkills())).append('\n');
        }
        if (hasText(ctx.aiJobSummary())) {
            sb.append("Extracted JD Summary: ").append(truncate(ctx.aiJobSummary(), 500)).append('\n');
        }
        if (ctx.aiRequiredSkills() != null && !ctx.aiRequiredSkills().isEmpty()) {
            sb.append("AI Required Skills: ").append(String.join(", ", ctx.aiRequiredSkills())).append('\n');
        }
        if (ctx.aiResponsibilities() != null && !ctx.aiResponsibilities().isEmpty()) {
            sb.append("Key Responsibilities: ").append(String.join("; ", ctx.aiResponsibilities())).append('\n');
        }
        sb.append("Job Description Excerpt:\n").append(truncate(ctx.jobDescription(), 2200)).append('\n');

        sb.append("\n=== VERIFIED CANDIDATE BACKGROUND ===\n");
        sb.append("Designation: ").append(safe(ctx.candidateDesignation())).append('\n');
        sb.append("Years of Experience: ").append(ctx.candidateYearsOfExperience()).append('\n');
        if (ctx.candidateTopSkills() != null && !ctx.candidateTopSkills().isEmpty()) {
            sb.append("Top Skills: ").append(String.join(", ", ctx.candidateTopSkills())).append('\n');
        }
        if (ctx.candidateExperienceHighlights() != null && !ctx.candidateExperienceHighlights().isEmpty()) {
            sb.append("Experience Summary: ").append(String.join(" | ", ctx.candidateExperienceHighlights())).append('\n');
        }
        if (hasText(ctx.masterResumeSummary())) {
            sb.append("Master CV Summary: ").append(truncate(ctx.masterResumeSummary(), 500)).append('\n');
        }

        return new AiJsonPromptRequest(GENERATE_TASK_NAME, GENERATION_SYSTEM_PROMPT, sb.toString(), 0.35, null);
    }

    public AiJsonPromptRequest buildEvaluationPrompt(
            InterviewPrepSession session,
            InterviewQuestion question,
            String userAnswer
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Evaluate candidate answer for session: ").append(safe(session.getSessionTitle())).append('\n');
        sb.append("Role Context: ").append(safe(session.getJob().getTitle()))
                .append(" at ").append(safe(session.getJob().getCompanyName())).append('\n');
        sb.append("Question Category: ").append(question.getCategory().name()).append('\n');
        sb.append("Question Difficulty: ").append(question.getDifficulty().name()).append('\n');
        sb.append("Question: ").append(question.getQuestionText()).append('\n');
        sb.append("Ideal Benchmark Answer:\n").append(safe(question.getIdealAnswer())).append("\n\n");
        sb.append("Candidate Submitted Answer:\n").append(userAnswer.trim()).append('\n');

        return new AiJsonPromptRequest(EVALUATE_TASK_NAME, EVALUATION_SYSTEM_PROMPT, sb.toString(), 0.25, null);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 3) + "...";
    }

    private String safe(String s) {
        return hasText(s) ? s.trim() : "Not specified";
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}

