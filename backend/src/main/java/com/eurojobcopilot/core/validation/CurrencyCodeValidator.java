package com.eurojobcopilot.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class CurrencyCodeValidator implements ConstraintValidator<ValidCurrencyCode, String> {

    private static final Set<String> VALID_ISO_CODES = Currency.getAvailableCurrencies()
            .stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    private boolean allowEmpty;

    @Override
    public void initialize(ValidCurrencyCode constraintAnnotation) {
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
        if (value.length() != 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            char c = value.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) {
                return false;
            }
        }
        return VALID_ISO_CODES.contains(value.toUpperCase(Locale.ROOT));
    }
}

