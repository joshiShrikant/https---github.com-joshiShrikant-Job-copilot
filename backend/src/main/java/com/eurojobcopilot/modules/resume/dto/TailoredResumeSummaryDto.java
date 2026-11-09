package com.eurojobcopilot.modules.resume.dto;

import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lightweight summary of a persisted JD-tailored resume version")
public class TailoredResumeSummaryDto {

    @Schema(description = "Public tailored resume UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uuid;

    @Schema(description = "Source master resume UUID")
    private String masterResumeUuid;

    @Schema(description = "Source master resume title", example = "Master Java Backend Resume")
    private String masterResumeTitle;

    @Schema(description = "Target job posting ID", example = "1042")
    private Long jobId;

    @Schema(description = "Target job posting title", example = "Staff Java Architect")
    private String jobTitle;

    @Schema(description = "Target employer name", example = "Adyen N.V.")
    private String companyName;

    @Schema(description = "Target job country", example = "Netherlands")
    private String jobCountry;

    @Schema(description = "Display title of this tailored CV draft", example = "Staff Java Architect · Adyen N.V. (v1)")
    private String title;

    @Schema(description = "Version number scoped to user and job", example = "1")
    private Integer versionNumber;

    @Schema(description = "Selected template", example = "MODERN")
    private ResumeTemplate templateName;

    @Schema(description = "Candidate-job compatibility match score (0-100)", example = "84.5")
    private Double matchScore;

    @Schema(description = "Deterministic ATS keyword coverage percentage (0-100)", example = "78.0")
    private Double atsKeywordCoverage;

    @Schema(description = "AI provider used for tailoring", example = "GEMINI")
    private String aiProvider;

    @Schema(description = "AI model used for tailoring", example = "gemini-2.5-flash")
    private String aiModelUsed;

    @Schema(description = "Whether the candidate has manually edited this tailored draft", example = "false")
    private boolean editedByUser;

    @Schema(description = "Whether a rendered PDF export file exists", example = "false")
    private boolean hasPdfExport;

    @Schema(description = "Whether a rendered DOCX export file exists", example = "false")
    private boolean hasDocxExport;

    @Schema(description = "UTC timestamp when tailored draft was generated")
    private Instant generatedAt;

    @Schema(description = "UTC creation timestamp")
    private Instant createdAt;

    @Schema(description = "UTC last modification timestamp")
    private Instant updatedAt;
}

