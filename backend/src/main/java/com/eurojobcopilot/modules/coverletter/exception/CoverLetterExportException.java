package com.eurojobcopilot.modules.coverletter.exception;

import com.eurojobcopilot.core.exception.InternalStorageException;

public class CoverLetterExportException extends InternalStorageException {

    private static final String PUBLIC_MESSAGE = "Failed to export cover letter document";

    public CoverLetterExportException(String message, Throwable cause) {
        super(message, PUBLIC_MESSAGE, cause);
    }

    public CoverLetterExportException(String message) {
        super(message, PUBLIC_MESSAGE, null);
    }
}

