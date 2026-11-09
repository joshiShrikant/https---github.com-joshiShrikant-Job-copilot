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
@Schema(description = "Tailored work experience entry preserving verified employer/dates while sharpening bullets for target JD")
public class TailoredResumeExperienceDto {

    @NotBlank(message = "Company name is required")
    @Size(max = 200, message = "Company name must not exceed 200 characters")
    @Schema(description = "Verified employer name", example = "Adyen N.V.")
    private String companyName;

    @NotBlank(message = "Designation is required")
    @Size(max = 150, message = "Designation must not exceed 150 characters")
    @Schema(description = "Verified role title", example = "Senior Java Backend Engineer")
    private String designation;

    @Size(max = 150, message = "Location must not exceed 150 characters")
    @Schema(description = "Work location", example = "Amsterdam, Netherlands")
    private String location;

    @Schema(description = "Verified start date", example = "2022-03-01")
    private LocalDate startDate;

    @Schema(description = "Verified end date (null when current is true)", example = "2025-12-31")
    private LocalDate endDate;

    @JsonAlias("isCurrent")
    @Schema(description = "Whether this is a current ongoing position", example = "true")
    private boolean current;

    @Size(max = 20, message = "Maximum 20 original bullets allowed")
    @Schema(description = "Original factual bullets/statements from candidate profile or master CV")
    private List<@NotBlank(message = "Bullet must not be blank")
    @Size(max = 600, message = "Bullet must not exceed 600 characters") String> originalBullets;

    @Size(max = 15, message = "Maximum 15 tailored bullets allowed per experience")
    @Schema(description = "ATS-sharpened achievement bullets strictly grounded in original experience evidence")
    private List<@NotBlank(message = "Tailored bullet must not be blank")
    @Size(max = 600, message = "Tailored bullet must not exceed 600 characters") String> tailoredBullets;

    @Size(max = 25, message = "Maximum 25 matched keywords allowed per experience")
    @Schema(description = "Target JD keywords authentically reflected in this role")
    private List<@NotBlank(message = "Keyword must not be blank")
    @Size(max = 100, message = "Keyword must not exceed 100 characters") String> matchedKeywords;

    @Schema(description = "Role relevance score (0-100) relative to target job description", example = "92")
    private Integer relevanceScore;
}

