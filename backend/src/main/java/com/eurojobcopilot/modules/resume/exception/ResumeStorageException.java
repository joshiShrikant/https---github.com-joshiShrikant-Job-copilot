package com.eurojobcopilot.modules.resume.exception;

import com.eurojobcopilot.core.exception.InternalStorageException;

public class ResumeStorageException extends InternalStorageException {

    private static final String PUBLIC_MESSAGE = "Failed to process resume file storage";

    public ResumeStorageException(String message, Throwable cause) {
        super(message, PUBLIC_MESSAGE, cause);
    }

    public ResumeStorageException(String message) {
        super(message, PUBLIC_MESSAGE, null);
    }
}
