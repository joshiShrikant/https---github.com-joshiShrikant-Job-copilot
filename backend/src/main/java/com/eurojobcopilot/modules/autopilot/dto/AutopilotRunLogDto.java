package com.eurojobcopilot.modules.autopilot.dto;

import com.eurojobcopilot.modules.autopilot.entity.AutopilotRunStatus;
import com.eurojobcopilot.modules.autopilot.entity.AutopilotTriggerType;
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
@Schema(description = "Audited execution record of a scheduled or manual job search autopilot run")
public class AutopilotRunLogDto {

    private Long id;
    private AutopilotTriggerType triggerType;
    private AutopilotRunStatus status;
    private boolean ingestionTriggered;
    private int jobsEvaluated;
    private int newMatchesFound;
    private int recommendedJobsCount;
    private int notificationsCreated;
    private boolean emailDispatched;
    private String summaryMessage;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
}

