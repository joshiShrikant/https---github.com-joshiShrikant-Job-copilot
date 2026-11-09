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
@Schema(description = "Optional refresh token payload when refresh_token cookie is not used")
public class RefreshTokenRequest {

    @Schema(description = "Opaque refresh token string")
    private String refreshToken;
}

