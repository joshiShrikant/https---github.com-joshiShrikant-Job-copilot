package com.eurojobcopilot.core.exception;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.core.dto.ValidationErrorItem;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Centralized REST exception handler normalizing all API failures into {@link ApiErrorResponse}.
 */
@Slf4j
@Order
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_FAILED_MESSAGE = "Validation failed for one or more fields";

    @ExceptionHandler(BaseApiException.class)
    public ResponseEntity<ApiErrorResponse> handleBaseApiException(
            BaseApiException ex,
            HttpServletRequest request
    ) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Server error [{}] at {} {}: {}",
                    ex.getStatus().value(), request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.debug("Client API error [{}] at {} {}: {}",
                    ex.getStatus().value(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        }
        return buildErrorResponse(ex.getStatus(), ex.getClientSafeMessage(), request, null);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(
            BindException ex,
            HttpServletRequest request
    ) {
        List<ValidationErrorItem> validationErrors = new ArrayList<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String fieldName = fieldError.getField();
            validationErrors.add(ValidationErrorItem.builder()
                    .field(fieldName)
                    .rejectedValue(redactSensitiveValue(fieldName, fieldError.getRejectedValue()))
                    .message(fieldError.getDefaultMessage())
                    .build());
        }

        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            validationErrors.add(ValidationErrorItem.builder()
                    .field(globalError.getObjectName())
                    .rejectedValue(null)
                    .message(globalError.getDefaultMessage())
                    .build());
        }

        return buildErrorResponse(HttpStatus.BAD_REQUEST, VALIDATION_FAILED_MESSAGE, request, validationErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException ex,
            HttpServletRequest request
    ) {
        List<ValidationErrorItem> validationErrors = new ArrayList<>();

        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String paramName = result.getMethodParameter().getParameterName();
            if (paramName == null || paramName.isBlank()) {
                paramName = "parameter";
            }
            String redactedValue = redactSensitiveValue(paramName, result.getArgument());
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                validationErrors.add(ValidationErrorItem.builder()
                        .field(paramName)
                        .rejectedValue(redactedValue)
                        .message(error.getDefaultMessage())
                        .build());
            }
        }

        for (MessageSourceResolvable crossError : ex.getCrossParameterValidationResults()) {
            validationErrors.add(ValidationErrorItem.builder()
                    .field("parameters")
                    .rejectedValue(null)
                    .message(crossError.getDefaultMessage())
                    .build());
        }

        return buildErrorResponse(HttpStatus.BAD_REQUEST, VALIDATION_FAILED_MESSAGE, request, validationErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<ValidationErrorItem> validationErrors = ex.getConstraintViolations().stream()
                .map(this::toValidationErrorItem)
                .toList();

        return buildErrorResponse(HttpStatus.BAD_REQUEST, VALIDATION_FAILED_MESSAGE, request, validationErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        log.debug("Unreadable HTTP payload at {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Malformed request payload or unsupported field value",
                request,
                null
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid parameter value for '" + ex.getName() + "'",
                request,
                null
        );
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestBinding(
            ServletRequestBindingException ex,
            HttpServletRequest request
    ) {
        String message;
        if (ex instanceof MissingServletRequestParameterException paramEx) {
            message = "Required request parameter '" + paramEx.getParameterName() + "' is missing";
        } else if (ex instanceof MissingRequestHeaderException headerEx) {
            message = "Required request header '" + headerEx.getHeaderName() + "' is missing";
        } else if (ex instanceof MissingRequestCookieException cookieEx) {
            message = "Required request cookie '" + cookieEx.getCookieName() + "' is missing";
        } else if (ex instanceof MissingPathVariableException pathEx) {
            message = "Required path variable '" + pathEx.getVariableName() + "' is missing";
        } else {
            message = "Invalid or incomplete request binding";
        }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request, null);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Required upload part '" + ex.getRequestPartName() + "' is missing",
                request,
                null
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Uploaded file exceeds maximum permitted upload size",
                request,
                null
        );
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartException(
            MultipartException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Malformed multipart file upload request",
                request,
                null
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        HttpHeaders headers = new HttpHeaders();
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (!CollectionUtils.isEmpty(supported)) {
            headers.setAllow(supported);
        }
        return buildErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint",
                request,
                null,
                headers
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request
    ) {
        MediaType contentType = ex.getContentType();
        String message = contentType != null
                ? "Content-Type '" + contentType + "' is not supported for this endpoint"
                : "Unsupported or missing Content-Type header";
        return buildErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message, request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.NOT_ACCEPTABLE,
                "Requested media type in Accept header is not supported",
                request,
                null
        );
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleEndpointNotFound(
            Exception ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "Endpoint not found: " + request.getRequestURI(),
                request,
                null
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "Access denied: insufficient privileges",
                request,
                null
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Authentication required or access token is invalid/expired",
                request,
                null
        );
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(
            Exception ex,
            HttpServletRequest request
    ) {
        log.warn("Optimistic lock conflict at {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "Resource was modified concurrently. Please refresh and retry",
                request,
                null
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        log.warn("Data integrity violation at {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "Operation conflicts with an existing record or database constraint",
                request,
                null
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unhandled unexpected exception at {} {}",
                request.getMethod(), request.getRequestURI(), ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal server error occurred",
                request,
                null
        );
    }

    private ValidationErrorItem toValidationErrorItem(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath() != null
                ? violation.getPropertyPath().toString()
                : "parameter";
        int lastDot = propertyPath.lastIndexOf('.');
        String fieldName = lastDot >= 0 && lastDot < propertyPath.length() - 1
                ? propertyPath.substring(lastDot + 1)
                : propertyPath;

        return ValidationErrorItem.builder()
                .field(fieldName)
                .rejectedValue(redactSensitiveValue(fieldName, violation.getInvalidValue()))
                .message(violation.getMessage())
                .build();
    }

    private String redactSensitiveValue(String fieldName, Object rejectedValue) {
        if (isSensitiveField(fieldName)) {
            return "[REDACTED]";
        }
        return String.valueOf(rejectedValue);
    }

    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("apikey")
                || lower.contains("api_key")
                || lower.contains("credential")
                || lower.contains("authorization");
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            List<ValidationErrorItem> validationErrors
    ) {
        return buildErrorResponse(status, message, request, validationErrors, null);
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            List<ValidationErrorItem> validationErrors,
            HttpHeaders extraHeaders
    ) {
        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .validationErrors(validationErrors != null && !validationErrors.isEmpty() ? validationErrors : null)
                .build();

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            builder.headers(extraHeaders);
        }
        return builder.body(errorResponse);
    }
}

