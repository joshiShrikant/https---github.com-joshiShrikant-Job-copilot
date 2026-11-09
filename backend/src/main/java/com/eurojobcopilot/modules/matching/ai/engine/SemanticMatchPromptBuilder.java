package com.eurojobcopilot.modules.matching.ai.engine;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.config.AiProperties;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.EducationEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.JobAiAnalysisEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.PreferenceEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.ProfileEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.ResumeEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.SkillEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.WorkExperienceEvidence;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Constructs grounded, schema-constrained semantic comparison prompts across candidate profile,
 * master resume, skills, work history, projects, education, and job description responsibilities.
 */
@Component
@RequiredArgsConstructor
public class SemanticMatchPromptBuilder {

    public static final String TASK_NAME = "semantic-job-matching";

    private static final String SYSTEM_PROMPT = """
            You are a rigorous European career advisor and technical hiring evaluator.
            Perform a semantic compatibility comparison between the Candidate Evidence and Target Role Specification provided below.
            Evaluate fit across ALL of these dimensions:
            1. User professional profile (designation, total years, bio, work authorization, location & salary alignment)
            2. Master resume (summary, parsed experience, projects, responsibilities if provided)
            3. Technical skills (core stack depth, proficiency, years of experience vs required/nice-to-have skills)
            4. Work experience & seniority trajectory
            5. Projects & concrete engineering deliverables
            6. Role responsibilities & domain/language demands vs the Job Description

            CRITICAL ANTI-HALLUCINATION RULES:
            - Evaluate ONLY facts explicitly present in the Candidate Evidence section below.
            - NEVER invent, fabricate, infer, or assume unstated skills, years of experience, projects, degrees, certifications, employers, or EU work authorization.
            - If the Master Resume has no parsed structured text or if Projects are absent, state that clearly as unverified or suggest adding them—NEVER claim the candidate completed unstated projects or holds unstated skills.
            - Return ONLY a single valid JSON object with NO markdown fences and NO surrounding prose.

            Return strictly these JSON keys:
            - "aiScore": number from 0 to 100 (e.g. 78.5) representing genuine semantic role fit based strictly on verified evidence
            - "strongMatchingAreas": array of concise evidence-backed strings explaining concrete alignments between candidate profile/skills/experience/projects/resume and role responsibilities ([] if none)
            - "missingSkills": array of specific technical, architectural, tooling, or language skills required/valued by the job that are missing or unverified in candidate evidence ([] if none)
            - "experienceGaps": array of specific seniority, years, domain scale, ownership, or responsibility gaps ([] if none)
            - "potentialConcerns": array of practical risks or concerns (e.g. visa sponsorship risk, relocation mismatch, missing master resume details, language barriers) ([] if none)
            - "improvementSuggestions": array of truthful, concrete actions the candidate can take (resume framing of existing work, portfolio projects to build, skills to close) without fabricating credentials ([] if none)
            - "rationale": concise 2-4 sentence executive explanation comparing verified candidate evidence against job requirements
            """;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiJsonPromptRequest buildPromptRequest(SemanticMatchContextSnapshot snapshot) {
        int totalBudget = Math.max(2500, aiProperties.getMaxInputChars());
        int candidateBudget = Math.max(1200, (int) (totalBudget * 0.48));
        int jobBudget = Math.max(1200, totalBudget - candidateBudget);

        String candidateSection = truncateSection(formatCandidateSection(snapshot), candidateBudget);
        String jobSection = truncateSection(formatJobSection(snapshot), jobBudget);

        String userPrompt = """
                Compare the verified Candidate Evidence against the Target Role Specification and return the JSON assessment.

                === CANDIDATE EVIDENCE ===
                %s

                === TARGET ROLE SPECIFICATION ===
                %s
                """.formatted(candidateSection, jobSection);

        return new AiJsonPromptRequest(
                TASK_NAME,
                SYSTEM_PROMPT,
                userPrompt,
                0.15,
                null
        );
    }

