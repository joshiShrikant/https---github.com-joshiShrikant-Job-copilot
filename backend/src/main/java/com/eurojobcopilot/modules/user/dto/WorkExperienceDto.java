package com.eurojobcopilot.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User work experience entry")
public class WorkExperienceDto {

    @Schema(description = "Work experience ID", example = "8")
    private Long id;

    @Schema(description = "Employer company name", example = "Adyen")
    private String companyName;

    @Schema(description = "Job title / designation", example = "Senior Java Engineer")
    private String designation;

    @Schema(description = "Work location", example = "Amsterdam, Netherlands")
    private String location;

    @Schema(description = "Role start date", example = "2022-03-01")
    private LocalDate startDate;

    @Schema(description = "Role end date (null when current role)", example = "2025-12-31")
    private LocalDate endDate;

    @Schema(description = "Whether this is the current role", example = "true")
    private boolean current;

    @Schema(description = "Role summary and achievements")
    private String description;

    @Schema(description = "Key technologies and skills used", example = "[\"Java\", \"Spring Boot\", \"Kafka\"]")
    private List<String> skillsUsed;
}
