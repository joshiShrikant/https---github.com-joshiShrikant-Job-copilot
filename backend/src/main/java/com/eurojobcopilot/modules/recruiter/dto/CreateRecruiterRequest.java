package com.eurojobcopilot.modules.recruiter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to create a recruiter contact record")
public class CreateRecruiterRequest {

    @NotBlank(message = "Recruiter full name is required")
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    @Schema(description = "Recruiter full name", example = "Sophie van Dijk")
    private String fullName;

    @Size(max = 120, message = "Role title must not exceed 120 characters")
    @Schema(description = "Recruiter role or specialty", example = "Senior Technical Recruiter")
    private String roleTitle;

    @Positive(message = "Company ID must be positive")
    @Schema(description = "Optional linked indexed employer ID")
    private Long companyId;

    @Size(max = 200, message = "Company name must not exceed 200 characters")
    @Schema(description = "Employer or agency company name", example = "Adyen N.V.")
    private String companyName;

    @Email(message = "Email must be a valid email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    @Schema(description = "Recruiter email address", example = "sophie.vandijk@adyen.example")
    private String email;

    @Size(max = 255, message = "LinkedIn URL must not exceed 255 characters")
    @Schema(description = "Recruiter LinkedIn profile URL", example = "https://www.linkedin.com/in/sophievandijk")
    private String linkedinUrl;

    @Size(max = 50, message = "Phone number must not exceed 50 characters")
    @Schema(description = "Optional contact phone number", example = "+31 20 123 4567")
    private String phone;

    @Size(max = 5000, message = "Notes must not exceed 5000 characters")
    @Schema(description = "Private recruiter context, hiring team details, or conversation notes")
    private String notes;
}

