package com.eurojobcopilot.modules.assistant.ai;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot.ApplicationEvidence;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot.CandidateEvidence;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot.ConversationTurnSummary;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot.MatchEvidence;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot.SponsorshipEvidence;
import com.eurojobcopilot.modules.assistant.entity.AssistantActionType;
import org.springframework.stereotype.Component;

@Component
public class JobAssistantPromptBuilder {

    public static final String TASK_NAME = "ai-job-application-assistant";

    private static final String SYSTEM_PROMPT = """
            You are EuroJob Copilot's contextual European tech career & application assistant embedded on a job listing page.

            CRITICAL GROUNDING & ANTI-HALLUCINATION RULES:
            1. Ground EVERY factual claim strictly in the Verified Candidate Evidence, Job Posting Evidence, Sponsorship Evidence, and Match Evidence provided below.
            2. NEVER invent fake past employers, degrees, certifications, years of experience, spoken languages, or unpossessed skills.
            3. NEVER falsely guarantee visa sponsorship approval or fabricate salary budgets not stated in the job listing or candidate inputs. Clearly separate observed posting facts from general market heuristics/ranges.
            4. When drafting editable user materials (tailored resume bullets, cover letter, recruiter outreach, follow-up email), address the real company/role cleanly using candidate's real name and verified background without fake claims.
            5. Respond ONLY as a single valid JSON object matching the exact schema below. Do NOT wrap output in markdown fences.

            Exact JSON Schema:
            {
              "assistantTitle": "Concise descriptive title for this assistant card",
              "assistantContent": "Well-structured, plain-text or lightweight section-headed response (ready for human editing when drafting text)",
              "highlights": ["3-6 crisp, concrete key takeaways, missing skills, or recommended next actions"]
            }
            """;

    public AiJsonPromptRequest buildPromptRequest(JobAssistantContextSnapshot snapshot) {
        String actionGuidance = resolveActionGuidance(snapshot.actionType());

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Requested Contextual Action: ")
                .append(snapshot.actionType().name())
                .append(" (").append(snapshot.actionType().getLabel()).append(")\n");
        userPrompt.append("Action Instructions: ").append(actionGuidance).append('\n');

        if (hasText(snapshot.userPrompt())) {
            userPrompt.append("Candidate Prompt: ").append(snapshot.userPrompt().trim()).append('\n');
        }
        if (hasText(snapshot.additionalContext())) {
            userPrompt.append("Additional Notes: ").append(snapshot.additionalContext().trim()).append('\n');
        }

        userPrompt.append("\n=== JOB POSTING EVIDENCE ===\n")
                .append(formatJob(snapshot.job())).append('\n');
        userPrompt.append("\n=== SPONSORSHIP & MOBILITY EVIDENCE ===\n")
                .append(formatSponsorship(snapshot.sponsorship())).append('\n');
        userPrompt.append("\n=== COMPATIBILITY MATCH EVIDENCE ===\n")
                .append(formatMatch(snapshot.match())).append('\n');
        userPrompt.append("\n=== VERIFIED CANDIDATE PROFILE & APPROVED MASTER RESUME ===\n")
                .append(formatCandidate(snapshot.candidate())).append('\n');

        if (snapshot.application() != null) {
            userPrompt.append("\n=== APPLICATION TRACKER STATE ===\n")
                    .append(formatApplication(snapshot.application())).append('\n');
        }
        if (snapshot.recentConversation() != null && !snapshot.recentConversation().isEmpty()) {
            userPrompt.append("\n=== PRIOR JOB ASSISTANT HISTORY ===\n")
                    .append(formatConversation(snapshot.recentConversation())).append('\n');
        }

        return new AiJsonPromptRequest(
                TASK_NAME,
                SYSTEM_PROMPT,
                userPrompt.toString(),
                0.35,
                null
        );
    }

