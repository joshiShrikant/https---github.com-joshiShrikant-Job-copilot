package com.eurojobcopilot.modules.user.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.user.dto.JobPreferencesResponse;
import com.eurojobcopilot.modules.user.dto.UpdateJobPreferencesRequest;
import com.eurojobcopilot.modules.user.service.JobPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Preferences", description = "Job discovery, salary, visa sponsorship, and autopilot search preferences")
public class JobPreferenceController {

    private final JobPreferenceService jobPreferenceService;

    @GetMapping
    @Operation(
            summary = "Get job search preferences",
            operationId = "getPreferences",
            description = "Retrieves the authenticated user's job discovery, salary, visa sponsorship, and autopilot preferences (or system defaults when not yet customized)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Current job search and autopilot preferences",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<JobPreferencesResponse> getPreferences(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(jobPreferenceService.getPreferences(currentUser));
    }

    @PutMapping
    @Operation(
            summary = "Update job search preferences",
            operationId = "updatePreferences",
            description = "Creates or updates the authenticated user's European job discovery filters and autopilot configuration."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Preferences updated successfully",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid preference fields, currency code, or match score bounds",
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
                    responseCode = "409",
                    description = "Concurrent preference update conflict",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<JobPreferencesResponse> updatePreferences(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Valid @RequestBody UpdateJobPreferencesRequest request
    ) {
        return ResponseEntity.ok(jobPreferenceService.updatePreferences(currentUser, request));
    }
}

