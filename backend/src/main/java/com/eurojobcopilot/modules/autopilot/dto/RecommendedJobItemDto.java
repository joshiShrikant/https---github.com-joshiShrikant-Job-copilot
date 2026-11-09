package com.eurojobcopilot.modules.autopilot.dto;

import com.eurojobcopilot.modules.job.dto.JobSummaryDto;
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
@Schema(description = "Autopilot-ranked job recommendation enriched with match explanations and visa sponsorship evidence")
public class RecommendedJobItemDto {

    private JobSummaryDto job;
    private List<String> matchingSkills;
    private List<String> missingSkills;
    private List<String> recommendationReasons;
    private String experienceCompatibility;
    private boolean meetsMinMatchScore;
    private boolean matchesPreferredCountry;
    private boolean matchesPreferredTitle;
    private boolean verifiedSponsor;
    private String sponsorshipEvidenceSummary;
    private String aiAnalysisSummary;
    private boolean alreadyApplied;
}

