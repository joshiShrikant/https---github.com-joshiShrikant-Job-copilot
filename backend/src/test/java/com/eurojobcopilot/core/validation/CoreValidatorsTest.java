package com.eurojobcopilot.core.validation;

import jakarta.validation.Payload;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;

class CoreValidatorsTest {

    @Test
    void uuidValidator_validatesCanonicalUuidsOnly() {
        UuidValidator validator = new UuidValidator();

        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("550e8400-e29b-41d4-a716-446655440000", null)).isTrue();
        assertThat(validator.isValid("550E8400-E29B-41D4-A716-446655440000", null)).isTrue();

        assertThat(validator.isValid("   ", null)).isFalse();
        assertThat(validator.isValid("not-a-uuid", null)).isFalse();
        assertThat(validator.isValid("1-2-3-4-5", null)).isFalse();
        assertThat(validator.isValid("550e8400-e29b-41d4-a716-44665544000z", null)).isFalse();
    }

    @Test
    void httpUrlValidator_enforcesSafeHttpAndHttpsUrls() {
        HttpUrlValidator optionalValidator = new HttpUrlValidator();
        optionalValidator.initialize(createValidHttpUrlAnnotation(true));

        assertThat(optionalValidator.isValid(null, null)).isTrue();
        assertThat(optionalValidator.isValid("", null)).isTrue();
        assertThat(optionalValidator.isValid("https://www.linkedin.com/in/alex-mueller", null)).isTrue();
        assertThat(optionalValidator.isValid("http://example.de:8080/profile?tab=cv", null)).isTrue();

        assertThat(optionalValidator.isValid("   ", null)).isFalse();
        assertThat(optionalValidator.isValid("javascript:alert(1)", null)).isFalse();
        assertThat(optionalValidator.isValid("ftp://files.example.eu/cv.pdf", null)).isFalse();
        assertThat(optionalValidator.isValid("https://user:secret@example.eu/login", null)).isFalse();
        assertThat(optionalValidator.isValid("https://localhost/admin", null)).isFalse();
        assertThat(optionalValidator.isValid("https://example.com/has space", null)).isFalse();
        assertThat(optionalValidator.isValid("not-a-url", null)).isFalse();

        HttpUrlValidator strictValidator = new HttpUrlValidator();
        strictValidator.initialize(createValidHttpUrlAnnotation(false));
        assertThat(strictValidator.isValid("", null)).isFalse();
    }

    @Test
    void currencyCodeValidator_validatesIso4217CurrencyCodes() {
        CurrencyCodeValidator optionalValidator = new CurrencyCodeValidator();
        optionalValidator.initialize(createValidCurrencyAnnotation(true));

        assertThat(optionalValidator.isValid(null, null)).isTrue();
        assertThat(optionalValidator.isValid("", null)).isTrue();
        assertThat(optionalValidator.isValid("EUR", null)).isTrue();
        assertThat(optionalValidator.isValid("eur", null)).isTrue();
        assertThat(optionalValidator.isValid("USD", null)).isTrue();
        assertThat(optionalValidator.isValid("GBP", null)).isTrue();
        assertThat(optionalValidator.isValid("CHF", null)).isTrue();

        assertThat(optionalValidator.isValid("EURO", null)).isFalse();
        assertThat(optionalValidator.isValid("EU", null)).isFalse();
        assertThat(optionalValidator.isValid("123", null)).isFalse();
        assertThat(optionalValidator.isValid("ZZZ", null)).isFalse();
        assertThat(optionalValidator.isValid("   ", null)).isFalse();

        CurrencyCodeValidator strictValidator = new CurrencyCodeValidator();
        strictValidator.initialize(createValidCurrencyAnnotation(false));
        assertThat(strictValidator.isValid("", null)).isFalse();
    }

    private ValidHttpUrl createValidHttpUrlAnnotation(boolean allowEmpty) {
        return new ValidHttpUrl() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ValidHttpUrl.class;
            }

            @Override
            public String message() {
                return "invalid url";
            }

            @Override
            public boolean allowEmpty() {
                return allowEmpty;
            }

            @Override
            public Class<?>[] groups() {
                return new Class[0];
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends Payload>[] payload() {
                return new Class[0];
            }
        };
    }

    private ValidCurrencyCode createValidCurrencyAnnotation(boolean allowEmpty) {
        return new ValidCurrencyCode() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ValidCurrencyCode.class;
            }

            @Override
            public String message() {
                return "invalid currency";
            }

            @Override
            public boolean allowEmpty() {
                return allowEmpty;
            }

            @Override
            public Class<?>[] groups() {
                return new Class[0];
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends Payload>[] payload() {
                return new Class[0];
            }
        };
    }
}

