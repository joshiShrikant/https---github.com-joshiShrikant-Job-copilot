package com.eurojobcopilot.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Authentication response containing user summary and session details")
public class AuthResponse {

    @Schema(description = "Signed JWT access token")
    private String accessToken;

    @Schema(description = "Token scheme", example = "Bearer")
    private String tokenType;

    @Schema(description = "Access token TTL in seconds", example = "900")
    private Long expiresInSeconds;

    @Schema(description = "Authenticated user account summary")
    private UserSummaryDto user;

    @Schema(description = "Operation status message", example = "Login successful")
    private String message;
}

