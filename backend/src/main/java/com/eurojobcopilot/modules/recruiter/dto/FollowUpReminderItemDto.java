package com.eurojobcopilot.modules.recruiter.dto;

import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageStatus;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Unified follow-up reminder entry from either a tracked application or a recruiter communication")
public class FollowUpReminderItemDto {

    @Schema(description = "Source entity type (`APPLICATION` or `RECRUITER_INTERACTION`)", example = "RECRUITER_INTERACTION")
    private String sourceType;

    @Schema(description = "Urgency bucket (`OVERDUE`, `DUE_TODAY`, `UPCOMING`, `COMPLETED`)", example = "DUE_TODAY")
    private String urgency;

    @Schema(description = "Scheduled reminder date", example = "2026-11-12")
    private LocalDate reminderDate;

    @Schema(description = "Whether the reminder has been marked completed")
    private boolean completed;

    @Schema(description = "Linked application UUID if applicable")
    private String applicationUuid;

    @Schema(description = "Linked application current stage if applicable")
    private ApplicationStatus applicationStatus;

    @Schema(description = "Linked recruiter ID if applicable")
    private Long recruiterId;

    @Schema(description = "Linked interaction ID if sourceType is RECRUITER_INTERACTION")
    private Long interactionId;

    @Schema(description = "Contact or employer label", example = "Sophie van Dijk · Adyen N.V.")
    private String contactLabel;

    @Schema(description = "Role title or summary context", example = "Staff Java Clearing Architect")
    private String roleTitle;

    @Schema(description = "Communication channel if sourceType is RECRUITER_INTERACTION")
    private CommunicationChannel channel;

    @Schema(description = "Message type if sourceType is RECRUITER_INTERACTION")
    private RecruiterMessageType messageType;

    @Schema(description = "Message approval state if sourceType is RECRUITER_INTERACTION")
    private RecruiterMessageStatus messageStatus;
}

