package com.eurojobcopilot.modules.autopilot.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.autopilot.dto.AutopilotRunLogDto;
import com.eurojobcopilot.modules.autopilot.dto.AutopilotStatusResponse;
import com.eurojobcopilot.modules.autopilot.dto.PagedAutopilotHistoryResponse;
import com.eurojobcopilot.modules.autopilot.dto.PagedRecommendedJobsResponse;
import com.eurojobcopilot.modules.autopilot.dto.RunAutopilotRequest;
import com.eurojobcopilot.modules.autopilot.service.AutopilotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/autopilot")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Autopilot & Recommendations")
public class AutopilotController {

    private final AutopilotService autopilotService;

    @GetMapping("/status")
    @Operation(
            summary = "Get autopilot status and configuration summary",
            operationId = "getAutopilotStatus",
            description = "Returns the current user's autopilot enablement/pause state, preference summary, recommendation count, unread alerts count, and latest run telemetry."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Autopilot status returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<AutopilotStatusResponse> getStatus(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(autopilotService.getStatus(currentUser));
    }

    @PostMapping("/run")
    @Operation(
            summary = "Run manual job search autopilot scan",
            operationId = "runAutopilotScan",
            description = "Recalculates personalized compatibility scores against active European roles, checks follow-up reminders, creates deduplicated in-app alerts, optionally refreshes connectors within cooldown limits, and records run history."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Autopilot scan executed and logged",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<AutopilotRunLogDto> runScan(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @RequestBody(required = false) RunAutopilotRequest request
    ) {
        return ResponseEntity.ok(autopilotService.runManualScan(currentUser, request));
    }

    @PostMapping("/pause")
    @Operation(
            summary = "Pause scheduled job search autopilot",
            operationId = "pauseAutopilot",
            description = "Pauses scheduled autopilot background scans for the current user without clearing preferences."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Autopilot paused",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<AutopilotStatusResponse> pause(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(autopilotService.pauseAutopilot(currentUser));
    }

    @PostMapping("/resume")
    @Operation(
            summary = "Resume scheduled job search autopilot",
            operationId = "resumeAutopilot",
            description = "Resumes scheduled job search autopilot discovery and alerts for the current user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Autopilot resumed",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<AutopilotStatusResponse> resume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(autopilotService.resumeAutopilot(currentUser));
    }

    @GetMapping("/recommended-jobs")
    @Operation(
            summary = "List autopilot-ranked recommended jobs",
            operationId = "getRecommendedJobs",
            description = "Returns personalized ranked European roles meeting the user's match score threshold along with skill overlap, experience compatibility, and verified sponsor signals."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Ranked recommended jobs returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid filter or pagination parameters",
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
    public ResponseEntity<PagedRecommendedJobsResponse> getRecommendedJobs(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Optional override minimum match score threshold (0..100)")
            @RequestParam(value = "minMatchScore", required = false) @Min(0) @Max(100) Integer minMatchScore,
            @Parameter(description = "Filter exclusively to verified visa sponsor employers")
            @RequestParam(value = "verifiedSponsorsOnly", defaultValue = "false") boolean verifiedSponsorsOnly,
            @Parameter(description = "Exclude roles where the user already recorded an application")
            @RequestParam(value = "excludeApplied", defaultValue = "false") boolean excludeApplied,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1..100)", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(autopilotService.getRecommendedJobs(
                currentUser, minMatchScore, verifiedSponsorsOnly, excludeApplied, page, size
        ));
    }

    @GetMapping("/history")
    @Operation(
            summary = "Get autopilot run telemetry history",
            operationId = "getAutopilotRunHistory",
            description = "Returns paginated execution history of manual and scheduled autopilot scans for the current user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Autopilot run history returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination parameters",
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
    public ResponseEntity<PagedAutopilotHistoryResponse> getHistory(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1..100)", example = "10")
            @RequestParam(value = "size", defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(autopilotService.getRunHistory(currentUser, page, size));
    }
}

