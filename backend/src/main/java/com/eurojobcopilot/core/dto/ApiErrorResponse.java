package com.eurojobcopilot.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Standard API error response payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API error response")
public class ApiErrorResponse {

    @Schema(description = "UTC timestamp when error occurred")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "HTTP error reason", example = "Bad Request")
    private String error;

    @Schema(description = "Human-readable error description", example = "Validation failed for one or more fields")
    private String message;

    @Schema(description = "Request path", example = "/api/v1/auth/register")
    private String path;

    @Schema(description = "Field-level validation errors if applicable")
    private List<ValidationErrorItem> validationErrors;
}

