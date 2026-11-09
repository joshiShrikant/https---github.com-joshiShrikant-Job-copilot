package com.eurojobcopilot.modules.resume.tailored;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.config.AiProperties;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeEducationDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProjectDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeSkillDto;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.EducationEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.HeaderEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.JobAiAnalysisEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.MasterResumeEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.SkillEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.WorkExperienceEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Constructs schema-constrained, anti-hallucination prompts for tailoring a candidate's
 * verified master CV and profile to a target European engineering job description.
 */
@Component
@RequiredArgsConstructor
public class TailoredResumePromptBuilder {

    public static final String TASK_NAME = "jd-tailored-resume-generation";

    private static final String SYSTEM_PROMPT = """
            You are a principal European technical resume strategist and ATS optimization specialist.
            Tailor the Candidate Evidence below into a compelling, factual resume draft targeted to the Target Job Specification and selected Template Style.

            CRITICAL ANTI-HALLUCINATION RULES (ZERO TOLERANCE):
            1. Use ONLY employers, job titles, dates, projects, degrees, certifications, and skills explicitly supported by Candidate Evidence.
            2. NEVER invent companies, titles, promotions, dates, metrics not implied/supported by original text, degrees, certifications, or unpossessed technologies.
            3. Every entry in "optimizedExperiences" MUST correspond to a real employer from Candidate Evidence. You may sharpen phrasing and highlight JD-relevant aspects of actual work performed, but NEVER add tools the candidate did not use.
            4. Every skill in "highlightedSkills" or "additionalSkills" MUST be verified in Candidate Evidence.
            5. Every required or desired skill/keyword from the Target Job that is NOT verified in Candidate Evidence MUST be placed strictly in "missingKeywords" (and NEVER inserted into "highlightedSkills" or experience bullets).
            6. Respect the template style hint:
               - MODERN: scannable outcome-first summary & achievement bullets
               - MINIMAL_ATS: maximum clean ATS keyword clarity & chronological precision
               - EUROPASS: structured, explicit European credential/competency phrasing
               - CLASSIC: formal executive engineering CV register
            7. Return ONLY a single valid JSON object with NO markdown fences and NO surrounding prose.

            Exact JSON Schema:
            {
              "headline": concise role-aligned professional headline grounded in candidate's real level/domain,
              "tailoredSummary": 3-4 sentence executive summary connecting verified candidate background to target job needs,
              "highlightedSkills": [verified candidate skills most aligned to the job, ordered highest relevance first],
              "additionalSkills": [remaining verified candidate skills],
              "missingKeywords": [job skills/keywords absent from candidate evidence],
              "tailoringRationale": concise explanation of emphasis choices made for this target job,
              "optimizedExperiences": [
                {
                  "companyName": exact employer name from Candidate Evidence,
                  "designation": exact role title from Candidate Evidence,
                  "location": string or null,
                  "startDate": "YYYY-MM-DD" or null,
                  "endDate": "YYYY-MM-DD" or null,
                  "current": boolean,
                  "tailoredBullets": [2-5 crisp, ATS-aligned achievement bullets grounded strictly in that role's evidence],
                  "matchedKeywords": [target job keywords authentically evidenced in this role],
                  "relevanceScore": integer 0..100
                }
              ],
              "projects": [
                {
                  "name": exact project name from Candidate Evidence (if candidate has projects),
                  "description": tailored concise summary grounded in verified project facts,
                  "tailoredBullets": [1-3 concise factual bullets],
                  "technologies": [verified project technologies],
                  "url": verified url or null
                }
              ],
              "warnings": [optional factual caveat or gap notes]
            }
            """;

    private final AiProperties aiProperties;

    public AiJsonPromptRequest buildPromptRequest(TailoredResumeContextSnapshot snapshot) {
        int totalBudget = Math.max(3000, aiProperties.getMaxInputChars());
        int candidateBudget = Math.max(1500, (int) (totalBudget * 0.52));
        int jobBudget = Math.max(1200, totalBudget - candidateBudget);

        String candidateSection = truncateSection(formatCandidateSection(snapshot), candidateBudget);
        String jobSection = truncateSection(formatJobSection(snapshot), jobBudget);

        ResumeTemplate template = snapshot.templateName() != null ? snapshot.templateName() : ResumeTemplate.MODERN;
        String focus = hasText(snapshot.tailoringFocus()) ? snapshot.tailoringFocus().trim() : "Balanced full-role alignment";

        String userPrompt = """
                Tailor the Candidate Evidence for the Target Job Specification below.

                Template Style: %s
                Requested Tailoring Focus: %s

                === CANDIDATE EVIDENCE ===
                %s

                === TARGET JOB SPECIFICATION ===
                %s
                """.formatted(template.name(), focus, candidateSection, jobSection);

        return new AiJsonPromptRequest(
                TASK_NAME,
                SYSTEM_PROMPT,
                userPrompt,
                0.2,
                null
        );
    }

