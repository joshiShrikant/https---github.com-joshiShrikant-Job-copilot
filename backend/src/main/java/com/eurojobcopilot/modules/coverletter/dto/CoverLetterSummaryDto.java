package com.eurojobcopilot.modules.coverletter.dto;

import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
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
@Schema(description = "Lightweight summary of a persisted AI cover letter version")
public class CoverLetterSummaryDto {

    @Schema(description = "Canonical UUID of the cover letter", example = "660e8400-e29b-41d4-a716-446655440000")
    private String uuid;

    @Schema(description = "Target job ID", example = "1042")
    private Long jobId;

    @Schema(description = "Target job title", example = "Staff Java Architect")
    private String jobTitle;

    @Schema(description = "Target company name", example = "Adyen N.V.")
    private String companyName;

    @Schema(description = "Target job country", example = "Netherlands")
    private String jobCountry;

    @Schema(description = "Optional linked tailored resume UUID")
    private String tailoredResumeUuid;

    @Schema(description = "Optional linked tailored resume display title")
    private String tailoredResumeTitle;

    @Schema(description = "Cover letter display title", example = "Cover Letter · Staff Java Architect · Adyen N.V. (v1)")
    private String title;

    @Schema(description = "Tone register used to draft the letter", example = "PROFESSIONAL")
    private CoverLetterTone tone;

    @Schema(description = "Incremental version number for this user and job", example = "1")
    private Integer versionNumber;

    @Schema(description = "Word count of the letter", example = "312")
    private Integer wordCount;

    @Schema(description = "AI provider identifier", example = "GEMINI")
    private String aiProvider;

    @Schema(description = "AI model used for generation", example = "gemini-2.5-flash")
    private String aiModelUsed;

    @Schema(description = "True when modified manually by the user after AI generation", example = "false")
    private boolean editedByUser;

    @Schema(description = "Timestamp when AI initially generated this draft")
    private Instant generatedAt;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}

