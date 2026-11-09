package com.eurojobcopilot.modules.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Candidate profile, resume, and search preference readiness summary")
public class DashboardReadinessDto {

    @Schema(description = "Whether the user has created a candidate profile")
    private boolean profileExists;

    @Schema(description = "Candidate profile completeness percentage (0-100)", example = "80")
    private int profileCompletenessPercent;

    @Schema(description = "Number of configured candidate skills", example = "6")
    private long skillsCount;

    @Schema(description = "Number of recorded work experience entries", example = "3")
    private long experiencesCount;

    @Schema(description = "Number of recorded education entries", example = "1")
    private long educationsCount;

    @Schema(description = "Number of uploaded resumes", example = "1")
    private long resumesCount;

    @Schema(description = "Whether at least one master resume is approved", example = "true")
    private boolean masterResumeApproved;

    @Schema(description = "Whether custom search preferences have been saved")
    private boolean preferencesConfigured;

    @Schema(description = "Whether Copilot Autopilot discovery is enabled")
    private boolean autopilotEnabled;

    @Schema(description = "Effective minimum match score threshold", example = "60")
    private int minMatchScore;

    @Schema(description = "Configured target European countries")
    private List<String> preferredCountries;

    @Schema(description = "Configured target job titles")
    private List<String> preferredJobTitles;
}

