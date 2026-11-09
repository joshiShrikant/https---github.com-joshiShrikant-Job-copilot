package com.eurojobcopilot.core.exception;

import org.springframework.http.HttpStatus;

public class ResourceConflictException extends BaseApiException {

    public ResourceConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}

