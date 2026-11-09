package com.eurojobcopilot.modules.dashboard.dto;

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
@Schema(description = "Executive summary metrics, readiness progress, pipeline funnel, and recent activity")
public class DashboardSummaryResponse {

    @Schema(description = "Total active discovered jobs across European sources", example = "1420")
    private long totalJobsDiscovered;

    @Schema(description = "Active jobs posted since the start of the current UTC day", example = "24")
    private long newJobsToday;

    @Schema(description = "Active jobs scoring at or above the candidate's match threshold", example = "18")
    private long highMatchJobsCount;

    @Schema(description = "Active jobs offering verified sponsorship, visa mention, or relocation", example = "412")
    private long sponsorshipOpportunitiesCount;

    @Schema(description = "Total verified European sponsor employers indexed", example = "215")
    private long verifiedSponsorsCount;

    @Schema(description = "Jobs saved by the authenticated user", example = "7")
    private long savedJobsCount;

    @Schema(description = "Jobs marked interested by the authenticated user", example = "5")
    private long interestedJobsCount;

    @Schema(description = "Active tracked applications (excluding REJECTED and WITHDRAWN)", example = "4")
    private long activeApplicationsCount;

    @Schema(description = "Unread in-app notifications count", example = "2")
    private long unreadNotificationsCount;

    @Schema(description = "Profile, master resume, and autopilot readiness snapshot")
    private DashboardReadinessDto readiness;

    @Schema(description = "Ordered application counts across all Kanban lifecycle stages")
    private List<PipelineStageCountDto> pipelineBreakdown;

    @Schema(description = "Newest user-scoped activity items across applications, saved jobs, resumes, and alerts")
    private List<DashboardActivityItemDto> recentActivity;

    @Schema(description = "Timestamp when this dashboard summary snapshot was computed")
    private Instant generatedAt;
}

