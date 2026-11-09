package com.eurojobcopilot.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to authenticate an existing user")
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a well-formed email address")
    @Schema(description = "Registered email address", example = "engineer@eurojobcopilot.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password", example = "SecureP@ssw0rd2026!")
    private String password;
}

