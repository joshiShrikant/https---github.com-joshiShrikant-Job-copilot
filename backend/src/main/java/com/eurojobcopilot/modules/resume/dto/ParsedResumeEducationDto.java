package com.eurojobcopilot.modules.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Education entry extracted from a master resume")
public class ParsedResumeEducationDto {

    @NotBlank(message = "Institution is required")
    @Size(max = 200, message = "Institution must not exceed 200 characters")
    @Schema(description = "Academic institution name", example = "Technical University of Munich")
    private String institution;

    @NotBlank(message = "Degree is required")
    @Size(max = 150, message = "Degree must not exceed 150 characters")
    @Schema(description = "Degree or diploma title", example = "M.Sc.")
    private String degree;

    @NotBlank(message = "Field of study is required")
    @Size(max = 150, message = "Field of study must not exceed 150 characters")
    @Schema(description = "Academic discipline or major", example = "Computer Science")
    private String fieldOfStudy;

    @Schema(description = "Start date (required when approving & syncing to profile)", example = "2018-10-01")
    private LocalDate startDate;

    @Schema(description = "End or graduation date", example = "2020-09-30")
    private LocalDate endDate;

    @Size(max = 50, message = "Grade or GPA must not exceed 50 characters")
    @Schema(description = "Grade or GPA if stated", example = "1.3")
    private String gradeOrGpa;

    @Size(max = 100, message = "Country must not exceed 100 characters")
    @Schema(description = "Country of institution (required when approving & syncing to profile)", example = "Germany")
    private String country;
}

