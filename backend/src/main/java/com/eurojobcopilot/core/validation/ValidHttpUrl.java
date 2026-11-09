package com.eurojobcopilot.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a URL uses HTTP or HTTPS with a valid DNS host and no embedded credentials.
 */
@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidHttpUrl {

    String message() default "Must be a valid HTTP or HTTPS URL";

    boolean allowEmpty() default true;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

