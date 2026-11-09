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
@Schema(description = "Optional logout request payload when not using HttpOnly cookie exclusively")
public class LogoutRequest {

    @Schema(description = "Opaque refresh/session token to invalidate if not passed via HttpOnly cookie")
    private String refreshToken;

    @Builder.Default
    @Schema(description = "If true, revokes all active refresh/session tokens for the user", example = "false")
    private boolean revokeAllSessions = false;
}

