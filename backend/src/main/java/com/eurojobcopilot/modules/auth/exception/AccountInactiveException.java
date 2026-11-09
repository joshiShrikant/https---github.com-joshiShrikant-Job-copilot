package com.eurojobcopilot.modules.auth.exception;

import com.eurojobcopilot.core.exception.UnauthorizedAccessException;

public class AccountInactiveException extends UnauthorizedAccessException {

    public AccountInactiveException(String message) {
        super(message);
    }
}
