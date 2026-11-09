package com.eurojobcopilot.modules.application.dto;

import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Request payload to transition an application to a new pipeline stage and record status history")
public class UpdateApplicationStatusRequest {

    @NotNull(message = "New application status is required")
    @Schema(description = "Target pipeline stage", example = "TECH_INTERVIEW")
    private ApplicationStatus newStatus;

    @Size(max = 1000, message = "Transition notes must not exceed 1000 characters")
    @Schema(description = "Optional explanation recorded in the chronological status timeline",
            example = "Passed coding screen; advancing to system design interview.")
    private String transitionNotes;

    @Schema(description = "Optional applied timestamp override (automatically set to now when moving to APPLIED if previously unset)")
    private Instant appliedDate;

    @Schema(description = "Optional updated next interview UTC timestamp during stage change")
    private Instant nextInterviewDate;

    @Schema(description = "Optional updated next follow-up date during stage change")
    private LocalDate nextFollowUpDate;
}

