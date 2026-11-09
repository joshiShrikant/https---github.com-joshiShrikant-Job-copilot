package com.eurojobcopilot.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to register a new EuroJob Copilot account")
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a well-formed email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    @Schema(description = "Unique candidate email address", example = "engineer@eurojobcopilot.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!.*_?\\-]).{8,64}$",
            message = "Password must be 8-64 characters and contain uppercase, lowercase, digit, and special character"
    )
    @Schema(description = "Strong account password", example = "SecureP@ssw0rd2026!")
    private String password;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    @Schema(description = "Candidate first name", example = "Alex")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    @Schema(description = "Candidate last name", example = "Mueller")
    private String lastName;
}

