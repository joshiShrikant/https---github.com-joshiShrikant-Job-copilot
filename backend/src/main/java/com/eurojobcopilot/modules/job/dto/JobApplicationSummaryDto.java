package com.eurojobcopilot.modules.job.dto;

import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lightweight link to the user's tracked application pipeline item for this job")
public class JobApplicationSummaryDto {

    @Schema(description = "Tracked application UUID", example = "7f21a1e8-b4d2-4d30-9f1a-45c7d9b8e110")
    private String uuid;

    @Schema(description = "Current application pipeline stage", example = "APPLIED")
    private ApplicationStatus status;

    @Schema(description = "UTC timestamp when the application was submitted")
    private Instant appliedDate;

    @Schema(description = "Scheduled next follow-up date")
    private LocalDate nextFollowUpDate;

    @Schema(description = "Scheduled next interview UTC timestamp")
    private Instant nextInterviewDate;
}

