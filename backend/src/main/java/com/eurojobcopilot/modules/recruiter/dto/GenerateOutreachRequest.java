package com.eurojobcopilot.modules.recruiter.dto;

import com.eurojobcopilot.core.validation.ValidUuid;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to generate an AI-assisted, strictly editable, unsent outreach draft for a recruiter")
public class GenerateOutreachRequest {

    @Builder.Default
    @NotNull(message = "Communication channel is required")
    @Schema(description = "Target channel (e.g. LINKEDIN or EMAIL)", example = "LINKEDIN", defaultValue = "LINKEDIN")
    private CommunicationChannel channel = CommunicationChannel.LINKEDIN;

    @Builder.Default
    @NotNull(message = "Message type is required")
    @Schema(description = "Outreach message category (LINKEDIN_CONNECTION, INITIAL_OUTREACH, FOLLOW_UP, POST_INTERVIEW_THANKS, etc.)",
            example = "LINKEDIN_CONNECTION", defaultValue = "INITIAL_OUTREACH")
    private RecruiterMessageType messageType = RecruiterMessageType.INITIAL_OUTREACH;

    @ValidUuid(message = "Application UUID must be a valid canonical UUID")
    @Schema(description = "Optional canonical UUID of the tracked application for contextual grounding")
    private String applicationUuid;

    @Positive(message = "Job ID must be positive")
    @Schema(description = "Optional indexed job ID for role context if applicationUuid is omitted")
    private Long jobId;

    @Size(max = 1000, message = "Personal context must not exceed 1000 characters")
    @Schema(description = "Optional candidate talking points, interview topic discussed, or custom emphasis",
            example = "Mention our discussion about SEPA Instant clearing throughput and my 30-day availability.")
    private String personalContext;

    @Schema(description = "Optional follow-up reminder date to store on the generated draft", example = "2026-11-18")
    private LocalDate followUpReminderDate;
}

