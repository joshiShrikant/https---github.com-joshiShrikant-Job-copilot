package com.eurojobcopilot.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Field-level request validation failure detail")
public class ValidationErrorItem {

    @Schema(description = "Name of the invalid field", example = "email")
    private String field;

    @Schema(description = "Rejected input value (masked for sensitive fields)", example = "invalid-email")
    private String rejectedValue;

    @Schema(description = "Validation error description", example = "Must be a valid email address")
    private String message;
}

