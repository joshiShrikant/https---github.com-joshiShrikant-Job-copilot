package com.eurojobcopilot.modules.recruiter.ai;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot.CandidateEvidence;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot.ExperienceItem;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot.JobAndApplicationEvidence;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot.RecruiterEvidence;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import org.springframework.stereotype.Component;

@Component
public class RecruiterOutreachPromptBuilder {

    public static final String TASK_NAME = "ai-recruiter-outreach-draft";

    private static final String SYSTEM_PROMPT = """
            You are a senior European tech career advisor drafting authentic, respectful recruiter communications on behalf of a candidate for human review BEFORE the user chooses to copy or send them.

            CRITICAL ANTI-HALLUCINATION & ACCURACY RULES:
            1. Cite ONLY employers, titles, years of experience, skills, visa/work authorization status, and notice period explicitly supported in Verified Candidate Evidence.
            2. NEVER invent fake achievements, unpossessed technologies, fake referrals, or imaginary prior conversations.
            3. NEVER falsely claim EU/EEA citizenship when Candidate Evidence indicates sponsorship is required. Mention sponsorship/relocation readiness constructively only when helpful.
            4. NEVER use bracketed template placeholders like [Your Name], [Recruiter Name], or [Company] when real names are supplied in context. Always address the recruiter cleanly (e.g., first name) and sign off using the candidate's real name (except ultra-short LinkedIn connection invites where first name or full name fits naturally).
            5. Tailor structure strictly to MessageType and Channel:
               - LINKEDIN_CONNECTION: Concise, warm LinkedIn connection request note STRICTLY <= 290 characters (including spaces and greeting). No subjectLine (set null). Mention target role/team and 1 genuine overlap point.
               - INITIAL_OUTREACH / OUTREACH: Recruiter introduction message (80-150 words). Concise opening linking candidate's top domain fit to target employer/role, explicit visa/relocation clarity if cross-border, and polite call to connect.
               - FOLLOW_UP: Polite application follow-up (70-130 words). References the application timeline/stage naturally without sounding pushy, reiterates 1 high-signal qualification, and offers interview availability.
               - POST_INTERVIEW_THANKS: Warm interview thank-you message (90-150 words). Expresses sincere appreciation for the team's time, incorporates any Candidate Personal Context discussion topics, and reaffirms enthusiasm.
               - OFFER_NEGOTIATION: Diplomatic compensation/relocation/offer clarification message (90-160 words) grounded in verified context.
            6. Include a clear professional "subjectLine" when Channel is EMAIL (or null when Channel is LINKEDIN for LINKEDIN_CONNECTION invites).
            7. Return ONLY a single valid JSON object with NO markdown fences and NO prose outside JSON.

            Exact JSON Schema:
            {
              "subjectLine": "Concise professional subject line or null for short LinkedIn invites",
              "messageBody": "Complete ready-to-review message body",
              "keyTalkingPoints": ["2-3 verified talking points emphasized in this message"],
              "suggestedFollowUpDays": 5,
              "warnings": ["Optional grounding notice if context/role link is missing or incomplete"]
            }
            """;

    public AiJsonPromptRequest buildPromptRequest(RecruiterOutreachContextSnapshot snapshot) {
        String candidateBlock = formatCandidate(snapshot.candidate());
        String recruiterBlock = formatRecruiter(snapshot.recruiter());
        String jobBlock = formatJobAndApplication(snapshot.jobContext());
        String personalNotes = hasText(snapshot.personalContext())
                ? snapshot.personalContext().trim()
                : "None supplied; keep messaging grounded in verified experience, European work/relocation readiness, and role fit.";

        CommunicationChannel channel = snapshot.channel() != null ? snapshot.channel() : CommunicationChannel.LINKEDIN;
        RecruiterMessageType type = snapshot.messageType() != null ? snapshot.messageType() : RecruiterMessageType.INITIAL_OUTREACH;

        String userPrompt = """
                Draft a reviewable, editable recruiter outreach message.

                Target Channel: %s
                Message Type: %s
                Candidate Personal Context / Specific Discussion Notes: %s

                === RECRUITER & EMPLOYER CONTEXT ===
                %s

                === TARGET ROLE & APPLICATION TIMELINE ===
                %s

                === VERIFIED CANDIDATE EVIDENCE ===
                %s
                """.formatted(channel.name(), type.name(), personalNotes, recruiterBlock, jobBlock, candidateBlock);

        return new AiJsonPromptRequest(
                TASK_NAME,
                SYSTEM_PROMPT,
                userPrompt,
                0.35,
                null
        );
    }

