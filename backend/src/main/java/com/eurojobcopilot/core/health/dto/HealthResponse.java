package com.eurojobcopilot.core.health.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Health check status response payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Service health and operational status response")
public class HealthResponse {

    @Schema(description = "Overall health status", example = "UP")
    private String status;

    @Schema(description = "Name of the microservice", example = "eurojob-copilot-backend")
    private String service;

    @Schema(description = "Active environment profile", example = "local")
    private String environment;

    @Schema(description = "Current application build version", example = "0.0.1-SNAPSHOT")
    private String version;

    @Schema(description = "Server UTC timestamp", example = "2026-11-08T23:20:00Z")
    private Instant timestamp;
}

