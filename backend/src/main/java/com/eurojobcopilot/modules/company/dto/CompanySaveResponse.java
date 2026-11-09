package com.eurojobcopilot.modules.company.dto;

import com.eurojobcopilot.modules.company.entity.CompanyPriorityTier;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@Schema(description = "User-scoped target company bookmark state after save or remove action")
public class CompanySaveResponse {

    @Schema(description = "Company identifier", example = "12")
    private Long companyId;

    @Schema(description = "Company name", example = "Adyen")
    private String companyName;

    @JsonProperty("isSaved")
    @Schema(name = "isSaved", description = "Whether the company is currently saved for the authenticated user", example = "true")
    private boolean saved;

    @Schema(description = "Assigned target priority tier when saved", example = "TOP_PRIORITY")
    private CompanyPriorityTier priorityTier;

    @Schema(description = "Saved personal notes when present")
    private String notes;

    @Schema(description = "UTC timestamp when the bookmark was created or retained")
    private Instant savedAt;
}

