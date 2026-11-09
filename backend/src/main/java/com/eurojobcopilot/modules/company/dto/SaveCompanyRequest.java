package com.eurojobcopilot.modules.company.dto;

import com.eurojobcopilot.modules.company.entity.CompanyPriorityTier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional priority tier and personal notes when bookmarking a target employer")
public class SaveCompanyRequest {

    @Schema(description = "Target priority classification (defaults to TARGET when omitted)", example = "TOP_PRIORITY")
    private CompanyPriorityTier priorityTier;

    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    @Schema(description = "Optional personal notes about hiring cycle, referrals, or visa policy", maxLength = 2000, example = "NL IND sponsor; ask hiring team about Java 21 openings")
    private String notes;
}

