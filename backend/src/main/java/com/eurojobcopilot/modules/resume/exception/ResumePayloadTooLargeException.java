package com.eurojobcopilot.modules.resume.exception;

import com.eurojobcopilot.core.exception.PayloadTooLargeException;

public class ResumePayloadTooLargeException extends PayloadTooLargeException {

    public ResumePayloadTooLargeException(String message) {
        super(message);
    }
}
