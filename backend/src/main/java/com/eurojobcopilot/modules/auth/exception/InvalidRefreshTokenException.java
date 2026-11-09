package com.eurojobcopilot.modules.auth.exception;

import com.eurojobcopilot.core.exception.UnauthorizedAccessException;

public class InvalidRefreshTokenException extends UnauthorizedAccessException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
