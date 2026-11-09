package com.eurojobcopilot.core.exception;

import org.springframework.http.HttpStatus;

public class BusinessValidationException extends BaseApiException {

    public BusinessValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}

