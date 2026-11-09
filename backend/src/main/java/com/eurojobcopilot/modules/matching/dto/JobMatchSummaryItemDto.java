package com.eurojobcopilot.modules.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Compact summary of a scored job returned after batch recalculation")
public class JobMatchSummaryItemDto {

    @Schema(description = "Numeric job identifier", example = "1042")
    private Long jobId;

    @Schema(description = "Job posting title", example = "Senior Java Backend Engineer")
    private String title;

    @Schema(description = "Employer company name", example = "Adyen N.V.")
    private String companyName;

    @Schema(description = "Primary country", example = "Netherlands")
    private String country;

    @Schema(description = "Rounded overall match percentage (0..100)", example = "86")
    private Integer matchPercentage;

    @Schema(description = "Composite weighted match score (0..100)", example = "86.25")
    private BigDecimal finalScore;

    @Schema(description = "Experience compatibility verdict", example = "Strong Fit · 6.0 yrs meets 5.0+ yrs (Senior)")
    private String experienceCompatibility;
}

