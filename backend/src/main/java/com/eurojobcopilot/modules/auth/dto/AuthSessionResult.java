package com.eurojobcopilot.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Internal service result carrying API payload plus raw refresh token for HttpOnly cookie attachment.
 */
@Getter
@AllArgsConstructor
public class AuthSessionResult {

    private final AuthResponse response;
    private final String rawRefreshToken;
}

