package com.eurojobcopilot.modules.resume.dto;

import com.eurojobcopilot.modules.resume.entity.ResumeParseStatus;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import com.eurojobcopilot.modules.resume.entity.StorageProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response representation of stored master resume metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Master resume metadata response")
public class ResumeResponse {

    @Schema(description = "Public resume UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uuid;

    @Schema(description = "Display title of the resume", example = "Master Full-Stack Java Resume 2026")
    private String title;

    @Schema(description = "Sanitized original filename", example = "shrikant-joshi-cv.pdf")
    private String fileName;

    @Schema(description = "File size in bytes", example = "245760")
    private Long fileSizeBytes;

    @Schema(description = "Validated MIME content type", example = "application/pdf")
    private String mimeType;

    @Schema(description = "Resume lifecycle category", example = "MASTER")
    private ResumeType resumeType;

    @Schema(description = "Storage backend provider", example = "LOCAL")
    private StorageProvider storageProvider;

    @Schema(description = "Sequential resume version for the user", example = "1")
    private Integer versionNumber;

    @JsonProperty("isMasterApproved")
    @Schema(name = "isMasterApproved", description = "Whether parsed master resume data has been human-approved", example = "false")
    private boolean masterApproved;

    @Schema(description = "Parsing and approval lifecycle status", example = "NOT_PARSED")
    private ResumeParseStatus parseStatus;

    @Schema(description = "UTC timestamp of latest AI parsing run")
    private Instant parsedAt;

    @Schema(description = "UTC timestamp of latest human approval")
    private Instant approvedAt;

    @Schema(description = "AI provider used to extract structured resume JSON", example = "GEMINI")
    private String aiProvider;

    @Schema(description = "AI model used to extract structured resume JSON", example = "gemini-2.5-flash")
    private String aiModelUsed;

    @Schema(description = "Whether structured parsed JSON content is saved on this resume", example = "false")
    private boolean hasParsedContent;

    @Schema(description = "Summary of profile synchronization performed during master approval (present on approval responses)")
    private MasterResumeSyncSummaryDto profileSyncSummary;

    @Schema(description = "UTC creation timestamp")
    private Instant createdAt;

    @Schema(description = "UTC last update timestamp")
    private Instant updatedAt;
}

