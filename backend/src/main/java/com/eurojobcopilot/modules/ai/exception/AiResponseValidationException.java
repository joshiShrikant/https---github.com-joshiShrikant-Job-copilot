package com.eurojobcopilot.modules.ai.exception;

import com.eurojobcopilot.core.exception.BaseApiException;
import org.springframework.http.HttpStatus;

public class AiResponseValidationException extends BaseApiException {

    public AiResponseValidationException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }

    public AiResponseValidationException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause, true, message);
    }
}

