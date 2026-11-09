package com.eurojobcopilot.modules.application.dto;

import com.eurojobcopilot.core.validation.ValidUuid;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to track a job in the user's application pipeline")
public class CreateApplicationRequest {

    @NotNull(message = "Target job ID is required")
    @Positive(message = "Target job ID must be positive")
    @Schema(description = "Target indexed job posting ID", example = "1042")
    private Long jobId;

    @Builder.Default
    @Schema(description = "Initial pipeline stage (defaults to INTERESTED)", example = "APPLIED", defaultValue = "INTERESTED")
    private ApplicationStatus status = ApplicationStatus.INTERESTED;

    @ValidUuid(message = "Tailored resume UUID must be a valid canonical UUID")
    @Schema(description = "Optional canonical UUID of the tailored resume version submitted or paired with this application")
    private String tailoredResumeUuid;

    @ValidUuid(message = "Cover letter UUID must be a valid canonical UUID")
    @Schema(description = "Optional canonical UUID of the cover letter version submitted or paired with this application")
    private String coverLetterUuid;

    @Schema(description = "Optional UTC timestamp when the application was submitted (defaults to now when created in APPLIED stage)")
    private Instant appliedDate;

    @Schema(description = "Optional scheduled follow-up reminder date (ISO-8601 date)", example = "2026-11-18")
    private LocalDate nextFollowUpDate;

    @Schema(description = "Optional next scheduled interview UTC timestamp", example = "2026-11-20T14:00:00Z")
    private Instant nextInterviewDate;

    @DecimalMin(value = "0.00", message = "Offered salary cannot be negative")
    @DecimalMax(value = "9999999.99", message = "Offered salary exceeds supported maximum")
    @Schema(description = "Optional compensation offer amount", example = "95000.00")
    private BigDecimal currentSalaryOffered;

    @Size(min = 3, max = 3, message = "Offered currency must be a 3-letter ISO-4217 code")
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Offered currency must be a 3-letter ISO currency code")
    @Schema(description = "Optional 3-letter ISO currency code for offered salary", example = "EUR")
    private String offeredCurrency;

    @Size(max = 5000, message = "Notes must not exceed 5000 characters")
    @Schema(description = "Personal notes, referral context, or preparation reminders")
    private String notes;

    @Size(max = 1000, message = "Transition notes must not exceed 1000 characters")
    @Schema(description = "Optional initial status timeline note", example = "Applied directly with tailored Java CV v2")
    private String transitionNotes;
}

