package com.eurojobcopilot.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
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
@Schema(description = "Create or update request for an education entry")
public class UpsertEducationRequest {

    @NotBlank(message = "Institution is required")
    @Size(max = 200, message = "Institution must not exceed 200 characters")
    @Schema(description = "Academic institution name", example = "Technical University of Munich")
    private String institution;

    @NotBlank(message = "Degree is required")
    @Size(max = 150, message = "Degree must not exceed 150 characters")
    @Schema(description = "Degree or certification", example = "M.Sc.")
    private String degree;

    @NotBlank(message = "Field of study is required")
    @Size(max = 150, message = "Field of study must not exceed 150 characters")
    @Schema(description = "Field of study", example = "Computer Science")
    private String fieldOfStudy;

    @NotNull(message = "Start date is required")
    @PastOrPresent(message = "Start date cannot be in the future")
    @Schema(description = "Start date", example = "2018-10-01")
    private LocalDate startDate;

    @Schema(description = "End or graduation date", example = "2020-09-30")
    private LocalDate endDate;

    @Size(max = 50, message = "Grade or GPA must not exceed 50 characters")
    @Schema(description = "Grade or GPA", example = "1.3")
    private String gradeOrGpa;

    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country must not exceed 100 characters")
    @Schema(description = "Country of institution", example = "Germany")
    private String country;
}
