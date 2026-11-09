package com.eurojobcopilot.modules.recruiter.dto;

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
@Schema(description = "Aggregated follow-up reminders across recruiter interactions and tracked applications")
public class FollowUpRemindersResponse {

    @Schema(description = "Count of incomplete overdue reminders", example = "1")
    private long overdueCount;

    @Schema(description = "Count of incomplete reminders due today", example = "2")
    private long dueTodayCount;

    @Schema(description = "Count of incomplete upcoming reminders", example = "3")
    private long upcomingCount;

    @Schema(description = "Total incomplete pending reminders", example = "6")
    private long totalPendingCount;

    @Schema(description = "Ordered list of follow-up reminder items")
    private List<FollowUpReminderItemDto> items;
}

