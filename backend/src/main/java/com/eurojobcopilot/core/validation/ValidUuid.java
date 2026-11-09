package com.eurojobcopilot.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a non-empty string conforms to canonical 36-character UUID format.
 */
@Documented
@Constraint(validatedBy = UuidValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUuid {

    String message() default "Must be a valid canonical UUID";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

