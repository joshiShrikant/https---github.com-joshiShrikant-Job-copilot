package com.eurojobcopilot.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User education entry")
public class EducationDto {

    @Schema(description = "Education ID", example = "3")
    private Long id;

    @Schema(description = "Academic institution name", example = "Technical University of Munich")
    private String institution;

    @Schema(description = "Degree obtained", example = "M.Sc.")
    private String degree;

    @Schema(description = "Field of study", example = "Computer Science")
    private String fieldOfStudy;

    @Schema(description = "Start date", example = "2018-10-01")
    private LocalDate startDate;

    @Schema(description = "End date (null if ongoing)", example = "2020-09-30")
    private LocalDate endDate;

    @Schema(description = "Grade or GPA", example = "1.3")
    private String gradeOrGpa;

    @Schema(description = "Country of study", example = "Germany")
    private String country;
}
