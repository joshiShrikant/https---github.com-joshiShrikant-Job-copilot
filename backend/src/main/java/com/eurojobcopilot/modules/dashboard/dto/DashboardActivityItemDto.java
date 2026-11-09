package com.eurojobcopilot.modules.dashboard.dto;

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
@Schema(description = "Recent timestamped activity item rendered on the executive dashboard")
public class DashboardActivityItemDto {

    @Schema(description = "Activity category", example = "APPLICATION")
    private String type;

    @Schema(description = "Primary headline for the activity item", example = "Senior Java Engineer · Booking.com")
    private String title;

    @Schema(description = "Contextual detail line", example = "Status: APPLIED")
    private String subtitle;

    @Schema(description = "Timestamp of the event")
    private Instant timestamp;

    @Schema(description = "Target Angular route to inspect details", example = "/applications")
    private String targetRoute;
}

