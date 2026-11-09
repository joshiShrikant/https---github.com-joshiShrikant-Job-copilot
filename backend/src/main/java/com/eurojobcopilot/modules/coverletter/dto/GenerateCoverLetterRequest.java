package com.eurojobcopilot.modules.coverletter.dto;

import com.eurojobcopilot.core.validation.ValidUuid;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to generate a company-aligned AI cover letter grounded strictly in verified candidate credentials")
public class GenerateCoverLetterRequest {

    @NotNull(message = "Target job ID is required")
    @Positive(message = "Target job ID must be positive")
    @Schema(description = "Target job posting ID", example = "1042")
    private Long jobId;

    @ValidUuid(message = "Tailored resume UUID must be a valid canonical UUID")
    @Schema(description = "Optional canonical UUID of the candidate's tailored CV for this role; when omitted, automatically links latest tailored CV for this job if present or relies on approved Master CV and profile")
    private String tailoredResumeUuid;

    @Builder.Default
    @Schema(description = "Tone register for the cover letter", example = "PROFESSIONAL", defaultValue = "PROFESSIONAL")
    private CoverLetterTone tone = CoverLetterTone.PROFESSIONAL;

    @Size(max = 1000, message = "Personal notes must not exceed 1000 characters")
    @Schema(description = "Optional candidate notes emphasizing motivation, company alignment, notice period, or relocation timeline",
            example = "Excited about SEPA Instant scaling in Amsterdam and able to relocate within 30 days.")
    private String personalNotes;

    @Schema(description = "When false, returns latest cached cover letter matching job, tailored CV link, tone, and notes if already generated (defaults to true)",
            example = "true")
    private Boolean forceRefresh;
}

