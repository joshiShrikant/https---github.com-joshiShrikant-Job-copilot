package com.eurojobcopilot.modules.user.exception;

import com.eurojobcopilot.core.exception.BusinessValidationException;

public class InvalidProfileDataException extends BusinessValidationException {

    public InvalidProfileDataException(String message) {
        super(message);
    }
}
