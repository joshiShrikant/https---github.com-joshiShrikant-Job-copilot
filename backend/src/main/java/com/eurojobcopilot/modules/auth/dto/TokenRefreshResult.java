package com.eurojobcopilot.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Internal service result for rotated refresh token exchange.
 */
@Getter
@AllArgsConstructor
public class TokenRefreshResult {

    private final TokenRefreshResponse response;
    private final String rawRefreshToken;
}

