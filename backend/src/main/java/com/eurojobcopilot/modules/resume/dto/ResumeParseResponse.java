package com.eurojobcopilot.modules.resume.dto;

import com.eurojobcopilot.modules.resume.entity.ResumeParseStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Structured master resume parsing result and review payload")
public class ResumeParseResponse {

    @Schema(description = "Canonical UUID of the resume", example = "550e8400-e29b-41d4-a716-446655440000")
    private String resumeUuid;

    @Schema(description = "Resume title", example = "Master Full-Stack Java Resume 2026")
    private String title;

    @Schema(description = "Sanitized original filename", example = "elena-cv-2026.pdf")
    private String fileName;

    @Schema(description = "MIME content type", example = "application/pdf")
    private String mimeType;

    @Schema(description = "Sequential resume version", example = "1")
    private Integer versionNumber;

    @JsonProperty("isMasterApproved")
    @Schema(name = "isMasterApproved", description = "Whether the candidate has human-approved the parsed master resume", example = "false")
    private boolean masterApproved;

    @Schema(description = "Current parse lifecycle status", example = "PARSED")
    private ResumeParseStatus parseStatus;

    @Schema(description = "UTC timestamp when resume parsing completed")
    private Instant parsedAt;

    @Schema(description = "UTC timestamp when candidate approved parsed resume")
    private Instant approvedAt;

    @Schema(description = "AI provider used for structured extraction", example = "GEMINI")
    private String aiProvider;

    @Schema(description = "AI model used for structured extraction", example = "gemini-2.5-flash")
    private String aiModelUsed;

    @Schema(description = "Count of raw characters extracted from the PDF/DOCX file", example = "4280")
    private Integer extractedCharCount;

    @Schema(description = "Extracted and validated structured resume draft ready for human editing and approval")
    private ParsedResumeJsonDto parsedData;

    @Schema(description = "Combined extraction and validation warnings requiring human review")
    private List<String> warnings;
}

