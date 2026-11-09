package com.eurojobcopilot.modules.user.dto;

import com.eurojobcopilot.core.enums.WorkArrangement;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authenticated candidate's job search and autopilot preferences")
public class JobPreferencesResponse {

    @Schema(description = "Preferred job titles for matching and discovery", example = "[\"Senior Java Engineer\", \"Staff Backend Engineer\"]")
    private List<String> preferredJobTitles;

    @Schema(description = "Preferred European destination countries", example = "[\"Germany\", \"Netherlands\"]")
    private List<String> preferredCountries;

    @Schema(description = "Preferred target European cities", example = "[\"Berlin\", \"Amsterdam\", \"Munich\"]")
    private List<String> preferredCities;

    @Schema(description = "Minimum expected annual gross salary", example = "85000.00")
    private BigDecimal minSalary;

    @Schema(description = "3-letter ISO 4217 currency code", example = "EUR")
    private String salaryCurrency;

    @Schema(description = "Whether the candidate prioritizes visa-sponsoring roles", example = "true")
    private boolean requiresSponsorship;

    @Schema(description = "Whether the candidate requires relocation support", example = "true")
    private boolean requiresRelocation;

    @Schema(description = "Preferred work arrangement filter", example = "HYBRID")
    private WorkArrangement workArrangement;

    @Schema(description = "Restrict discovery/matching to English-speaking roles only", example = "true")
    private boolean englishSpeakingOnly;

    @Schema(description = "Minimum compatibility match score threshold (0-100)", example = "60")
    private Integer minMatchScore;

    @Schema(description = "Enables background autopilot recommendations and alerts", example = "false")
    private boolean autopilotEnabled;

    @Schema(description = "Temporarily pauses scheduled autopilot execution while preserving settings", example = "false")
    private boolean autopilotPaused;

    @Schema(description = "Enables optional email delivery for autopilot digests when mail relay is configured", example = "false")
    private boolean emailNotificationsEnabled;

    @Schema(description = "Timestamp of the user's most recent autopilot scan", nullable = true)
    private Instant lastAutopilotRunAt;

    @Schema(description = "Timestamp when preferences were last saved", nullable = true)
    private Instant updatedAt;
}

