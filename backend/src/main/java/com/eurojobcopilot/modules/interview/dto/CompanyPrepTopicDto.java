package com.eurojobcopilot.modules.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Grounded employer-specific interview preparation topic backed explicitly by observed job/company evidence")
public class CompanyPrepTopicDto {

    private String topic;
    private String whyRelevant;
    private String groundingBasis;
}

