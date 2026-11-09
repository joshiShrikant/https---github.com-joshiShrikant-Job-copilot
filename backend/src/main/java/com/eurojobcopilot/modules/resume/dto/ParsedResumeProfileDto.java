package com.eurojobcopilot.modules.resume.dto;

import com.eurojobcopilot.core.validation.ValidHttpUrl;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Parsed candidate profile highlights extracted from master resume")
public class ParsedResumeProfileDto {

    @Size(max = 200, message = "Full name must not exceed 200 characters")
    @Schema(description = "Candidate full name if printed in resume", example = "Elena Weber")
    private String fullName;

    @Size(max = 150, message = "Current designation must not exceed 150 characters")
    @Schema(description = "Current or target professional designation", example = "Senior Java Backend Architect")
    private String currentDesignation;

    @DecimalMin(value = "0.0", message = "Years of experience must be 0 or greater")
    @DecimalMax(value = "70.0", message = "Years of experience must not exceed 70")
    @Digits(integer = 3, fraction = 1, message = "Years of experience must have at most 3 integer digits and 1 decimal place")
    @Schema(description = "Total years of professional experience", example = "7.5")
    private BigDecimal yearsOfExperience;

    @Size(max = 100, message = "Current country must not exceed 100 characters")
    @Schema(description = "Current country if stated in resume", example = "Germany")
    private String currentCountry;

    @Size(max = 100, message = "Current city must not exceed 100 characters")
    @Schema(description = "Current city if stated in resume", example = "Berlin")
    private String currentCity;

    @Size(max = 255, message = "LinkedIn URL must not exceed 255 characters")
    @ValidHttpUrl(message = "LinkedIn URL must be a valid HTTP or HTTPS URL")
    @Schema(description = "LinkedIn URL if explicitly present in resume", example = "https://www.linkedin.com/in/elena-weber")
    private String linkedinUrl;

    @Size(max = 255, message = "GitHub URL must not exceed 255 characters")
    @ValidHttpUrl(message = "GitHub URL must be a valid HTTP or HTTPS URL")
    @Schema(description = "GitHub URL if explicitly present in resume", example = "https://github.com/elena-weber")
    private String githubUrl;

    @Size(max = 255, message = "Portfolio URL must not exceed 255 characters")
    @ValidHttpUrl(message = "Portfolio URL must be a valid HTTP or HTTPS URL")
    @Schema(description = "Portfolio or personal website URL if explicitly present in resume", example = "https://elenaweber.dev")
    private String portfolioUrl;

    @Size(max = 2000, message = "Bio must not exceed 2000 characters")
    @Schema(description = "Professional summary or bio grounded in the resume")
    private String bio;

    @Schema(description = "Work authorization status only when explicitly stated in the resume", example = "EU_CITIZEN")
    private WorkAuthorizationStatus workAuthorizationStatus;

    @Schema(description = "Relocation preference only when explicitly stated in the resume", example = "ALREADY_IN_EUROPE")
    private RelocationPreference relocationPreference;
}

