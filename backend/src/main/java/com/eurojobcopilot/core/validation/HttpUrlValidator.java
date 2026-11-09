package com.eurojobcopilot.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

public class HttpUrlValidator implements ConstraintValidator<ValidHttpUrl, String> {

    private static final Pattern HOST_PATTERN =
            Pattern.compile("^(?i)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    private static final Pattern WHITESPACE_OR_CONTROL = Pattern.compile("[\\s\\p{Cntrl}]");

    private boolean allowEmpty;

    @Override
    public void initialize(ValidHttpUrl constraintAnnotation) {
        this.allowEmpty = constraintAnnotation.allowEmpty();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value.isEmpty()) {
            return allowEmpty;
        }
        if (WHITESPACE_OR_CONTROL.matcher(value).find()) {
            return false;
        }

        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return false;
            }
            if (uri.getUserInfo() != null) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || !HOST_PATTERN.matcher(host).matches()) {
                return false;
            }
            int port = uri.getPort();
            return port == -1 || (port >= 1 && port <= 65535);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}