    private String formatCandidateSection(SemanticMatchContextSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();

        sb.append("1. Professional Profile:\n");
        ProfileEvidence p = snapshot.profile();
        if (p == null) {
            sb.append("  Not provided.\n");
        } else {
            appendLine(sb, "Candidate Name", safeValue(p.displayName()));
            appendLine(sb, "Current Designation", safeValue(p.currentDesignation()));
            appendLine(sb, "Years of Experience", p.yearsOfExperience() != null ? p.yearsOfExperience() + " years" : "Not specified");
            appendLine(sb, "Current Location", joinNonBlank(p.currentCity(), p.currentCountry()));
            appendLine(sb, "Work Authorization", safeValue(p.workAuthorizationStatus()));
            appendLine(sb, "Relocation Preference", safeValue(p.relocationPreference()));
            appendLine(sb, "Expected Minimum Salary", p.expectedSalaryMin() != null
                    ? p.expectedSalaryMin() + " " + safeValue(p.expectedSalaryCurrency())
                    : "Not specified");
            appendLine(sb, "Notice Period", p.noticePeriodDays() != null ? p.noticePeriodDays() + " days" : "Not specified");
            appendLine(sb, "Portfolio / Code Links Provided",
                    "LinkedIn=" + yesNo(hasText(p.linkedinUrl()))
                            + ", GitHub=" + yesNo(hasText(p.githubUrl()))
                            + ", Portfolio=" + yesNo(hasText(p.portfolioUrl())));
            appendLine(sb, "Professional Bio / Summary", safeValue(p.bio()));
        }

        PreferenceEvidence pref = snapshot.preference();
        sb.append("\n2. Target Search Preferences:\n");
        if (pref == null) {
            sb.append("  Not configured.\n");
        } else {
            appendLine(sb, "Target Titles", joinList(pref.preferredJobTitles()));
            appendLine(sb, "Target Countries", joinList(pref.preferredCountries()));
            appendLine(sb, "Target Cities", joinList(pref.preferredCities()));
            appendLine(sb, "Preferred Work Arrangement", safeValue(pref.workArrangement()));
            appendLine(sb, "Requires Visa Sponsorship", String.valueOf(pref.requiresSponsorship()));
            appendLine(sb, "English Speaking Roles Only", String.valueOf(pref.englishSpeakingOnly()));
        }

        sb.append("\n3. Verified Technical Skills:\n");
        if (snapshot.skills().isEmpty()) {
            sb.append("  None listed in profile.\n");
        } else {
            for (SkillEvidence skill : snapshot.skills()) {
                sb.append("  - ").append(safeValue(skill.skillName()))
                        .append(" (")
                        .append(skill.yearsExperience() != null ? skill.yearsExperience() + " yrs" : "duration unset")
                        .append(", ").append(safeValue(skill.proficiencyLevel()))
                        .append(skill.primary() ? ", Primary" : "")
                        .append(")\n");
            }
        }

        sb.append("\n4. Work Experience & Role Deliverables:\n");
        if (snapshot.workExperiences().isEmpty()) {
            sb.append("  None listed in profile.\n");
        } else {
            for (WorkExperienceEvidence exp : snapshot.workExperiences()) {
                String endLabel = exp.current() ? "Present"
                        : (exp.endDate() != null ? exp.endDate().toString() : "Unspecified");
                sb.append("  - ").append(safeValue(exp.designation()))
                        .append(" at ").append(safeValue(exp.companyName()))
                        .append(" [").append(exp.startDate() != null ? exp.startDate() : "Unspecified")
                        .append(" to ").append(endLabel).append("]");
                if (hasText(exp.location())) {
                    sb.append(" (").append(exp.location().trim()).append(")");
                }
                sb.append('\n');
                if (!exp.skillsUsed().isEmpty()) {
                    sb.append("    Skills used: ").append(String.join(", ", exp.skillsUsed())).append('\n');
                }
                if (hasText(exp.description())) {
                    sb.append("    Responsibilities & Projects: ").append(exp.description().trim()).append('\n');
                }
            }
        }

        sb.append("\n5. Education:\n");
        if (snapshot.educations().isEmpty()) {
            sb.append("  None listed.\n");
        } else {
            for (EducationEvidence edu : snapshot.educations()) {
                sb.append("  - ").append(safeValue(edu.degree()))
                        .append(" in ").append(safeValue(edu.fieldOfStudy()))
                        .append(", ").append(safeValue(edu.institution()))
                        .append(" (").append(safeValue(edu.country())).append(")\n");
            }
        }

        sb.append("\n6. Master Resume & Parsed Projects/Highlights:\n");
        ResumeEvidence resume = snapshot.masterResume();
        if (resume == null) {
            sb.append("  No master resume uploaded.\n");
        } else {
            appendLine(sb, "Resume Title", safeValue(resume.title()));
            appendLine(sb, "File Name", safeValue(resume.fileName()));
            appendLine(sb, "Version", "v" + (resume.versionNumber() != null ? resume.versionNumber() : 1)
                    + (resume.masterApproved() ? " (Master Approved)" : " (Not Yet Master Approved)"));
            Map<String, Object> parsed = resume.parsedContentJson();
            if (parsed == null || parsed.isEmpty()) {
                sb.append("  Parsed Resume Content: Not yet extracted from document file.\n");
            } else {
                appendLine(sb, "Parsed Resume JSON", serializeParsedJson(parsed));
            }
        }

        return sb.toString().trim();
    }

