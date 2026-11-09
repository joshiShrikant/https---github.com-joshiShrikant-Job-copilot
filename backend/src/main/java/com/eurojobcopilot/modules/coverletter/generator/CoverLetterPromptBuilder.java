package com.eurojobcopilot.modules.coverletter.generator;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.config.AiProperties;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.CandidateHeaderEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.EducationEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.JobAiAnalysisEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.MasterResumeEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.PreferenceEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.SkillEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.TailoredResumeEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.WorkExperienceEvidence;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProjectDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeSkillDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeDataDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeProjectDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Constructs schema-constrained, anti-hallucination prompts for generating tailored
 * European job cover letters anchored in verified candidate history, company context,
 * and visa/relocation readiness.
 */
@Component
@RequiredArgsConstructor
public class CoverLetterPromptBuilder {

    public static final String TASK_NAME = "ai-cover-letter-generation";

    private static final String SYSTEM_PROMPT = """
            You are a principal European career strategist and executive technical writer.
            Write an authentic, high-signal cover letter connecting Verified Candidate Evidence to the Target Company & Role Specification below.

            CRITICAL ANTI-HALLUCINATION RULES (ZERO TOLERANCE):
            1. Reference ONLY employers, job titles, dates, projects, degrees, metrics, and skills explicitly supported in Verified Candidate Evidence or Linked Tailored CV.
            2. NEVER invent fake employers, promotions, unpossessed technologies, certifications, publications, or false metrics.
            3. NEVER falsely claim EU/EEA citizenship, Permanent Residency, or visa-free eligibility when the candidate's verified Work Authorization requires sponsorship. Frame visa sponsorship or relocation timeline honestly and constructively.
            4. Connect concrete candidate achievements directly to the Target Company's product domain, engineering scale, and responsibilities.
            5. Every skill listed in "highlightedSkills" MUST come directly from Verified Candidate Evidence.
            6. Adapt structure, vocabulary, and rhythm strictly to the requested Tone:
               - PROFESSIONAL: balanced, crisp European business register (280-380 words)
               - CONFIDENT: ownership-focused, achievement-led executive voice emphasizing scale and leadership impact (260-360 words)
               - ACADEMIC: formal, analytical register foregrounding research methodology, advanced degrees, and engineering rigor (300-400 words)
               - ENTHUSIASTIC: warm, product-curious, mission-aligned voice conveying genuine excitement for the company domain (270-360 words)
               - CONCISE: tight, recruiter-first, 3-short-paragraph summary eliminating fluff (190-260 words)
            7. Return ONLY a single valid JSON object with NO markdown fences and NO extra prose outside JSON.

            Exact JSON Schema:
            {
              "salutation": "Dear Hiring Team at <Company>," or role-appropriate salutation,
              "openingParagraph": compelling 2-3 sentence hook connecting candidate's verified seniority/domain to the target role and company mission,
              "bodyParagraphs": [
                "First body paragraph connecting concrete verified experience/project outcomes to core JD technical requirements",
                "Second body paragraph connecting architecture/collaboration/domain evidence to additional role responsibilities (optional third paragraph if substantive)"
              ],
              "relocationAndAuthorizationParagraph": concise factual statement covering verified EU work authorization or visa sponsorship alignment, current location/relocation readiness, and notice period (or null if CONCISE and covered briefly elsewhere),
              "closingParagraph": polite call to action inviting an interview and reiterating interest in the team,
              "signOff": "Sincerely,\\n<Verified Candidate Full Name>",
              "companyConnectionSummary": 1-2 sentence summary explaining why the candidate's verified background fits this specific employer's domain/mission,
              "keyAlignmentPoints": [2-4 concise bullet highlights linking candidate proof to JD priorities],
              "highlightedSkills": [verified candidate skills emphasized in this letter],
              "warnings": [optional caveat when candidate lacks a required skill or language requirement]
            }
            """;

    private final AiProperties aiProperties;

    public AiJsonPromptRequest buildPromptRequest(CoverLetterContextSnapshot snapshot) {
        int totalBudget = Math.max(3000, aiProperties.getMaxInputChars());
        int candidateBudget = Math.max(1400, (int) (totalBudget * 0.50));
        int jobBudget = Math.max(1200, totalBudget - candidateBudget);

        String candidateSection = truncateSection(formatCandidateSection(snapshot), candidateBudget);
        String jobSection = truncateSection(formatJobSection(snapshot), jobBudget);

        CoverLetterTone tone = snapshot.tone() != null ? snapshot.tone() : CoverLetterTone.PROFESSIONAL;
        String notes = hasText(snapshot.personalNotes())
                ? snapshot.personalNotes().trim()
                : "No extra personal notes supplied; emphasize top role impact, company fit, and European work/relocation readiness.";

        String userPrompt = """
                Draft a grounded European cover letter for the target opportunity below.

                Requested Tone: %s
                Candidate Personal Notes / Emphasis: %s

                === VERIFIED CANDIDATE EVIDENCE ===
                %s

                === TARGET COMPANY & ROLE SPECIFICATION ===
                %s
                """.formatted(tone.name(), notes, candidateSection, jobSection);

        return new AiJsonPromptRequest(
                TASK_NAME,
                SYSTEM_PROMPT,
                userPrompt,
                0.35,
                null
        );
    }

