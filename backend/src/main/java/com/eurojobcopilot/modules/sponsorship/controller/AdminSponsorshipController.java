package com.eurojobcopilot.modules.sponsorship.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.modules.sponsorship.dto.IndSyncResultDto;
import com.eurojobcopilot.modules.sponsorship.dto.RegistrySyncStatusDto;
import com.eurojobcopilot.modules.sponsorship.ind.IndRegisterSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/sponsorship")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "BearerAuth")
@Tag(
        name = "Sponsorship Registry Admin",
        description = "Admin synchronization and status monitoring for official European sponsor registers (Netherlands IND)"
)
public class AdminSponsorshipController {

    private final IndRegisterSyncService syncService;

    @PostMapping("/ind/sync")
    @Operation(
            summary = "Synchronize Netherlands IND recognized sponsor register",
            operationId = "syncNetherlandsIndRegister",
            description = "Fetches and parses the Netherlands IND public register where permitted (respecting HTTP access controls without bypassing anti-bot protections), updates official_sponsor_registry_entries, links matching companies, and records a sync telemetry log."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "IND register synchronization attempt completed and telemetry logged",
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Another IND synchronization run is already in progress",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<IndSyncResultDto> triggerIndSync() {
        return ResponseEntity.ok(syncService.syncIndRegister());
    }

    @GetMapping("/ind/status")
    @Operation(
            summary = "Get Netherlands IND register sync status",
            operationId = "getNetherlandsIndSyncStatus",
            description = "Returns active entry counts and latest synchronization health telemetry for the Netherlands IND register."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Latest IND sync status returned",
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
    public ResponseEntity<RegistrySyncStatusDto> getIndSyncStatus() {
        return ResponseEntity.ok(syncService.getLatestSyncStatus());
    }
}

