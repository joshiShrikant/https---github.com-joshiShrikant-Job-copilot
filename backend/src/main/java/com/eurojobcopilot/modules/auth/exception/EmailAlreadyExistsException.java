package com.eurojobcopilot.modules.auth.exception;

import com.eurojobcopilot.core.exception.ResourceConflictException;

public class EmailAlreadyExistsException extends ResourceConflictException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
