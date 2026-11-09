package com.eurojobcopilot.modules.recruiter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Recruiter contact profile with communication summary and contact history")
public class RecruiterResponse {

    @Schema(description = "Recruiter record ID", example = "7")
    private Long id;

    @Schema(description = "Recruiter full name", example = "Sophie van Dijk")
    private String fullName;

    @Schema(description = "Recruiter role or title", example = "Senior Technical Recruiter")
    private String roleTitle;

    @Schema(description = "Linked indexed employer company ID")
    private Long companyId;

    @Schema(description = "Company name", example = "Adyen N.V.")
    private String companyName;

    @Schema(description = "Whether linked company is a verified sponsor")
    private boolean companyVerifiedSponsor;

    @Schema(description = "Recruiter email address")
    private String email;

    @Schema(description = "Recruiter LinkedIn profile URL")
    private String linkedinUrl;

    @Schema(description = "Recruiter phone number")
    private String phone;

    @Schema(description = "Private recruiter context notes")
    private String notes;

    @Schema(description = "Total logged/drafted interactions count", example = "3")
    private long interactionsCount;

    @Schema(description = "Timestamp of latest recorded interaction")
    private Instant lastInteractionAt;

    @Schema(description = "Earliest upcoming/overdue incomplete follow-up reminder date")
    private LocalDate nextFollowUpReminderDate;

    @Schema(description = "Count of incomplete pending/overdue follow-up reminders")
    private long pendingRemindersCount;

    @Schema(description = "Chronological communication history (populated in detail view)")
    private List<RecruiterInteractionDto> interactions;

    @Schema(description = "Creation UTC timestamp")
    private Instant createdAt;

    @Schema(description = "Last update UTC timestamp")
    private Instant updatedAt;
}

