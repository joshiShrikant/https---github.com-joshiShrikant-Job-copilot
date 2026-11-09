package com.eurojobcopilot.modules.application.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.core.dto.ApiResponse;
import com.eurojobcopilot.core.validation.ValidUuid;
import com.eurojobcopilot.modules.application.dto.CreateApplicationRequest;
import com.eurojobcopilot.modules.application.dto.JobApplicationDetailResponse;
import com.eurojobcopilot.modules.application.dto.UpdateApplicationRequest;
import com.eurojobcopilot.modules.application.dto.UpdateApplicationStatusRequest;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.service.ApplicationService;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    @Operation(
            summary = "List user job applications",
            operationId = "getApplications",
            description = "Retrieves all job applications tracked by the authenticated user for Kanban board, table, and calendar views with status timelines and linked document versions."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Active application pipeline owned by the user",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid query parameter value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<List<JobApplicationDetailResponse>> getApplications(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Optional pipeline stage filter")
            @RequestParam(name = "status", required = false) ApplicationStatus status,
            @Parameter(description = "Optional text search across job title, company, location, or notes")
            @RequestParam(name = "query", required = false) String query,
            @Parameter(description = "When true, returns only applications with due or overdue follow-up dates")
            @RequestParam(name = "dueFollowUpOnly", required = false) Boolean dueFollowUpOnly,
            @Parameter(description = "When true, returns only applications with a scheduled interview date")
            @RequestParam(name = "hasInterviewOnly", required = false) Boolean hasInterviewOnly
    ) {
        return ResponseEntity.ok(
                applicationService.listApplications(currentUser, status, query, dueFollowUpOnly, hasInterviewOnly)
        );
    }

    @PostMapping
    @Operation(
            summary = "Track a new job application",
            operationId = "createApplication",
            description = "Adds an indexed job posting to the user's application tracker with initial stage, linked tailored CV/cover letter versions, dates, offer, and notes."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Application tracked with initial chronological timeline entry",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid application request fields",
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
                    responseCode = "404",
                    description = "Target job or linked tailored resume / cover letter not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Job is already tracked in the user's application pipeline",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<JobApplicationDetailResponse> createApplication(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Valid @RequestBody CreateApplicationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(applicationService.createApplication(currentUser, request));
    }

    @GetMapping("/{uuid}")
    @Operation(
            summary = "Get tracked application detail",
            operationId = "getApplication",
            description = "Retrieves a tracked job application owned by the user including its full chronological status transition history."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Tracked application detail",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed application UUID",
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
                    responseCode = "404",
                    description = "Tracked application not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<JobApplicationDetailResponse> getApplication(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical application UUID", example = "7f21a1e8-b4d2-4d30-9f1a-45c7d9b8e110")
            @PathVariable("uuid") @ValidUuid(message = "Application UUID must be a valid canonical UUID") String uuid
    ) {
        return ResponseEntity.ok(applicationService.getApplication(currentUser, uuid));
    }

    @PutMapping("/{uuid}")
    @Operation(
            summary = "Update tracked application details",
            operationId = "updateApplication",
            description = "Updates dates (applied, interview, follow-up reminder), salary offer & currency, notes, or linked tailored resume and cover letter versions."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Tracked application updated",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed UUID or validation failure in update payload",
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
                    responseCode = "404",
                    description = "Application or referenced tailored document not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Optimistic lock version mismatch on concurrent modification",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<JobApplicationDetailResponse> updateApplication(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical application UUID", example = "7f21a1e8-b4d2-4d30-9f1a-45c7d9b8e110")
            @PathVariable("uuid") @ValidUuid(message = "Application UUID must be a valid canonical UUID") String uuid,
            @Valid @RequestBody UpdateApplicationRequest request
    ) {
        return ResponseEntity.ok(applicationService.updateApplication(currentUser, uuid, request));
    }

    @PatchMapping("/{uuid}/status")
    @Operation(
            summary = "Transition Kanban status of an application",
            operationId = "updateApplicationStatus",
            description = "Moves a tracked application between pipeline stages and appends an immutable entry to the application timeline audit log."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Application pipeline stage updated",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed UUID or invalid stage transition payload",
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
                    responseCode = "404",
                    description = "Tracked application not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<JobApplicationDetailResponse> updateApplicationStatus(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical application UUID", example = "7f21a1e8-b4d2-4d30-9f1a-45c7d9b8e110")
            @PathVariable("uuid") @ValidUuid(message = "Application UUID must be a valid canonical UUID") String uuid,
            @Valid @RequestBody UpdateApplicationStatusRequest request
    ) {
        return ResponseEntity.ok(applicationService.updateApplicationStatus(currentUser, uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @Operation(
            summary = "Delete tracked application",
            operationId = "deleteApplication",
            description = "Deletes an application owned by the authenticated user along with its status history while safely detaching any linked recruiter interactions."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Tracked application deleted",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed application UUID",
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
                    responseCode = "404",
                    description = "Tracked application not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Void>> deleteApplication(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical application UUID", example = "7f21a1e8-b4d2-4d30-9f1a-45c7d9b8e110")
            @PathVariable("uuid") @ValidUuid(message = "Application UUID must be a valid canonical UUID") String uuid
    ) {
        applicationService.deleteApplication(currentUser, uuid);
        return ResponseEntity.ok(ApiResponse.ok("Tracked application deleted successfully"));
    }
}