    private String resolveActionGuidance(AssistantActionType action) {
        return switch (action) {
            case ANALYZE_JOB ->
                    "Summarize core responsibilities, technical stack priorities, seniority bar, work mode, language expectations, and realistic positioning advice.";
            case EXPLAIN_MATCH_SCORE ->
                    "Explain clearly why the compatibility score turned out this way across skills, seniority, location, visa sponsorship, and salary fit, highlighting strengths and drag factors.";
            case GENERATE_TAILORED_RESUME ->
                    "Draft editable tailored resume summary + 4-6 ATS-aligned experience/project bullets prioritizing this role's requirements, using strictly verified candidate achievements without inventing skills.";
            case GENERATE_COVER_LETTER ->
                    "Draft a complete, authentic European-style cover letter (3-4 concise paragraphs) addressed to the hiring team, linking verified achievements and relocation/visa readiness to this specific role.";
            case IDENTIFY_SKILL_GAPS ->
                    "List exact missing or under-emphasized required/preferred skills, distinguish critical blockers from learnable adjacent gaps, and give concrete mitigation steps before applying.";
            case PREPARE_RECRUITER_MESSAGE ->
                    "Draft two ready-to-edit outreach templates: (1) <= 290-character LinkedIn connection note, and (2) concise 100-word LinkedIn/Email introduction to a recruiter at this company.";
            case GENERATE_FOLLOW_UP_EMAIL ->
                    "Draft a polite, ready-to-send post-application or post-interview follow-up email with a clear subject line tailored to current application stage and role context.";
            case SUGGEST_SALARY_EXPECTATIONS ->
                    "Analyze posted salary (if disclosed) vs candidate minimum expectations, clarify gross vs local European tax/30%-ruling considerations as general guidance only (not tax advice), and suggest a grounded negotiation range & framing script.";
            case EXPLAIN_SPONSORSHIP ->
                    "Explain company sponsor registry verification status, country statutory pathways (e.g., EU Blue Card, NL IND Kennismigrant, DE §18b), and explicitly remind the user of role-level verification caveats.";
            case INTERVIEW_PREP_TOPICS ->
                    "Produce a structured interview preparation plan covering role-specific technical topics (Angular/Java/System Design/TypeScript/RxJS when relevant), behavioral themes, and verified company discussion angles.";
            case CUSTOM_QUESTION ->
                    "Answer the candidate's specific question directly and concisely, strictly grounded in the provided job, sponsorship, match, and verified candidate context.";
        };
    }

    private String formatJob(JobEvidence j) {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(safe(j.title())).append('\n');
        sb.append("Company: ").append(safe(j.companyName())).append('\n');
        sb.append("Location: ").append(join(j.city(), j.country())).append('\n');
        sb.append("Work/Contract/Level: ").append(safe(j.workArrangement())).append(" | ")
                .append(safe(j.employmentType())).append(" | ").append(safe(j.experienceLevel())).append('\n');
        sb.append("Advertised Salary: ").append(j.salaryMin()).append(" - ").append(j.salaryMax())
                .append(' ').append(safe(j.salaryCurrency())).append(" / ").append(safe(j.salaryPeriod())).append('\n');
        sb.append("Job Sponsor Status: ").append(safe(j.sponsorStatus())).append('\n');
        if (j.skills() != null && !j.skills().isEmpty()) {
            sb.append("Listed Skills: ").append(String.join(", ", j.skills())).append('\n');
        }
        if (hasText(j.aiAnalysisSummary())) {
            sb.append("Extracted JD Summary: ").append(truncate(j.aiAnalysisSummary(), 600)).append('\n');
        }
        if (j.aiRequiredSkills() != null && !j.aiRequiredSkills().isEmpty()) {
            sb.append("AI Required Skills: ").append(String.join(", ", j.aiRequiredSkills())).append('\n');
        }
        if (j.aiNiceToHaveSkills() != null && !j.aiNiceToHaveSkills().isEmpty()) {
            sb.append("AI Preferred Skills: ").append(String.join(", ", j.aiNiceToHaveSkills())).append('\n');
        }
        if (j.aiLanguageRequirements() != null && !j.aiLanguageRequirements().isEmpty()) {
            sb.append("Language Requirements: ").append(String.join(", ", j.aiLanguageRequirements())).append('\n');
        }
        if (j.aiResponsibilities() != null && !j.aiResponsibilities().isEmpty()) {
            sb.append("Responsibilities: ").append(String.join("; ", j.aiResponsibilities())).append('\n');
        }
        sb.append("Description Excerpt:\n").append(truncate(j.descriptionExcerpt(), 2200));
        return sb.toString();
    }

