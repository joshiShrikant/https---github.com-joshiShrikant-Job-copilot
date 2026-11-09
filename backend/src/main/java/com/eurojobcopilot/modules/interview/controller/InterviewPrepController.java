package com.eurojobcopilot.modules.interview.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.core.dto.ApiResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.interview.dto.CreateInterviewPrepSessionRequest;
import com.eurojobcopilot.modules.interview.dto.InterviewPerformanceHistoryResponse;
import com.eurojobcopilot.modules.interview.dto.InterviewPrepSessionDetailResponse;
import com.eurojobcopilot.modules.interview.dto.InterviewPrepSessionSummaryDto;
import com.eurojobcopilot.modules.interview.dto.SubmitInterviewAnswerRequest;
import com.eurojobcopilot.modules.interview.service.InterviewPrepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/interview-prep")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Interview Preparation")
public class InterviewPrepController {

    private final InterviewPrepService interviewPrepService;

    @PostMapping("/sessions")
    @Operation(
            summary = "Generate a job-tailored AI interview prep session",
            operationId = "createInterviewPrepSession",
            description = "Generates role-specific technical/system-design/behavioral interview questions with ideal benchmark answers and grounded company preparation topics."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Interview prep session generated and persisted",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid session generation parameters",
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
                    description = "Target job posting not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "AI provider returned malformed session payload",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "AI provider is disabled or unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<InterviewPrepSessionDetailResponse> createSession(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Valid @RequestBody CreateInterviewPrepSessionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interviewPrepService.createSession(currentUser, request));
    }

    @GetMapping("/sessions")
    @Operation(
            summary = "List user interview prep sessions",
            operationId = "listInterviewPrepSessions",
            description = "Retrieves interview preparation sessions owned by the current user, optionally filtered by target job."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Interview prep sessions returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid jobId filter",
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
    public ResponseEntity<List<InterviewPrepSessionSummaryDto>> listSessions(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Optional job ID filter")
            @RequestParam(value = "jobId", required = false) @Positive Long jobId
    ) {
        return ResponseEntity.ok(interviewPrepService.listSessions(currentUser, jobId));
    }

    @GetMapping("/sessions/{id}")
    @Operation(
            summary = "Get full interview prep session detail",
            operationId = "getInterviewPrepSession",
            description = "Retrieves the full interview prep session including grounded company prep topics, ordered questions, ideal answers, submitted practice answers, and AI grading feedback."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Interview prep session detail returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid session ID",
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
                    description = "Interview prep session not found for user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<InterviewPrepSessionDetailResponse> getSession(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @PathVariable("id") @Positive Long id
    ) {
        return ResponseEntity.ok(interviewPrepService.getSession(currentUser, id));
    }

    @PostMapping("/sessions/{id}/answers")
    @Operation(
            summary = "Submit practice answer for AI grading and coaching",
            operationId = "submitInterviewAnswer",
            description = "Evaluates the candidate's submitted practice answer against the ideal benchmark answer and job context, persisting score, strengths, missing elements, and updated session coaching."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Practice answer evaluated and persisted",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid question ID or blank answer",
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
                    description = "Interview prep session or question not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "AI evaluator returned malformed score or feedback",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "AI provider is disabled or unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<InterviewPrepSessionDetailResponse> submitAnswer(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody SubmitInterviewAnswerRequest request
    ) {
        return ResponseEntity.ok(interviewPrepService.submitAnswer(currentUser, id, request));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(
            summary = "Delete an interview prep session",
            operationId = "deleteInterviewPrepSession",
            description = "Deletes an interview preparation session and its questions owned by the authenticated user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Interview prep session deleted",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid session ID",
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
                    description = "Interview prep session not found for user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @PathVariable("id") @Positive Long id
    ) {
        interviewPrepService.deleteSession(currentUser, id);
        return ResponseEntity.ok(ApiResponse.ok("Interview prep session deleted successfully"));
    }

    @GetMapping("/history")
    @Operation(
            summary = "Get interview prep performance history and improvement analytics",
            operationId = "getInterviewPerformanceHistory",
            description = "Aggregates user interview prep performance across categories, difficulty levels, chronological score trends, weakest areas, and coaching recommendations."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Interview performance analytics returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<InterviewPerformanceHistoryResponse> getPerformanceHistory(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(interviewPrepService.getPerformanceHistory(currentUser));
    }
}

