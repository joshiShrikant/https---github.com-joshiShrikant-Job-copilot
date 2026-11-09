package com.eurojobcopilot.modules.resume.exception;

import com.eurojobcopilot.core.exception.ResourceConflictException;

public class ResumeConflictException extends ResourceConflictException {

    public ResumeConflictException(String message) {
        super(message);
    }
}
