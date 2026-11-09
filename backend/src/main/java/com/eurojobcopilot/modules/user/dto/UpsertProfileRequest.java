package com.eurojobcopilot.modules.user.dto;

import com.eurojobcopilot.core.validation.ValidCurrencyCode;
import com.eurojobcopilot.core.validation.ValidHttpUrl;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create or update request for the authenticated user's professional profile")
public class UpsertProfileRequest {

    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    @Pattern(regexp = ".*\\S.*", message = "First name must not be blank")
    @Schema(description = "User first name", example = "Alex")
    private String firstName;

    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    @Pattern(regexp = ".*\\S.*", message = "Last name must not be blank")
    @Schema(description = "User last name", example = "Mueller")
    private String lastName;

    @Size(max = 150, message = "Current designation must not exceed 150 characters")
    @Schema(description = "Current job title or role", example = "Senior Java Backend Engineer")
    private String currentDesignation;

    @DecimalMin(value = "0.0", message = "Years of experience must be 0 or greater")
    @DecimalMax(value = "70.0", message = "Years of experience must not exceed 70")
    @Digits(integer = 3, fraction = 1, message = "Years of experience must have at most 3 integer digits and 1 decimal place")
    @Schema(description = "Total years of experience", example = "6.5")
    private BigDecimal yearsOfExperience;

    @Size(max = 100, message = "Current country must not exceed 100 characters")
    @Schema(description = "Current country of residence", example = "India")
    private String currentCountry;

    @Size(max = 100, message = "Current city must not exceed 100 characters")
    @Schema(description = "Current city / location", example = "Bengaluru")
    private String currentCity;

    @DecimalMin(value = "0.0", message = "Expected minimum salary must be 0 or greater")
    @DecimalMax(value = "99999999.99", message = "Expected minimum salary exceeds allowed maximum")
    @Digits(integer = 10, fraction = 2, message = "Expected salary must have at most 10 integer digits and 2 decimal places")
    @Schema(description = "Minimum expected annual gross salary", example = "85000.00")
    private BigDecimal expectedSalaryMin;

    @ValidCurrencyCode(message = "Expected salary currency must be a 3-letter ISO currency code")
    @Schema(description = "3-letter ISO currency code", example = "EUR")
    private String expectedSalaryCurrency;

    @Min(value = 0, message = "Notice period days must be 0 or greater")
    @Max(value = 365, message = "Notice period days must not exceed 365")
    @Schema(description = "Notice period in days", example = "30")
    private Integer noticePeriodDays;

    @Schema(description = "European work authorization status", example = "NEEDS_SPONSORSHIP")
    private WorkAuthorizationStatus workAuthorizationStatus;

    @Schema(description = "Relocation preference", example = "WILLING_TO_RELOCATE")
    private RelocationPreference relocationPreference;

    @Size(max = 255, message = "LinkedIn URL must not exceed 255 characters")
    @ValidHttpUrl(message = "LinkedIn URL must be a valid HTTP or HTTPS URL")
    @Schema(description = "LinkedIn profile URL", example = "https://www.linkedin.com/in/alex-mueller")
    private String linkedinUrl;

    @Size(max = 255, message = "GitHub URL must not exceed 255 characters")
    @ValidHttpUrl(message = "GitHub URL must be a valid HTTP or HTTPS URL")
    @Schema(description = "GitHub profile URL", example = "https://github.com/alex-mueller")
    private String githubUrl;

    @Size(max = 255, message = "Portfolio URL must not exceed 255 characters")
    @ValidHttpUrl(message = "Portfolio URL must be a valid HTTP or HTTPS URL")
    @Schema(description = "Portfolio website URL", example = "https://alexmueller.dev")
    private String portfolioUrl;

    @Size(max = 2000, message = "Bio must not exceed 2000 characters")
    @Schema(description = "Professional bio / summary")
    private String bio;

    @Size(max = 25, message = "Maximum 25 preferred job titles allowed")
    @Schema(description = "Preferred job titles", example = "[\"Senior Java Engineer\", \"Staff Backend Engineer\"]")
    private List<@NotBlank(message = "Preferred job title must not be blank")
    @Size(max = 100, message = "Preferred job title must not exceed 100 characters") String> preferredJobTitles;

    @Size(max = 30, message = "Maximum 30 preferred countries allowed")
    @Schema(description = "Preferred European destination countries", example = "[\"Germany\", \"Netherlands\"]")
    private List<@NotBlank(message = "Preferred country must not be blank")
    @Size(max = 100, message = "Preferred country must not exceed 100 characters") String> preferredCountries;
}
