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
@Schema(description = "Generated editable outreach draft with grounding verification metadata and explicit approval guarantee")
public class OutreachDraftResponse {

    @Schema(description = "Persisted unsent interaction record (status=DRAFT, sentAt=null)")
    private RecruiterInteractionDto interaction;

    @Schema(description = "Verified talking points grounded in candidate profile and target role context")
    private List<String> keyTalkingPoints;

    @Schema(description = "Suggested number of days until follow-up", example = "5")
    private Integer suggestedFollowUpDays;

    @Schema(description = "Grounding notices or character-limit observations")
    private List<String> warnings;

    @Schema(description = "Always true: explicit user approval is required before marking or sending outreach", example = "true")
    private boolean requiresManualApproval;

    @Schema(description = "Always false: EuroJob Copilot never dispatches emails or LinkedIn messages automatically", example = "false")
    private boolean autoSent;
}

