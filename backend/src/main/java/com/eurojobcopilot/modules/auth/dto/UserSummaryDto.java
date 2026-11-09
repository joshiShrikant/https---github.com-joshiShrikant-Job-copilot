package com.eurojobcopilot.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authenticated user summary")
public class UserSummaryDto {

    @Schema(description = "Public user UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uuid;

    @Schema(description = "User email address", example = "engineer@eurojobcopilot.com")
    private String email;

    @Schema(description = "First name", example = "Alex")
    private String firstName;

    @Schema(description = "Last name", example = "Mueller")
    private String lastName;

    @Schema(description = "Security role", example = "ROLE_USER")
    private String role;

    @Schema(description = "Current account lifecycle status", example = "ACTIVE")
    private String accountStatus;

    @Schema(description = "Whether email address is verified", example = "false")
    private boolean emailVerified;
}

