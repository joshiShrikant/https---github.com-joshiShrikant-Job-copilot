package com.eurojobcopilot.modules.user.dto;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.core.validation.ValidCurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Create or update request for job search, sponsorship, and autopilot preferences")
public class UpdateJobPreferencesRequest {

    @Size(max = 25, message = "Maximum 25 preferred job titles allowed")
    @Schema(description = "Preferred target job titles", example = "[\"Senior Java Engineer\", \"Full-Stack Engineer\"]")
    private List<@NotBlank(message = "Preferred job title must not be blank")
    @Size(max = 100, message = "Preferred job title must not exceed 100 characters") String> preferredJobTitles;

    @Size(max = 30, message = "Maximum 30 preferred countries allowed")
    @Schema(description = "Preferred European destination countries", example = "[\"Germany\", \"Netherlands\"]")
    private List<@NotBlank(message = "Preferred country must not be blank")
    @Size(max = 100, message = "Preferred country must not exceed 100 characters") String> preferredCountries;

    @Size(max = 30, message = "Maximum 30 preferred cities allowed")
    @Schema(description = "Preferred European destination cities", example = "[\"Berlin\", \"Amsterdam\"]")
    private List<@NotBlank(message = "Preferred city must not be blank")
    @Size(max = 100, message = "Preferred city must not exceed 100 characters") String> preferredCities;

    @DecimalMin(value = "0.0", message = "Minimum salary must be 0 or greater")
    @DecimalMax(value = "99999999.99", message = "Minimum salary exceeds allowed maximum")
    @Digits(integer = 10, fraction = 2, message = "Minimum salary must have at most 10 integer digits and 2 decimal places")
    @Schema(description = "Minimum expected annual gross salary", example = "85000.00")
    private BigDecimal minSalary;

    @ValidCurrencyCode(message = "Salary currency must be a valid 3-letter ISO currency code")
    @Schema(description = "3-letter ISO currency code", example = "EUR")
    private String salaryCurrency;

    @Schema(description = "Whether the candidate requires visa sponsorship", example = "true")
    private Boolean requiresSponsorship;

    @Schema(description = "Whether the candidate requires relocation support", example = "true")
    private Boolean requiresRelocation;

    @Schema(description = "Preferred work arrangement", example = "HYBRID")
    private WorkArrangement workArrangement;

    @Schema(description = "Restrict recommendations to English-speaking positions", example = "true")
    private Boolean englishSpeakingOnly;

    @Min(value = 0, message = "Minimum match score must be between 0 and 100")
    @Max(value = 100, message = "Minimum match score must be between 0 and 100")
    @Schema(description = "Minimum match percentage threshold (0-100)", example = "70")
    private Integer minMatchScore;

    @Schema(description = "Enable background autopilot match alerts and digests", example = "true")
    private Boolean autopilotEnabled;

    @Schema(description = "Temporarily pause autopilot scanning without disabling saved preferences", example = "false")
    private Boolean autopilotPaused;

    @Schema(description = "Opt in to email delivery of high-match autopilot notifications when mail relay is enabled", example = "false")
    private Boolean emailNotificationsEnabled;
}

