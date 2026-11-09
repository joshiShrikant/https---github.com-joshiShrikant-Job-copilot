package com.eurojobcopilot.modules.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Configured deterministic matching dimension weights")
public class MatchingWeightsDto {

    @Schema(description = "Technical skills weight", example = "40.0")
    private double technicalSkills;

    @Schema(description = "Experience weight", example = "20.0")
    private double experience;

    @Schema(description = "Job title alignment weight", example = "15.0")
    private double jobTitle;

    @Schema(description = "Location & work arrangement weight", example = "10.0")
    private double location;

    @Schema(description = "Visa sponsorship compatibility weight", example = "10.0")
    private double sponsorship;

    @Schema(description = "Salary expectation weight", example = "5.0")
    private double salary;
}

