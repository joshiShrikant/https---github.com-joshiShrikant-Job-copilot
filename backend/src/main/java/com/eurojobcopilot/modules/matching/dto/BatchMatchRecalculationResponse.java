package com.eurojobcopilot.modules.matching.dto;

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
@Schema(description = "Summary telemetry returned after recalculating deterministic job match scores for a user")
public class BatchMatchRecalculationResponse {

    @Schema(description = "Total active non-expired jobs evaluated and persisted", example = "42")
    private int evaluatedJobsCount;

    @Schema(description = "Evaluated jobs meeting or exceeding the user's minimum match threshold", example = "12")
    private int highMatchJobsCount;

    @Schema(description = "User minimum match threshold percentage applied", example = "60")
    private int minMatchThreshold;

    @Schema(description = "UTC timestamp when batch scoring completed")
    private Instant calculatedAt;

    @Schema(description = "Active matching weight configuration used during scoring")
    private MatchingWeightsDto weightsUsed;

    @Schema(description = "Top highest-scoring jobs from this recalculation batch")
    private List<JobMatchSummaryItemDto> topMatches;
}