    private String formatCandidateSection(TailoredResumeContextSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();

        sb.append("1. Verified Candidate Header:\n");
        HeaderEvidence h = snapshot.header();
        if (h == null) {
            sb.append("  Not provided.\n");
        } else {
            appendLine(sb, "Full Name", safeValue(h.fullName()));
            appendLine(sb, "Current Designation", safeValue(h.currentDesignation()));
            appendLine(sb, "Years of Experience", h.yearsOfExperience() != null ? h.yearsOfExperience() + " years" : "Not specified");
            appendLine(sb, "Location", joinNonBlank(h.currentCity(), h.currentCountry()));
            appendLine(sb, "Work Authorization", safeValue(h.workAuthorizationStatus()));
            appendLine(sb, "Relocation Preference", safeValue(h.relocationPreference()));
            appendLine(sb, "Bio / Summary", safeValue(h.bio()));
        }

        sb.append("\n2. Verified Skills (Profile):\n");
        if (snapshot.skills().isEmpty()) {
            sb.append("  None in profile skills.\n");
        } else {
            for (SkillEvidence s : snapshot.skills()) {
                sb.append("  - ").append(safeValue(s.skillName()))
                        .append(s.yearsExperience() != null ? " (" + s.yearsExperience() + " yrs" : " (duration unset")
                        .append(s.proficiencyLevel() != null ? ", " + s.proficiencyLevel() : "")
                        .append(s.primary() ? ", Primary" : "")
                        .append(")\n");
            }
        }

        sb.append("\n3. Verified Work Experiences (Profile):\n");
        if (snapshot.workExperiences().isEmpty()) {
            sb.append("  None in profile work history.\n");
        } else {
            for (WorkExperienceEvidence exp : snapshot.workExperiences()) {
                String endLabel = exp.current() ? "Present" : (exp.endDate() != null ? exp.endDate().toString() : "Unspecified");
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
                    sb.append("    Original Description: ").append(exp.description().trim()).append('\n');
                }
            }
        }

        sb.append("\n4. Verified Educations:\n");
        if (snapshot.educations().isEmpty()) {
            sb.append("  None in profile educations.\n");
        } else {
            for (EducationEvidence edu : snapshot.educations()) {
                sb.append("  - ").append(safeValue(edu.degree()))
                        .append(" in ").append(safeValue(edu.fieldOfStudy()))
                        .append(", ").append(safeValue(edu.institution()))
                        .append(" (").append(safeValue(edu.country())).append(")\n");
            }
        }

