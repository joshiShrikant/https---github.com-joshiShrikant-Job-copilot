package com.eurojobcopilot.modules.job.ingestion.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.modules.job.entity.FetchStatus;
import com.eurojobcopilot.modules.job.ingestion.AdminIngestionService;
import com.eurojobcopilot.modules.job.ingestion.dto.AdminIngestionTriggerResponse;
import com.eurojobcopilot.modules.job.ingestion.dto.IngestionStatusResponse;
import com.eurojobcopilot.modules.job.ingestion.dto.PagedFetchLogsResponse;
import com.eurojobcopilot.modules.job.ingestion.dto.StaleJobCleanupResponse;
import com.eurojobcopilot.modules.job.ingestion.dto.TriggerIngestionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/ingestion")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Job Ingestion Admin", description = "Admin job ingestion triggers, scheduler telemetry, fetch logs, and stale job maintenance")
public class AdminIngestionController {

    private final AdminIngestionService adminIngestionService;

    @PostMapping("/trigger")
    @Operation(
            summary = "Trigger job ingestion manually",
            operationId = "triggerJobIngestion",
            description = "Triggers an immediate synchronous job discovery batch across all enabled providers or a specified enabled provider."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Ingestion execution completed and summary recorded",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid trigger parameters or target provider is disabled/missing credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN privileges",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Requested job provider does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Another job ingestion run is already in progress",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<AdminIngestionTriggerResponse> triggerIngestion(
            @Parameter(description = "Optional specific provider name override (e.g. ARBEITNOW, ADZUNA, or JOOBLE)")
            @RequestParam(value = "provider", required = false) String provider,
            @Valid @RequestBody(required = false) TriggerIngestionRequest request
    ) {
        return ResponseEntity.ok(adminIngestionService.triggerManualIngestion(provider, request));
    }

    @GetMapping("/status")
    @Operation(
            summary = "Get job ingestion status",
            operationId = "getJobIngestionStatus",
            description = "Retrieves scheduler configuration, active concurrency lock state, indexed job counts, and latest run health per provider."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Ingestion status snapshot returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN privileges",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<IngestionStatusResponse> getStatus() {
        return ResponseEntity.ok(adminIngestionService.getStatus());
    }

    @GetMapping("/logs")
    @Operation(
            summary = "List job fetch logs",
            operationId = "listJobFetchLogs",
            description = "Returns paginated job ingestion execution audit logs filtered optionally by provider and status, ordered newest first."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Paginated fetch logs returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination or filter parameter",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN privileges",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<PagedFetchLogsResponse> listFetchLogs(
            @Parameter(description = "Optional provider name filter (case-insensitive)")
            @RequestParam(value = "provider", required = false) String provider,
            @Parameter(description = "Optional fetch status filter")
            @RequestParam(value = "status", required = false) FetchStatus status,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1..100)", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(adminIngestionService.listFetchLogs(provider, status, page, size));
    }

    @PostMapping("/cleanup-stale")
    @Operation(
            summary = "Deactivate expired or stale jobs",
            operationId = "cleanupStaleJobs",
            description = "Immediately marks jobs past their explicit expiresAt timestamp or older than the configured stale threshold as inactive."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Stale job cleanup sweep executed",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid staleDays parameter",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN privileges",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<StaleJobCleanupResponse> cleanupStaleJobs(
            @Parameter(description = "Optional threshold override in days (1..365)")
            @RequestParam(value = "staleDays", required = false) @Min(1) @Max(365) Integer staleDays
    ) {
        return ResponseEntity.ok(adminIngestionService.cleanupStaleJobs(staleDays));
    }
}

