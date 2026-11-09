package com.eurojobcopilot.modules.resume.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Work experience entry extracted from a master resume")
public class ParsedResumeExperienceDto {

    @NotBlank(message = "Company name is required")
    @Size(max = 200, message = "Company name must not exceed 200 characters")
    @Schema(description = "Employer name", example = "Adyen N.V.")
    private String companyName;

    @NotBlank(message = "Designation is required")
    @Size(max = 150, message = "Designation must not exceed 150 characters")
    @Schema(description = "Role title", example = "Senior Java Backend Engineer")
    private String designation;

    @Size(max = 150, message = "Location must not exceed 150 characters")
    @Schema(description = "Work location", example = "Amsterdam, Netherlands")
    private String location;

    @Schema(description = "Start date (required when approving & syncing to profile)", example = "2022-03-01")
    private LocalDate startDate;

    @Schema(description = "End date (cleared when current is true)", example = "2025-12-31")
    private LocalDate endDate;

    @JsonAlias("isCurrent")
    @Schema(description = "Whether this is an ongoing role", example = "true")
    private boolean current;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    @Schema(description = "Responsibilities and measurable achievements from resume")
    private String description;

    @Size(max = 50, message = "Maximum 50 skills allowed per experience entry")
    @Schema(description = "Technologies and tools used in this role", example = "[\"Java 21\", \"Spring Boot\", \"PostgreSQL\"]")
    private List<@NotBlank(message = "Skill entry must not be blank")
    @Size(max = 100, message = "Skill name must not exceed 100 characters") String> skillsUsed;
}

