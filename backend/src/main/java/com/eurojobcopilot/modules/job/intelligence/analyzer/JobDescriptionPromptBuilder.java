package com.eurojobcopilot.modules.job.intelligence.analyzer;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.config.AiProperties;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds deterministic, schema-constrained JSON extraction prompts for job descriptions.
 */
@Component
@RequiredArgsConstructor
public class JobDescriptionPromptBuilder {

    public static final String TASK_NAME = "job-description-analysis";

    private static final String SYSTEM_PROMPT = """
            You are a precise European technical recruiter and immigration intelligence analyst.
            Extract structured information from the provided job posting and return ONLY a single valid JSON object.
            Do NOT wrap your answer in markdown code fences or prose. Do NOT invent unstated facts.
            When a field (such as salary, sponsorship, relocation, or language requirement) is not stated, return null or [].

            Return strictly these JSON keys:
            - "jobTitle": string or null
            - "company": string or null
            - "country": string (English country name in Europe/target country) or null
            - "location": string (city and/or region + country) or null
            - "requiredExperienceYears": number (e.g. 5.0) or null
            - "requiredExperienceLevel": one of ["ENTRY", "JUNIOR", "MID", "SENIOR", "LEAD", "PRINCIPAL"] or null
            - "requiredExperienceSummary": concise single-line summary string or null
            - "requiredSkills": array of concise skill/technology names ([] if none)
            - "preferredSkills": array of concise optional/nice-to-have skills ([] if none)
            - "responsibilities": array of concise key responsibility statements ([] if none)
            - "benefits": array of stated perks/benefits ([] if none)
            - "languageRequirements": array of language demands e.g. ["English (C1)", "German (B1 optional)"] ([] if none)
            - "salaryMin": numeric lower compensation bound or null
            - "salaryMax": numeric upper compensation bound or null
            - "salaryCurrency": 3-letter uppercase ISO currency code e.g. "EUR", "GBP", "CHF" or null
            - "salaryPeriod": one of ["YEARLY", "MONTHLY", "DAILY", "HOURLY"] or null
            - "workArrangement": one of ["REMOTE", "HYBRID", "ONSITE"] or null
            - "employmentType": one of ["FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP", "FREELANCE"] or null
            - "sponsorshipOffered": boolean (true if visa/work permit sponsorship or EU Blue Card explicitly offered, false if explicitly declined or local permit strictly required, null if unmentioned)
            - "visaSponsorshipAnalysis": concise 1-2 sentence explanation of visa sponsorship clues or absence thereof
            - "relocationAssistanceOffered": boolean (true if relocation support explicitly offered, false if explicitly excluded, null if unmentioned)
            - "relocationAnalysis": concise 1-2 sentence explanation of relocation support clues or absence thereof
            - "summary": concise 2-3 sentence executive summary of the role and core expectations
            """;

    private final JobNormalizer jobNormalizer;
    private final AiProperties aiProperties;

    public AiJsonPromptRequest buildPromptRequest(Job job) {
        String cleanDescription = jobNormalizer.sanitizeHtmlToText(job.getDescription());
        if (cleanDescription.isBlank()) {
            cleanDescription = job.getDescription() != null ? job.getDescription().trim() : "";
        }

        int maxChars = Math.max(1000, aiProperties.getMaxInputChars());
        String truncatedDescription = cleanDescription.length() > maxChars
                ? cleanDescription.substring(0, maxChars).trim() + "\n[TRUNCATED]"
                : cleanDescription;

        String userPrompt = """
                Analyze this job posting and extract the structured JSON fields.

                Posting Metadata:
                - Title: %s
                - Company: %s
                - Country: %s
                - Location: %s
                - Advertised Work Arrangement Hint: %s
                - Advertised Employment Type Hint: %s

                Job Description:
                %s
                """.formatted(
                safeValue(job.getTitle()),
                safeValue(job.getCompanyName()),
                safeValue(job.getCountry()),
                safeValue(job.getLocation()),
                job.getWorkArrangement() != null ? job.getWorkArrangement().name() : "Unknown",
                job.getEmploymentType() != null ? job.getEmploymentType().name() : "Unknown",
                truncatedDescription
        );

        return new AiJsonPromptRequest(
                TASK_NAME,
                SYSTEM_PROMPT,
                userPrompt,
                0.1,
                null
        );
    }

    private String safeValue(String value) {
        return value != null && !value.isBlank() ? value.trim() : "Unknown";
    }
}

