package com.eurojobcopilot.core.health;

import com.eurojobcopilot.core.health.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;

/**
 * Controller exposing standard REST health status endpoint.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health Check", description = "Operational readiness and service health probe")
public class HealthCheckController {

    private final Environment environment;

    @Value("${spring.application.name:eurojob-copilot-backend}")
    private String applicationName;

    public HealthCheckController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping
    @Operation(
            summary = "Check application health",
            operationId = "getHealth",
            description = "Public readiness probe returning operational status, service name, active profile, version, and UTC timestamp."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Service is healthy and accepting traffic",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = HealthResponse.class))
            )
    })
    public ResponseEntity<HealthResponse> getHealth() {
        String[] activeProfiles = environment.getActiveProfiles();
        String activeProfile = activeProfiles.length > 0 ? Arrays.toString(activeProfiles) : "default";

        HealthResponse response = HealthResponse.builder()
                .status("UP")
                .service(applicationName)
                .environment(activeProfile)
                .version("0.0.1-SNAPSHOT")
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.ok(response);
    }
}

