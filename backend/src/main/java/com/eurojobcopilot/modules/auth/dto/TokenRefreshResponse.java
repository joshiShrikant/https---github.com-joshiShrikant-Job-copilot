package com.eurojobcopilot.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Refreshed JWT access token payload")
public class TokenRefreshResponse {

    @Schema(description = "Newly issued JWT access token")
    private String accessToken;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Access token TTL in seconds", example = "900")
    private Long expiresInSeconds;
}

