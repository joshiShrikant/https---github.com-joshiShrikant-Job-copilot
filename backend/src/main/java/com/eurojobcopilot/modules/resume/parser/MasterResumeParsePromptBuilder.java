package com.eurojobcopilot.modules.resume.parser;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.config.AiProperties;
import com.eurojobcopilot.modules.resume.entity.Resume;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Constructs strict anti-hallucination JSON extraction prompts from extracted master resume text.
 */
@Component
@RequiredArgsConstructor
public class MasterResumeParsePromptBuilder {

    public static final String TASK_NAME = "master-resume-parsing";

    private static final String SYSTEM_PROMPT = """
            You are an expert European technical recruiter and structured CV parser.
            Convert the provided candidate resume text into a single valid JSON object matching the exact schema below.

            CRITICAL ANTI-HALLUCINATION RULES:
            - Extract ONLY facts explicitly present in the candidate resume text below.
            - NEVER invent, guess, extrapolate, or assume unstated employers, roles, projects, degrees, GPAs, skills, dates, URLs, countries, or European visa/work authorization status.
            - When a field is not explicitly stated in the resume text, return null (or [] for arrays).
            - Return ONLY a single valid JSON object with NO markdown code fences and NO prose outside JSON.

            Exact JSON Schema:
            {
              "profile": {
                "fullName": string or null,
                "currentDesignation": string or null,
                "yearsOfExperience": number (e.g. 6.5) or null,
                "currentCountry": string (English country name if stated, e.g. "Germany", "Netherlands") or null,
                "currentCity": string or null,
                "linkedinUrl": string (full https:// URL if explicitly printed) or null,
                "githubUrl": string (full https:// URL if explicitly printed) or null,
                "portfolioUrl": string (full https:// URL if explicitly printed) or null,
                "bio": concise factual summary grounded strictly in the resume or null,
                "workAuthorizationStatus": one of ["EU_CITIZEN", "PERMANENT_RESIDENT", "BLUE_CARD_HOLDER", "NEEDS_SPONSORSHIP", "OTHER"] ONLY if explicitly mentioned in CV else null,
                "relocationPreference": one of ["WILLING_TO_RELOCATE", "RELOCATING_SOON", "ALREADY_IN_EUROPE", "REMOTE_ONLY", "NOT_INTERESTED"] ONLY if explicitly mentioned in CV else null
              },
              "summary": concise 2-4 sentence factual career summary grounded strictly in the resume or null,
              "skills": [
                {
                  "skillName": string,
                  "yearsExperience": number or null,
                  "proficiencyLevel": one of ["BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"] or null,
                  "primary": boolean
                }
              ],
              "experiences": [
                {
                  "companyName": string,
                  "designation": string,
                  "location": string or null,
                  "startDate": "YYYY-MM-DD" or "YYYY-MM" or "YYYY" or null,
                  "endDate": "YYYY-MM-DD" or "YYYY-MM" or "YYYY" or null (must be null when current is true),
                  "current": boolean,
                  "description": string preserving concrete bullet outcomes and metrics or null,
                  "skillsUsed": [string]
                }
              ],
              "educations": [
                {
                  "institution": string,
                  "degree": string,
                  "fieldOfStudy": string,
                  "startDate": "YYYY-MM-DD" or "YYYY-MM" or "YYYY" or null,
                  "endDate": "YYYY-MM-DD" or "YYYY-MM" or "YYYY" or null,
                  "gradeOrGpa": string or null,
                  "country": string or null
                }
              ],
              "projects": [
                {
                  "name": string,
                  "description": string or null,
                  "technologies": [string],
                  "url": string or null
                }
              ],
              "languages": ["Language (Level)", ...],
              "certifications": [string],
              "warnings": [string]
            }
            """;

    private final AiProperties aiProperties;

    public PromptBuildResult buildPromptRequest(Resume resume, ExtractedResumeText extracted) {
        int maxChars = Math.max(2000, aiProperties.getMaxInputChars());
        String rawText = extracted != null && extracted.text() != null ? extracted.text() : "";
        boolean truncated = rawText.length() > maxChars;

        String boundedText = truncated
                ? rawText.substring(0, maxChars).trim() + "\n[TRUNCATED DUE TO INPUT CHAR LIMIT]"
                : rawText;

        String userPrompt = """
                Parse this candidate master resume document and extract the structured JSON fields.

                Document Metadata:
                - Resume Title: %s
                - Original File Name: %s
                - Format: %s
                - Pages Extracted: %d
                - Version: v%d

                Extracted Resume Text:
                %s
                """.formatted(
                safeMeta(resume != null ? resume.getTitle() : null),
                safeMeta(resume != null ? resume.getFileName() : null),
                safeMeta(extracted != null ? extracted.format() : null),
                extracted != null ? extracted.pageCount() : 1,
                resume != null && resume.getVersionNumber() != null ? resume.getVersionNumber() : 1,
                boundedText
        );

        AiJsonPromptRequest request = new AiJsonPromptRequest(
                TASK_NAME,
                SYSTEM_PROMPT,
                userPrompt,
                0.05,
                null
        );
        return new PromptBuildResult(request, truncated);
    }

    private String safeMeta(String value) {
        return value != null && !value.isBlank() ? value.trim() : "Unknown";
    }

    public record PromptBuildResult(AiJsonPromptRequest request, boolean inputTruncated) {
    }
}
