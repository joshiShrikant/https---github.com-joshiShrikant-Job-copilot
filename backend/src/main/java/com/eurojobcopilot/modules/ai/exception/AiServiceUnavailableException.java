package com.eurojobcopilot.modules.ai.exception;

import com.eurojobcopilot.core.exception.BaseApiException;
import org.springframework.http.HttpStatus;

public class AiServiceUnavailableException extends BaseApiException {

    public AiServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}

