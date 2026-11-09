package com.eurojobcopilot.modules.user.exception;

import com.eurojobcopilot.core.exception.ResourceConflictException;

public class ProfileConflictException extends ResourceConflictException {

    public ProfileConflictException(String message) {
        super(message);
    }
}