    private String formatJobSection(SemanticMatchContextSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        JobEvidence job = snapshot.job();
        if (job != null) {
            appendLine(sb, "Title", safeValue(job.title()));
            appendLine(sb, "Company", safeValue(job.companyName()));
            if (hasText(job.companyIndustry())) {
                appendLine(sb, "Company Industry", job.companyIndustry());
            }
            appendLine(sb, "Employer Verified Visa Sponsor",
                    job.companyVerifiedSponsor()
                            ? "Yes (" + safeValue(job.companySponsorConfidence()) + ")"
                            : "Unverified / " + safeValue(job.sponsorStatus()));
            appendLine(sb, "Country", safeValue(job.country()));
            appendLine(sb, "Location", hasText(job.location()) ? job.location() : joinNonBlank(job.city(), job.country()));
            appendLine(sb, "Work Arrangement", safeValue(job.workArrangement()));
            appendLine(sb, "Employment Type", safeValue(job.employmentType()));
            appendLine(sb, "Seniority / Min Experience",
                    safeValue(job.experienceLevel())
                            + (job.minExperienceYears() != null ? " (" + job.minExperienceYears() + "+ years)" : ""));
            if (job.salaryMin() != null || job.salaryMax() != null) {
                appendLine(sb, "Advertised Salary",
                        (job.salaryMin() != null ? job.salaryMin() : "?") + " - "
                                + (job.salaryMax() != null ? job.salaryMax() : "?") + " "
                                + safeValue(job.salaryCurrency()) + " (" + safeValue(job.salaryPeriod()) + ")");
            }
            appendLine(sb, "Listed Technical Skills", joinList(job.skills()));
        }

        JobAiAnalysisEvidence aiJd = snapshot.jobAiAnalysis();
        if (aiJd != null) {
            sb.append("\nStructured Role Intelligence (Extracted JD):\n");
            appendLine(sb, "Required Experience Summary", safeValue(aiJd.requiredExperienceSummary()));
            appendLine(sb, "Extracted Required Skills", joinList(aiJd.requiredSkills()));
            appendLine(sb, "Extracted Nice-to-Have Skills", joinList(aiJd.niceToHaveSkills()));
            if (!aiJd.responsibilities().isEmpty()) {
                sb.append("  Key Responsibilities:\n");
                for (String resp : aiJd.responsibilities()) {
                    sb.append("    * ").append(resp).append('\n');
                }
            }
            appendLine(sb, "Language Requirements", joinList(aiJd.languageRequirements()));
            appendLine(sb, "Visa Sponsorship Notes", safeValue(aiJd.visaSponsorshipAnalysis()));
            appendLine(sb, "Relocation Notes", safeValue(aiJd.relocationAnalysis()));
        }

        if (job != null && hasText(job.sanitizedDescription())) {
            sb.append("\nFull Job Description Text:\n");
            sb.append(job.sanitizedDescription().trim()).append('\n');
        }

        return sb.toString().trim();
    }

    private String serializeParsedJson(Map<String, Object> parsed) {
        try {
            return objectMapper.writeValueAsString(parsed);
        } catch (JsonProcessingException ex) {
            return parsed.toString();
        }
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        sb.append("  ").append(label).append(": ").append(value).append('\n');
    }

    private String truncateSection(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content != null ? content : "";
        }
        return content.substring(0, maxChars).trim() + "\n[TRUNCATED]";
    }

    private String safeValue(String value) {
        return hasText(value) ? value.trim() : "Not specified";
    }

    private String joinList(List<String> items) {
        return items == null || items.isEmpty() ? "None specified" : String.join(", ", items);
    }

    private String joinNonBlank(String first, String second) {
        boolean hasFirst = hasText(first);
        boolean hasSecond = hasText(second);
        if (hasFirst && hasSecond) {
            return first.trim() + ", " + second.trim();
        }
        if (hasFirst) {
            return first.trim();
        }
        if (hasSecond) {
            return second.trim();
        }
        return "Not specified";
    }

    private String yesNo(boolean flag) {
        return flag ? "Provided" : "Not provided";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

