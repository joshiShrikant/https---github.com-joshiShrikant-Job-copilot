package com.eurojobcopilot.modules.recruiter.dto;

import com.eurojobcopilot.core.validation.ValidUuid;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to edit an AI/manual outreach draft, approve it for manual dispatch, explicitly mark it as sent, or update its follow-up reminder")
public class UpdateRecruiterInteractionRequest {

    @ValidUuid(message = "Application UUID must be a valid canonical UUID")
    @Schema(description = "Optional updated application UUID link")
    private String applicationUuid;

    @Schema(description = "When true, unlinks the associated application", defaultValue = "false")
    private boolean clearApplication;

    @Schema(description = "Updated communication channel")
    private CommunicationChannel channel;

    @Schema(description = "Updated message category")
    private RecruiterMessageType messageType;

    @Size(max = 255, message = "Subject line must not exceed 255 characters")
    @Schema(description = "Updated subject line")
    private String subjectLine;

    @Size(max = 8000, message = "Message body must not exceed 8000 characters")
    @Schema(description = "Updated editable message content")
    private String messageBody;

    @Schema(description = "Updated follow-up reminder date")
    private LocalDate followUpReminderDate;

    @Schema(description = "When true, clears followUpReminderDate", defaultValue = "false")
    private boolean clearFollowUpReminderDate;

    @Schema(description = "Whether the follow-up reminder has been completed")
    private Boolean reminderCompleted;

    @Builder.Default
    @Schema(description = "Draft workflow action: SAVE_DRAFT (keeps unsent), APPROVE (marks reviewed/ready without auto-sending), or MARK_SENT (requires explicitApprovalConfirmed=true)",
            example = "SAVE_DRAFT", defaultValue = "SAVE_DRAFT")
    private RecruiterInteractionAction action = RecruiterInteractionAction.SAVE_DRAFT;

    @Schema(description = "Explicit user approval flag required when action is MARK_SENT", defaultValue = "false")
    private boolean explicitApprovalConfirmed;

    @Schema(description = "Optional UTC timestamp recorded when action is MARK_SENT")
    private Instant sentAt;
}