    private String formatCandidateSection(CoverLetterContextSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();

        sb.append("1. Verified Candidate Identity & European Mobility:\n");
        CandidateHeaderEvidence h = snapshot.header();
        if (h == null) {
            sb.append("  Not provided.\n");
        } else {
            appendLine(sb, "Full Name", safeValue(h.fullName()));
            appendLine(sb, "Current Designation", safeValue(h.currentDesignation()));
            appendLine(sb, "Years of Experience", h.yearsOfExperience() != null ? h.yearsOfExperience() + " years" : "Not specified");
            appendLine(sb, "Current Location", joinNonBlank(h.currentCity(), h.currentCountry()));
            appendLine(sb, "Work Authorization Status", safeValue(h.workAuthorizationStatus()));
            appendLine(sb, "Relocation Preference", safeValue(h.relocationPreference()));
            if (h.noticePeriodDays() != null) {
                appendLine(sb, "Notice Period", h.noticePeriodDays() + " days");
            }
            appendLine(sb, "Bio / Summary", safeValue(h.bio()));
        }

        PreferenceEvidence pref = snapshot.preference();
        if (pref != null) {
            sb.append("  Target Mobility Preferences: requiresSponsorship=")
                    .append(pref.requiresSponsorship())
                    .append(", requiresRelocation=").append(pref.requiresRelocation());
            if (!pref.preferredCountries().isEmpty()) {
                sb.append(", preferredCountries=").append(String.join(", ", pref.preferredCountries()));
            }
            sb.append('\n');
        }

        sb.append("\n2. Verified Skills (Profile):\n");
        if (snapshot.skills().isEmpty()) {
            sb.append("  None recorded.\n");
        } else {
            for (SkillEvidence s : snapshot.skills()) {
                sb.append("  - ").append(safeValue(s.skillName()))
                        .append(s.yearsExperience() != null ? " (" + s.yearsExperience() + " yrs" : " (duration unset")
                        .append(s.proficiencyLevel() != null ? ", " + s.proficiencyLevel() : "")
                        .append(s.primary() ? ", Primary" : "")
                        .append(")\n");
            }
        }

        sb.append("\n3. Verified Work History:\n");
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
                    sb.append("    Details: ").append(exp.description().trim()).append('\n');
                }
            }
        }

        sb.append("\n4. Verified Educations:\n");
        if (snapshot.educations().isEmpty()) {
            sb.append("  None recorded.\n");
        } else {
            for (EducationEvidence edu : snapshot.educations()) {
                sb.append("  - ").append(safeValue(edu.degree()))
                        .append(" in ").append(safeValue(edu.fieldOfStudy()))
                        .append(", ").append(safeValue(edu.institution()))
                        .append(" (").append(safeValue(edu.country())).append(")\n");
            }
        }

        TailoredResumeEvidence tr = snapshot.tailoredResume();
        if (tr != null && tr.resumeData() != null) {
            sb.append("\n5. Linked Tailored CV Context (for role alignment continuity):\n");
            appendLine(sb, "Tailored CV Title", safeValue(tr.title()));
            if (hasText(tr.tailoringFocus())) {
                appendLine(sb, "CV Tailoring Focus", tr.tailoringFocus());
            }
            TailoredResumeDataDto data = tr.resumeData();
            if (hasText(data.getTailoredSummary())) {
                appendLine(sb, "Tailored Summary", data.getTailoredSummary());
            }
            if (data.getHighlightedSkills() != null && !data.getHighlightedSkills().isEmpty()) {
                appendLine(sb, "CV Highlighted Skills", String.join(", ", data.getHighlightedSkills()));
            }
            if (data.getOptimizedExperiences() != null && !data.getOptimizedExperiences().isEmpty()) {
                sb.append("  CV Emphasized Experience Bullets:\n");
                for (TailoredResumeExperienceDto exp : data.getOptimizedExperiences()) {
                    if (exp == null) {
                        continue;
                    }
                    sb.append("    * ").append(safeValue(exp.getDesignation()))
                            .append(" at ").append(safeValue(exp.getCompanyName())).append('\n');
                    if (exp.getTailoredBullets() != null) {
                        for (String b : exp.getTailoredBullets()) {
                            sb.append("      - ").append(b).append('\n');
                        }
                    }
                }
            }
            if (data.getProjects() != null && !data.getProjects().isEmpty()) {
                sb.append("  Verified Projects:\n");
                for (TailoredResumeProjectDto p : data.getProjects()) {
                    if (p != null && hasText(p.getName())) {
                        sb.append("    * ").append(p.getName().trim());
                        if (hasText(p.getDescription())) {
                            sb.append(": ").append(p.getDescription().trim());
                        }
                        sb.append('\n');
                    }
                }
            }
        }

        MasterResumeEvidence mr = snapshot.masterResume();
        if (mr != null) {
            sb.append("\n6. Master CV Evidence:\n");
            appendLine(sb, "Master CV Title", safeValue(mr.title()));
            ParsedResumeJsonDto parsed = mr.parsedResume();
            if (parsed != null) {
                if (hasText(parsed.getSummary())) {
                    appendLine(sb, "Master CV Summary", parsed.getSummary());
                }
                if (parsed.getSkills() != null && !parsed.getSkills().isEmpty()) {
                    List<String> names = parsed.getSkills().stream()
                            .map(ParsedResumeSkillDto::getSkillName)
                            .filter(CoverLetterPromptBuilder::hasText)
                            .toList();
                    appendLine(sb, "Parsed Skills", String.join(", ", names));
                }
                if (parsed.getExperiences() != null && !parsed.getExperiences().isEmpty() && snapshot.workExperiences().isEmpty()) {
                    sb.append("  Parsed Experiences:\n");
                    for (ParsedResumeExperienceDto pe : parsed.getExperiences()) {
                        if (pe == null) {
                            continue;
                        }
                        sb.append("    * ").append(safeValue(pe.getDesignation()))
                                .append(" at ").append(safeValue(pe.getCompanyName())).append('\n');
                        if (hasText(pe.getDescription())) {
                            sb.append("      ").append(pe.getDescription().trim()).append('\n');
                        }
                    }
                }
                if (parsed.getProjects() != null && !parsed.getProjects().isEmpty()) {
                    sb.append("  Parsed Projects:\n");
                    for (ParsedResumeProjectDto proj : parsed.getProjects()) {
                        if (proj != null && hasText(proj.getName())) {
                            sb.append("    * ").append(proj.getName().trim());
                            if (hasText(proj.getDescription())) {
                                sb.append(": ").append(proj.getDescription().trim());
                            }
                            sb.append('\n');
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
                sb.append("  Extracted Raw Resume Text:\n");
                sb.append(truncateSection(mr.rawExtractedText(), 4500)).append('\n');
            }
        }

        return sb.toString().trim();
    }

    private String formatJobSection(CoverLetterContextSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        JobEvidence j = snapshot.job();
        if (j != null) {
            appendLine(sb, "Job Title", safeValue(j.title()));
            appendLine(sb, "Company Name", safeValue(j.companyName()));
            appendLine(sb, "Location", hasText(j.location()) ? j.location() : joinNonBlank(j.city(), j.country()));
            appendLine(sb, "Work Arrangement", safeValue(j.workArrangement()));
            appendLine(sb, "Seniority / Level",
                    safeValue(j.experienceLevel()) + (j.minExperienceYears() != null ? " (" + j.minExperienceYears() + "+ yrs)" : ""));
            appendLine(sb, "Job Sponsor Status", safeValue(j.sponsorStatus()));
            appendLine(sb, "Listed Skills", j.skills().isEmpty() ? "None listed" : String.join(", ", j.skills()));

            if (hasText(j.companyIndustry()) || hasText(j.companyDescription()) || j.verifiedSponsor() || !j.companyTechStack().isEmpty()) {
                sb.append("\nEmployer Intelligence:\n");
                appendLine(sb, "Industry", safeValue(j.companyIndustry()));
                appendLine(sb, "Company Size", safeValue(j.companySize()));
                appendLine(sb, "Verified Sponsor Registry",
                        j.verifiedSponsor()
                                ? "YES (" + safeValue(j.sponsorConfidenceLevel()) + ")"
                                : "No / Unconfirmed (" + safeValue(j.sponsorConfidenceLevel()) + ")");
                if (!j.companyTechStack().isEmpty()) {
                    appendLine(sb, "Company Tech Stack", String.join(", ", j.companyTechStack()));
                }
                if (hasText(j.companyDescription())) {
                    appendLine(sb, "Company Overview / Mission", truncateSection(j.companyDescription().trim(), 900));
                }
            }
        }

        JobAiAnalysisEvidence ai = snapshot.jobAiAnalysis();
        if (ai != null) {
            sb.append("\nStructured JD Intelligence:\n");
            appendLine(sb, "Required Experience", safeValue(ai.requiredExperienceSummary()));
            appendLine(sb, "Required Skills", ai.requiredSkills().isEmpty() ? "None" : String.join(", ", ai.requiredSkills()));
            appendLine(sb, "Nice-to-Have Skills", ai.niceToHaveSkills().isEmpty() ? "None" : String.join(", ", ai.niceToHaveSkills()));
            appendLine(sb, "Language Requirements", ai.languageRequirements().isEmpty() ? "None" : String.join(", ", ai.languageRequirements()));
            if (hasText(ai.visaSponsorshipAnalysis())) {
                appendLine(sb, "Visa Sponsorship Clues", ai.visaSponsorshipAnalysis());
            }
            if (hasText(ai.relocationAnalysis())) {
                appendLine(sb, "Relocation Clues", ai.relocationAnalysis());
            }
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
            sb.append("\nJob Description Excerpt:\n");
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
        return hasText(second) ? second.trim() : "Not specified";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

