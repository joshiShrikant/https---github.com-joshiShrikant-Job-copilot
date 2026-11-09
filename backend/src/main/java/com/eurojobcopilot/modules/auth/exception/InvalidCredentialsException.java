package com.eurojobcopilot.modules.auth.exception;

import com.eurojobcopilot.core.exception.UnauthorizedAccessException;

public class InvalidCredentialsException extends UnauthorizedAccessException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
