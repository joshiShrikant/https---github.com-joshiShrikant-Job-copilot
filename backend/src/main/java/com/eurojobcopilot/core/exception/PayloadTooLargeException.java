package com.eurojobcopilot.core.exception;

import org.springframework.http.HttpStatus;

public class PayloadTooLargeException extends BaseApiException {

    public PayloadTooLargeException(String message) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }
}

