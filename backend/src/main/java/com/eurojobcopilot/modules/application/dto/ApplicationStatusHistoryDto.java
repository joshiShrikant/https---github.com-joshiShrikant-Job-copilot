package com.eurojobcopilot.modules.application.dto;

import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
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
@Schema(description = "Chronological status transition entry for a tracked application")
public class ApplicationStatusHistoryDto {

    @Schema(description = "History record ID", example = "12")
    private Long id;

    @Schema(description = "Previous pipeline stage (null on initial creation)", example = "APPLIED")
    private ApplicationStatus previousStatus;

    @Schema(description = "Resulting pipeline stage", example = "TECH_INTERVIEW")
    private ApplicationStatus newStatus;

    @Schema(description = "Optional note attached to this transition")
    private String transitionNotes;

    @Schema(description = "UTC timestamp when the stage transition occurred")
    private Instant transitionedAt;
}