    private String formatSponsorship(SponsorshipEvidence s) {
        if (s == null) {
            return "No detailed sponsorship breakdown available.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Detailed Status: ").append(safe(s.detailedStatus())).append(" (Confidence: ")
                .append(safe(s.confidenceLevel())).append(")\n");
        sb.append("Summary: ").append(safe(s.summary())).append('\n');
        if (hasText(s.countryGuideSummary())) {
            sb.append("Country Pathway Guide: ").append(truncate(s.countryGuideSummary(), 500)).append('\n');
        }
        if (hasText(s.vacancyCaveat())) {
            sb.append("Vacancy Caveat: ").append(s.vacancyCaveat()).append('\n');
        }
        if (s.evidenceSignals() != null && !s.evidenceSignals().isEmpty()) {
            sb.append("Evidence Signals: ").append(String.join("; ", s.evidenceSignals())).append('\n');
        }
        if (s.registryRecordNames() != null && !s.registryRecordNames().isEmpty()) {
            sb.append("Registry Matches: ").append(String.join(", ", s.registryRecordNames())).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatMatch(MatchEvidence m) {
        if (m == null) {
            return "Match score has not been calculated yet.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Overall Match Percentage: ").append(m.matchPercentage()).append("%\n");
        sb.append("Deterministic Score: ").append(m.deterministicScore())
                .append(" | AI Score: ").append(m.aiScore()).append('\n');
        sb.append("Experience Compatibility: ").append(safe(m.experienceCompatibility())).append('\n');
        if (m.matchingSkills() != null && !m.matchingSkills().isEmpty()) {
            sb.append("Matching Skills: ").append(String.join(", ", m.matchingSkills())).append('\n');
        }
        if (m.missingSkills() != null && !m.missingSkills().isEmpty()) {
            sb.append("Missing Skills (Rule): ").append(String.join(", ", m.missingSkills())).append('\n');
        }
        if (m.aiMissingSkills() != null && !m.aiMissingSkills().isEmpty()) {
            sb.append("Missing Skills (AI): ").append(String.join(", ", m.aiMissingSkills())).append('\n');
        }
        if (m.aiPotentialConcerns() != null && !m.aiPotentialConcerns().isEmpty()) {
            sb.append("Concerns: ").append(String.join("; ", m.aiPotentialConcerns())).append('\n');
        }
        if (m.recommendationReasons() != null && !m.recommendationReasons().isEmpty()) {
            sb.append("Match Reasons: ").append(String.join("; ", m.recommendationReasons())).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatCandidate(CandidateEvidence c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(safe(c.fullName())).append('\n');
        sb.append("Current Designation: ").append(safe(c.currentDesignation())).append('\n');
        sb.append("Years of Experience: ").append(c.yearsOfExperience()).append('\n');
        sb.append("Location: ").append(safe(c.currentLocation())).append('\n');
        sb.append("Work Authorization: ").append(safe(c.workAuthorizationStatus()))
                .append(" | Relocation: ").append(safe(c.relocationPreference())).append('\n');
        sb.append("Notice Period Days: ").append(c.noticePeriodDays())
                .append(" | Expected Min Salary: ").append(c.expectedSalaryMin())
                .append(' ').append(safe(c.expectedSalaryCurrency())).append('\n');
        sb.append("Requires Sponsorship: ").append(c.requiresSponsorship())
                .append(" | Requires Relocation: ").append(c.requiresRelocation()).append('\n');
        if (c.topSkills() != null && !c.topSkills().isEmpty()) {
            sb.append("Verified Skills: ").append(String.join(", ", c.topSkills())).append('\n');
        }
        if (c.experiencesSummary() != null && !c.experiencesSummary().isEmpty()) {
            sb.append("Work Experience:\n");
            for (String item : c.experiencesSummary()) {
                sb.append("  - ").append(item).append('\n');
            }
        }
        if (c.educationSummary() != null && !c.educationSummary().isEmpty()) {
            sb.append("Education:\n");
            for (String edu : c.educationSummary()) {
                sb.append("  - ").append(edu).append('\n');
            }
        }
        if (hasText(c.approvedMasterResumeTitle())) {
            sb.append("Approved Master CV: ").append(c.approvedMasterResumeTitle()).append('\n');
        }
        if (hasText(c.approvedMasterResumeSummary())) {
            sb.append("Approved Master CV Summary: ").append(truncate(c.approvedMasterResumeSummary(), 600)).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatApplication(ApplicationEvidence a) {
        return "Status: " + safe(a.status())
                + ", AppliedDate: " + safe(a.appliedDate())
                + ", NextInterview: " + safe(a.nextInterviewDate())
                + ", NextFollowUp: " + safe(a.nextFollowUpDate());
    }

    private String formatConversation(java.util.List<ConversationTurnSummary> history) {
        StringBuilder sb = new StringBuilder();
        for (ConversationTurnSummary turn : history) {
            sb.append("- [").append(safe(turn.actionLabel())).append("] ").append(safe(turn.assistantTitle()));
            if (hasText(turn.userPrompt())) {
                sb.append(" (Prompt: ").append(truncate(turn.userPrompt(), 160)).append(')');
            }
            sb.append(": ").append(truncate(turn.editedContentSnippet(), 320)).append('\n');
        }
        return sb.toString().trim();
    }

    private String join(String a, String b) {
        if (hasText(a) && hasText(b)) return a.trim() + ", " + b.trim();
        return hasText(a) ? a.trim() : safe(b);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    private String safe(String v) {
        return hasText(v) ? v.trim() : "Not specified";
    }

    private boolean hasText(String v) {
        return v != null && !v.isBlank();
    }
}

