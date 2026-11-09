package com.eurojobcopilot.modules.recruiter.dto;

import com.eurojobcopilot.core.validation.ValidUuid;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Request payload to manually compose a draft or record an externally sent recruiter interaction")
public class CreateRecruiterInteractionRequest {

    @ValidUuid(message = "Application UUID must be a valid canonical UUID")
    @Schema(description = "Optional linked application UUID")
    private String applicationUuid;

    @NotNull(message = "Communication channel is required")
    @Schema(description = "Communication channel", example = "EMAIL")
    private CommunicationChannel channel;

    @NotNull(message = "Message type is required")
    @Schema(description = "Message category", example = "FOLLOW_UP")
    private RecruiterMessageType messageType;

    @Size(max = 255, message = "Subject line must not exceed 255 characters")
    @Schema(description = "Optional subject line for email communications")
    private String subjectLine;

    @NotBlank(message = "Message body is required")
    @Size(max = 8000, message = "Message body must not exceed 8000 characters")
    @Schema(description = "Communication message content")
    private String messageBody;

    @Schema(description = "Optional follow-up reminder date", example = "2026-11-20")
    private LocalDate followUpReminderDate;

    @Schema(description = "When true AND explicitApprovalConfirmed is true, marks the manually composed record as already sent externally", defaultValue = "false")
    private boolean markAsSent;

    @Schema(description = "Explicit user confirmation required whenever markAsSent is true", defaultValue = "false")
    private boolean explicitApprovalConfirmed;

    @Schema(description = "Optional UTC timestamp of external dispatch when markAsSent is true")
    private Instant sentAt;
}

