package com.eurojobcopilot.modules.matching.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobMatchBreakdownDto;
import com.eurojobcopilot.modules.matching.ai.dto.EvaluateAiMatchRequest;
import com.eurojobcopilot.modules.matching.ai.service.SemanticJobMatchingService;
import com.eurojobcopilot.modules.matching.dto.BatchMatchRecalculationResponse;
import com.eurojobcopilot.modules.matching.service.JobMatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(
        name = "Job Matching",
        description = "Deterministic rule-based compatibility scoring, AI semantic job matching, and batch match recalculation APIs"
)
public class JobMatchingController {

    private final JobMatchingService jobMatchingService;
    private final SemanticJobMatchingService semanticJobMatchingService;

    @GetMapping("/jobs/{id}/intelligence/match-score")
    @Operation(
            summary = "Calculate and retrieve deterministic job match score breakdown",
            operationId = "evaluateJobMatchScore",
            description = "Computes the user's weighted deterministic compatibility score (0..100) across technical skills, experience, title, location, sponsorship, and salary, persists it for the user-job pair, and returns the detailed breakdown."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Deterministic job match breakdown computed and returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid non-positive or malformed job identifier",
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
                    description = "Job with the specified ID was not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Concurrent match score update conflict",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<JobMatchBreakdownDto> getOrCalculateMatchScore(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric job identifier", example = "1042")
            @PathVariable("id") @Positive Long id
    ) {
        return ResponseEntity.ok(jobMatchingService.evaluateAndPersistJobMatch(currentUser, id));
    }

    @PostMapping("/jobs/{id}/intelligence/ai-match")
    @Operation(
            summary = "Run AI-assisted semantic job matching comparison",
            operationId = "evaluateSemanticJobMatch",
            description = "Compares the authenticated candidate's professional profile, master resume, technical skills, work experience, projects, and education against the job description and structured responsibilities using AI while keeping deterministic rule-based scoring separate and forbidding fabricated candidate qualifications."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "AI semantic comparison validated, persisted, and returned alongside deterministic breakdown",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid job identifier, empty candidate profile/resume evidence, or empty job posting content",
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
                    description = "Job with the specified ID was not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Concurrent AI match score update conflict",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "Upstream AI provider failure or invalid AI semantic match response rejected by validation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "AI intelligence disabled or no AI provider API key configured",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<JobMatchBreakdownDto> evaluateSemanticJobMatch(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric job identifier", example = "1042")
            @PathVariable("id") @Positive Long id,
            @RequestBody(required = false) EvaluateAiMatchRequest request
    ) {
        Boolean forceRefresh = request != null ? request.getForceRefresh() : null;
        return ResponseEntity.ok(semanticJobMatchingService.evaluateSemanticMatch(currentUser, id, forceRefresh));
    }

    @PostMapping("/matching/recalculate")
    @Operation(
            summary = "Recalculate deterministic match scores across active jobs",
            operationId = "recalculateActiveJobMatches",
            description = "Evaluates active non-expired jobs against the authenticated candidate's profile, skills, work experience, and job preferences using the configured deterministic weights and returns top matches."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Deterministic match scores recalculated across active jobs",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Concurrent batch match score update conflict",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<BatchMatchRecalculationResponse> recalculateActiveJobMatches(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(jobMatchingService.recalculateActiveJobs(currentUser));
    }
}

