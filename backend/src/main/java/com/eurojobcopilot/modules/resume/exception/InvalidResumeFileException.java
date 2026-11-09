package com.eurojobcopilot.modules.resume.exception;

import com.eurojobcopilot.core.exception.BusinessValidationException;

public class InvalidResumeFileException extends BusinessValidationException {

    public InvalidResumeFileException(String message) {
        super(message);
    }
}
