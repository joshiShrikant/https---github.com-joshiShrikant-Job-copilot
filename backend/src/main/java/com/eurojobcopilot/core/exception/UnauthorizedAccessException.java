package com.eurojobcopilot.core.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedAccessException extends BaseApiException {

    public UnauthorizedAccessException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}

