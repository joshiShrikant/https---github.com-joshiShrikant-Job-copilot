package com.eurojobcopilot.modules.resume.dto;

import com.eurojobcopilot.core.validation.ValidUuid;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
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
@Schema(description = "Request payload to generate a JD-tailored CV grounded strictly in verified candidate credentials")
public class GenerateTailoredResumeRequest {

    @NotNull(message = "Target job ID is required")
    @Positive(message = "Target job ID must be positive")
    @Schema(description = "Target job posting ID", example = "1042")
    private Long jobId;

    @ValidUuid(message = "Master resume UUID must be a valid canonical UUID")
    @Schema(description = "Optional canonical UUID of the candidate's MASTER resume (defaults to latest approved master CV, or latest master CV if unapproved)")
    private String masterResumeUuid;

    @Builder.Default
    @Schema(description = "Target CV visual layout template", example = "MODERN", defaultValue = "MODERN")
    private ResumeTemplate templateName = ResumeTemplate.MODERN;

    @Size(max = 500, message = "Tailoring focus must not exceed 500 characters")
    @Schema(description = "Optional emphasis instruction guiding which genuine candidate projects or strengths to foreground",
            example = "Emphasize high-throughput payment settlement architecture and Java 21 backend leadership")
    private String tailoringFocus;

    @Schema(description = "When false, returns latest existing draft matching job, master resume, and template if already generated (defaults to true)",
            example = "true")
    private Boolean forceRefresh;
}

