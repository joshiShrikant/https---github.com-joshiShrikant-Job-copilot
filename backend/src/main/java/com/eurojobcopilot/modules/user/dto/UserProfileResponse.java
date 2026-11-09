package com.eurojobcopilot.modules.user.dto;

import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete professional profile for the authenticated user")
public class UserProfileResponse {

    @Schema(description = "Public user UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uuid;

    @Schema(description = "First name", example = "Alex")
    private String firstName;

    @Schema(description = "Last name", example = "Mueller")
    private String lastName;

    @Schema(description = "Full name", example = "Alex Mueller")
    private String fullName;

    @Schema(description = "Account email address (read-only)", example = "alex@eurojob.eu")
    private String email;

    @Schema(description = "Current job title / designation", example = "Senior Full-Stack Engineer")
    private String currentDesignation;

    @Schema(description = "Total years of professional experience", example = "6.5")
    private BigDecimal yearsOfExperience;

    @Schema(description = "Current country of residence", example = "India")
    private String currentCountry;

    @Schema(description = "Current city / location", example = "Bengaluru")
    private String currentCity;

    @Schema(description = "Minimum expected annual gross salary", example = "85000.00")
    private BigDecimal expectedSalaryMin;

    @Schema(description = "Expected salary currency (3-letter ISO 4217)", example = "EUR")
    private String expectedSalaryCurrency;

    @Schema(description = "Notice period in days", example = "30")
    private Integer noticePeriodDays;

    @Schema(description = "European work authorization status", example = "NEEDS_SPONSORSHIP")
    private WorkAuthorizationStatus workAuthorizationStatus;

    @Schema(description = "Relocation preference", example = "WILLING_TO_RELOCATE")
    private RelocationPreference relocationPreference;

    @Schema(description = "LinkedIn profile URL", example = "https://www.linkedin.com/in/alex-mueller")
    private String linkedinUrl;

    @Schema(description = "GitHub profile URL", example = "https://github.com/alex-mueller")
    private String githubUrl;

    @Schema(description = "Personal portfolio URL", example = "https://alexmueller.dev")
    private String portfolioUrl;

    @Schema(description = "Professional summary / bio")
    private String bio;

    @Schema(description = "Preferred target job titles", example = "[\"Senior Java Engineer\", \"Staff Backend Engineer\"]")
    private List<String> preferredJobTitles;

    @Schema(description = "Preferred European destination countries", example = "[\"Germany\", \"Netherlands\"]")
    private List<String> preferredCountries;

    @Schema(description = "Technical skills")
    private List<UserSkillDto> skills;

    @Schema(description = "Academic education history")
    private List<EducationDto> educations;

    @Schema(description = "Work experience history")
    private List<WorkExperienceDto> experiences;

    @Schema(description = "Profile creation timestamp")
    private Instant createdAt;

    @Schema(description = "Profile last updated timestamp")
    private Instant updatedAt;
}
