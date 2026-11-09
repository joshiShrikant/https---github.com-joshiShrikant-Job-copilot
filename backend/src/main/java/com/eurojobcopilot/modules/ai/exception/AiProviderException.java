package com.eurojobcopilot.modules.ai.exception;

import com.eurojobcopilot.core.exception.BaseApiException;
import org.springframework.http.HttpStatus;

public class AiProviderException extends BaseApiException {

    public AiProviderException(String message, Throwable cause) {
        super(
                HttpStatus.BAD_GATEWAY,
                message,
                cause,
                false,
                "The upstream AI provider could not complete the analysis request. Please try again later."
        );
    }

    public AiProviderException(String message) {
        this(message, null);
    }
}

