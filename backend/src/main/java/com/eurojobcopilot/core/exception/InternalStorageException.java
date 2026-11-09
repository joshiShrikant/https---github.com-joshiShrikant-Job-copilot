package com.eurojobcopilot.core.exception;

import org.springframework.http.HttpStatus;

public class InternalStorageException extends BaseApiException {

    private static final String DEFAULT_PUBLIC_MESSAGE = "Failed to process file storage";

    public InternalStorageException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, null, false, DEFAULT_PUBLIC_MESSAGE);
    }

    public InternalStorageException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause, false, DEFAULT_PUBLIC_MESSAGE);
    }

    protected InternalStorageException(String message, String publicMessage, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause, false, publicMessage);
    }
}
