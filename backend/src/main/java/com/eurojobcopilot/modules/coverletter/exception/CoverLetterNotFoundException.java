package com.eurojobcopilot.modules.coverletter.exception;

import com.eurojobcopilot.core.exception.ResourceNotFoundException;

public class CoverLetterNotFoundException extends ResourceNotFoundException {

    public CoverLetterNotFoundException(String message) {
        super(message);
    }
}

