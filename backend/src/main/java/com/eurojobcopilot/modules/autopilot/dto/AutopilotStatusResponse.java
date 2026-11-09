package com.eurojobcopilot.modules.autopilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Current user's autopilot configuration, pause state, recommendation summary, and latest run telemetry")
public class AutopilotStatusResponse {

    private boolean autopilotEnabled;
    private boolean autopilotPaused;
    private boolean emailNotificationsEnabled;
    private boolean serverEmailRelayEnabled;
    private Instant lastAutopilotRunAt;
    private int minMatchScore;
    private List<String> preferredJobTitles;
    private List<String> preferredCountries;
    private List<String> preferredCities;
    private long recommendedJobsCount;
    private long unreadNotificationsCount;
    private AutopilotRunLogDto latestRun;
}

