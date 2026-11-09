package com.eurojobcopilot.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string represents a valid 3-letter ISO 4217 currency code.
 */
@Documented
@Constraint(validatedBy = CurrencyCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCurrencyCode {

    String message() default "Must be a valid 3-letter ISO currency code";

    boolean allowEmpty() default true;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