        sb.append("\n5. Master Resume Details:\n");
        MasterResumeEvidence mr = snapshot.masterResume();
        if (mr != null) {
            appendLine(sb, "Master CV Title", safeValue(mr.title()));
            appendLine(sb, "Version", "v" + (mr.versionNumber() != null ? mr.versionNumber() : 1)
                    + (mr.masterApproved() ? " (Approved)" : " (Draft/Unapproved)"));
            ParsedResumeJsonDto parsed = mr.parsedResume();
            if (parsed != null) {
                if (hasText(parsed.getSummary())) {
                    appendLine(sb, "Master CV Summary", parsed.getSummary());
                }
                if (parsed.getSkills() != null && !parsed.getSkills().isEmpty()) {
                    List<String> names = parsed.getSkills().stream()
                            .map(ParsedResumeSkillDto::getSkillName)
                            .filter(TailoredResumePromptBuilder::hasText)
                            .toList();
                    appendLine(sb, "Parsed CV Skills", String.join(", ", names));
                }
                if (parsed.getExperiences() != null && !parsed.getExperiences().isEmpty()) {
                    sb.append("  Parsed CV Experiences:\n");
                    for (ParsedResumeExperienceDto pe : parsed.getExperiences()) {
                        sb.append("    * ").append(safeValue(pe.getDesignation()))
                                .append(" at ").append(safeValue(pe.getCompanyName()))
                                .append(" [").append(pe.getStartDate() != null ? pe.getStartDate() : "?")
                                .append(" to ").append(pe.isCurrent() ? "Present" : (pe.getEndDate() != null ? pe.getEndDate() : "?"))
                                .append("]\n");
                        if (hasText(pe.getDescription())) {
                            sb.append("      Details: ").append(pe.getDescription().trim()).append('\n');
                        }
                    }
                }
                if (parsed.getEducations() != null && !parsed.getEducations().isEmpty()) {
                    sb.append("  Parsed CV Educations:\n");
                    for (ParsedResumeEducationDto ped : parsed.getEducations()) {
                        sb.append("    * ").append(safeValue(ped.getDegree()))
                                .append(" in ").append(safeValue(ped.getFieldOfStudy()))
                                .append(" - ").append(safeValue(ped.getInstitution())).append('\n');
                    }
                }
                if (parsed.getProjects() != null && !parsed.getProjects().isEmpty()) {
                    sb.append("  Parsed CV Projects:\n");
                    for (ParsedResumeProjectDto proj : parsed.getProjects()) {
                        sb.append("    * ").append(safeValue(proj.getName()));
                        if (proj.getTechnologies() != null && !proj.getTechnologies().isEmpty()) {
                            sb.append(" [").append(String.join(", ", proj.getTechnologies())).append("]");
                        }
                        if (hasText(proj.getUrl())) {
                            sb.append(" (").append(proj.getUrl()).append(")");
                        }
                        sb.append('\n');
                        if (hasText(proj.getDescription())) {
                            sb.append("      Description: ").append(proj.getDescription().trim()).append('\n');
                        }
                    }
                }
                if (parsed.getLanguages() != null && !parsed.getLanguages().isEmpty()) {
                    appendLine(sb, "Languages", String.join(", ", parsed.getLanguages()));
                }
                if (parsed.getCertifications() != null && !parsed.getCertifications().isEmpty()) {
                    appendLine(sb, "Certifications", String.join(", ", parsed.getCertifications()));
                }
            }
            if (hasText(mr.rawExtractedText())) {
                sb.append("  Extracted Raw Master Resume Text:\n");
                sb.append(truncateSection(mr.rawExtractedText(), 6000)).append('\n');
            }
        }

        return sb.toString().trim();
    }

    private String formatJobSection(TailoredResumeContextSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        JobEvidence j = snapshot.job();
        if (j != null) {
            appendLine(sb, "Job Title", safeValue(j.title()));
            appendLine(sb, "Company", safeValue(j.companyName()));
            appendLine(sb, "Location", hasText(j.location()) ? j.location() : joinNonBlank(j.city(), j.country()));
            appendLine(sb, "Work Arrangement", safeValue(j.workArrangement()));
            appendLine(sb, "Seniority / Min Experience",
                    safeValue(j.experienceLevel()) + (j.minExperienceYears() != null ? " (" + j.minExperienceYears() + "+ yrs)" : ""));
            appendLine(sb, "Listed Skills", j.skills().isEmpty() ? "None listed" : String.join(", ", j.skills()));
        }

        JobAiAnalysisEvidence ai = snapshot.jobAiAnalysis();
        if (ai != null) {
            sb.append("\nStructured JD Intelligence:\n");
            appendLine(sb, "Experience Summary", safeValue(ai.requiredExperienceSummary()));
            appendLine(sb, "Required Skills", ai.requiredSkills().isEmpty() ? "None" : String.join(", ", ai.requiredSkills()));
            appendLine(sb, "Nice-to-Have Skills", ai.niceToHaveSkills().isEmpty() ? "None" : String.join(", ", ai.niceToHaveSkills()));
            appendLine(sb, "Language Requirements", ai.languageRequirements().isEmpty() ? "None" : String.join(", ", ai.languageRequirements()));
            if (!ai.responsibilities().isEmpty()) {
                sb.append("  Key Responsibilities:\n");
                for (String resp : ai.responsibilities()) {
                    sb.append("    * ").append(resp).append('\n');
                }
            }
            if (hasText(ai.summary())) {
                appendLine(sb, "Role Summary", ai.summary());
            }
        }

        if (j != null && hasText(j.sanitizedDescription())) {
            sb.append("\nFull Job Description:\n");
            sb.append(j.sanitizedDescription().trim()).append('\n');
        }

        return sb.toString().trim();
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        sb.append("  ").append(label).append(": ").append(value).append('\n');
    }

    private String truncateSection(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text != null ? text : "";
        }
        return text.substring(0, maxChars).trim() + "\n[TRUNCATED]";
    }

    private String safeValue(String value) {
        return hasText(value) ? value.trim() : "Not specified";
    }

    private String joinNonBlank(String first, String second) {
        if (hasText(first) && hasText(second)) {
            return first.trim() + ", " + second.trim();
        }
        if (hasText(first)) {
            return first.trim();
        }
        if (hasText(second)) {
            return second.trim();
        }
        return "Not specified";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

