package com.eurojobcopilot.modules.user.exception;

import com.eurojobcopilot.core.exception.ResourceNotFoundException;

public class ProfileResourceNotFoundException extends ResourceNotFoundException {

    public ProfileResourceNotFoundException(String message) {
        super(message);
    }
}