    private String formatRecruiter(RecruiterEvidence r) {
        StringBuilder sb = new StringBuilder();
        sb.append("  Recruiter Name: ").append(safe(r.fullName())).append('\n');
        sb.append("  Recruiter Title: ").append(safe(r.roleTitle())).append('\n');
        sb.append("  Company: ").append(safe(r.companyName())).append('\n');
        sb.append("  Verified Visa Sponsor Employer: ").append(r.verifiedSponsorCompany() ? "Yes" : "No / Unconfirmed").append('\n');
        if (hasText(r.companyIndustry())) {
            sb.append("  Industry: ").append(r.companyIndustry().trim()).append('\n');
        }
        if (r.companyTechStack() != null && !r.companyTechStack().isEmpty()) {
            sb.append("  Company Stack: ").append(String.join(", ", r.companyTechStack())).append('\n');
        }
        if (hasText(r.notes())) {
            sb.append("  Candidate Notes About Recruiter: ").append(truncate(r.notes().trim(), 500)).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatJobAndApplication(JobAndApplicationEvidence j) {
        if (j == null) {
            return "  No specific tracked application or job attached; draft outreach tailored to the recruiter's company generally.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("  Job Title: ").append(safe(j.jobTitle())).append('\n');
        sb.append("  Company: ").append(safe(j.companyName())).append('\n');
        sb.append("  Location: ").append(hasText(j.location()) ? j.location() : safe(j.country())).append('\n');
        if (j.applicationStatus() != null) {
            sb.append("  Current Application Stage: ").append(j.applicationStatus().name()).append('\n');
        }
        if (j.appliedDate() != null) {
            sb.append("  Applied Date: ").append(j.appliedDate()).append('\n');
        }
        if (j.nextInterviewDate() != null) {
            sb.append("  Scheduled/Recent Interview Date: ").append(j.nextInterviewDate()).append('\n');
        }
        sb.append("  Job Visa Sponsor Status: ").append(safe(j.sponsorStatus())).append('\n');
        if (j.jobSkills() != null && !j.jobSkills().isEmpty()) {
            sb.append("  Role Listed Skills: ").append(String.join(", ", j.jobSkills())).append('\n');
        }
        if (hasText(j.jdSummary())) {
            sb.append("  Role Summary: ").append(truncate(j.jdSummary().trim(), 700)).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatCandidate(CandidateEvidence c) {
        StringBuilder sb = new StringBuilder();
        sb.append("  Candidate Name: ").append(safe(c.fullName())).append('\n');
        sb.append("  Current Designation: ").append(safe(c.currentDesignation())).append('\n');
        sb.append("  Years Experience: ").append(c.yearsOfExperience() != null ? c.yearsOfExperience() + " years" : "Not specified").append('\n');
        sb.append("  Location: ").append(joinNonBlank(c.currentCity(), c.currentCountry())).append('\n');
        sb.append("  Work Authorization: ").append(safe(c.workAuthorizationStatus())).append('\n');
        sb.append("  Relocation Readiness: ").append(safe(c.relocationPreference())).append('\n');
        if (c.noticePeriodDays() != null) {
            sb.append("  Notice Period: ").append(c.noticePeriodDays()).append(" days\n");
        }
        sb.append("  Requires Sponsorship: ").append(c.requiresSponsorship()).append(", Requires Relocation: ").append(c.requiresRelocation()).append('\n');
        if (hasText(c.bio())) {
            sb.append("  Bio: ").append(truncate(c.bio().trim(), 500)).append('\n');
        }
        if (c.topSkills() != null && !c.topSkills().isEmpty()) {
            sb.append("  Verified Skills: ").append(String.join(", ", c.topSkills())).append('\n');
        }
        if (c.workExperiences() != null && !c.workExperiences().isEmpty()) {
            sb.append("  Verified Experience Highlights:\n");
            for (ExperienceItem exp : c.workExperiences()) {
                sb.append("    - ").append(safe(exp.designation())).append(" at ").append(safe(exp.companyName()));
                if (hasText(exp.summary())) {
                    sb.append(": ").append(truncate(exp.summary().trim(), 240));
                }
                sb.append('\n');
            }
        }
        if (hasText(c.masterResumeSummary())) {
            sb.append("  Approved Master CV Summary: ").append(truncate(c.masterResumeSummary().trim(), 500)).append('\n');
        }
        return sb.toString().trim();
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text != null ? text : "";
        }
        return text.substring(0, maxChars).trim() + "...";
    }

    private String joinNonBlank(String a, String b) {
        if (hasText(a) && hasText(b)) {
            return a.trim() + ", " + b.trim();
        }
        return hasText(a) ? a.trim() : safe(b);
    }

    private String safe(String value) {
        return hasText(value) ? value.trim() : "Not specified";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

