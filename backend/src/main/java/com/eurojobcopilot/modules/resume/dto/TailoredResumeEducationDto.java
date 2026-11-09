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
@Schema(description = "Verified academic education entry carried into the tailored CV")
public class TailoredResumeEducationDto {

    @NotBlank(message = "Institution is required")
    @Size(max = 200, message = "Institution must not exceed 200 characters")
    @Schema(description = "Verified university or college name", example = "Technical University of Munich")
    private String institution;

    @NotBlank(message = "Degree is required")
    @Size(max = 150, message = "Degree must not exceed 150 characters")
    @Schema(description = "Verified degree title", example = "M.Sc.")
    private String degree;

    @NotBlank(message = "Field of study is required")
    @Size(max = 150, message = "Field of study must not exceed 150 characters")
    @Schema(description = "Verified academic field", example = "Computer Science")
    private String fieldOfStudy;

    @Schema(description = "Start date", example = "2016-10-01")
    private LocalDate startDate;

    @Schema(description = "Completion date", example = "2018-09-30")
    private LocalDate endDate;

    @Size(max = 50, message = "Grade or GPA must not exceed 50 characters")
    @Schema(description = "Grade or GPA if recorded", example = "1.3 (Sehr Gut)")
    private String gradeOrGpa;

    @Size(max = 100, message = "Country must not exceed 100 characters")
    @Schema(description = "Country of study", example = "Germany")
    private String country;
}

