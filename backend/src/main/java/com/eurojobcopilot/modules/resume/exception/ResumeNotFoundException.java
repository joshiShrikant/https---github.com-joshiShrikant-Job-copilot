package com.eurojobcopilot.modules.resume.exception;

import com.eurojobcopilot.core.exception.ResourceNotFoundException;

public class ResumeNotFoundException extends ResourceNotFoundException {

    public ResumeNotFoundException(String message) {
        super(message);
    }
}
