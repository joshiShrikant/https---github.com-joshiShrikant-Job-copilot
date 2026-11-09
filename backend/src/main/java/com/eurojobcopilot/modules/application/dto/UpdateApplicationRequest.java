package com.eurojobcopilot.modules.application.dto;

import com.eurojobcopilot.core.validation.ValidUuid;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
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
@Schema(description = "Request payload to update application metadata, dates, salary offer, notes, or linked documents")
public class UpdateApplicationRequest {

    @ValidUuid(message = "Tailored resume UUID must be a valid canonical UUID")
    @Schema(description = "Canonical UUID of the tailored resume version used for this application")
    private String tailoredResumeUuid;

    @Schema(description = "When true, unlinks the currently attached tailored resume", defaultValue = "false")
    private boolean clearTailoredResume;

    @ValidUuid(message = "Cover letter UUID must be a valid canonical UUID")
    @Schema(description = "Canonical UUID of the cover letter version used for this application")
    private String coverLetterUuid;

    @Schema(description = "When true, unlinks the currently attached cover letter", defaultValue = "false")
    private boolean clearCoverLetter;

    @Schema(description = "UTC timestamp when the application was submitted")
    private Instant appliedDate;

    @Schema(description = "When true, clears appliedDate", defaultValue = "false")
    private boolean clearAppliedDate;

    @Schema(description = "Scheduled next follow-up date (ISO-8601)", example = "2026-11-22")
    private LocalDate nextFollowUpDate;

    @Schema(description = "When true, clears nextFollowUpDate", defaultValue = "false")
    private boolean clearNextFollowUpDate;

    @Schema(description = "Whether the scheduled application follow-up has been completed")
    private Boolean followUpCompleted;

    @Schema(description = "Scheduled next interview UTC timestamp")
    private Instant nextInterviewDate;

    @Schema(description = "When true, clears nextInterviewDate", defaultValue = "false")
    private boolean clearNextInterviewDate;

    @DecimalMin(value = "0.00", message = "Offered salary cannot be negative")
    @DecimalMax(value = "9999999.99", message = "Offered salary exceeds supported maximum")
    @Schema(description = "Offered salary amount", example = "102000.00")
    private BigDecimal currentSalaryOffered;

    @Schema(description = "When true, clears currentSalaryOffered and offeredCurrency", defaultValue = "false")
    private boolean clearSalaryOffer;

    @Size(min = 3, max = 3, message = "Offered currency must be a 3-letter ISO-4217 code")
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Offered currency must be a 3-letter ISO currency code")
    @Schema(description = "3-letter ISO currency code for salary offer", example = "EUR")
    private String offeredCurrency;

    @Size(max = 5000, message = "Notes must not exceed 5000 characters")
    @Schema(description = "Updated personal application notes, interview prep, or offer breakdown")
    private String notes;

    @Schema(description = "Optional optimistic concurrency lock version from prior read")
    private Integer expectedVersion;
}

