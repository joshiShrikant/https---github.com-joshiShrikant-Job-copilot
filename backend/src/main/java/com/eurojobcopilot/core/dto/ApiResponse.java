package com.eurojobcopilot.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard success envelope across REST endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API success response envelope")
public class ApiResponse<T> {

    @Schema(description = "Indicates whether the operation succeeded", example = "true")
    private boolean success;

    @Schema(description = "UTC timestamp of response", example = "2026-11-09T02:00:00Z")
    private Instant timestamp;

    @Schema(description = "Optional response payload")
    private T data;

    @Schema(description = "Human-readable status message", example = "Operation completed successfully")
    private String message;

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .timestamp(Instant.now())
                .data(data)
                .message(message)
                .build();
    }

    public static ApiResponse<Void> ok(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .timestamp(Instant.now())
                .message(message)
                .build();
    }
}

