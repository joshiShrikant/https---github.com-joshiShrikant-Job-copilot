package com.eurojobcopilot.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Root runtime exception for all domain/API errors mapped consistently by {@link GlobalExceptionHandler}.
 */
@Getter
public abstract class BaseApiException extends RuntimeException {

    private final HttpStatus status;
    private final boolean exposeMessage;
    private final String publicMessage;

    protected BaseApiException(HttpStatus status, String message) {
        this(status, message, null, true, message);
    }

    protected BaseApiException(HttpStatus status, String message, Throwable cause) {
        this(status, message, cause, !status.is5xxServerError(), message);
    }

    protected BaseApiException(
            HttpStatus status,
            String message,
            Throwable cause,
            boolean exposeMessage,
            String publicMessage
    ) {
        super(message, cause);
        this.status = status;
        this.exposeMessage = exposeMessage;
        this.publicMessage = publicMessage;
    }

    public String getClientSafeMessage() {
        if (exposeMessage) {
            return getMessage();
        }
        return (publicMessage != null && !publicMessage.isBlank())
                ? publicMessage
                : "An internal server error occurred";
    }
}

