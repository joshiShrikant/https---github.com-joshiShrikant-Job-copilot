package com.eurojobcopilot.modules.job.intelligence.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobAiAnalysisDto;
import com.eurojobcopilot.modules.job.intelligence.dto.AnalyzeJobDescriptionRequest;
import com.eurojobcopilot.modules.job.intelligence.service.JobAiAnalysisService;
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

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(
        name = "AI Intelligence",
        description = "AI-powered job description extraction, structured validation, and analysis version history APIs"
)
public class JobAiAnalysisController {

    private final JobAiAnalysisService jobAiAnalysisService;

    @PostMapping("/{id}/intelligence/analyze-jd")
    @Operation(
            summary = "Run AI job description extraction and persist versioned structured analysis",
            operationId = "analyzeJobDescription",
            description = "Invokes the configured LLM provider to extract structured role metadata, required/preferred skills, responsibilities, experience, compensation, work arrangement, employment type, language demands, visa sponsorship clues, and relocation assistance, strictly validates the AI JSON payload before saving, updates the cached job analysis, and appends an immutable versioned history snapshot."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "AI job description extraction validated and persisted",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid job identifier or empty job description",
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
                    responseCode = "502",
                    description = "Upstream AI provider failure or invalid AI JSON response rejected by validation",
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
    public ResponseEntity<JobAiAnalysisDto> analyzeJobDescription(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric job identifier", example = "1042")
            @PathVariable("id") @Positive Long id,
            @RequestBody(required = false) AnalyzeJobDescriptionRequest request
    ) {
        Boolean forceRefresh = request != null ? request.getForceRefresh() : null;
        return ResponseEntity.ok(jobAiAnalysisService.analyzeJobDescription(currentUser, id, forceRefresh));
    }

    @GetMapping("/{id}/intelligence/analyze-jd")
    @Operation(
            summary = "Retrieve latest persisted AI job description analysis",
            operationId = "getLatestJobDescriptionAnalysis",
            description = "Returns the most recent validated AI structured extraction stored against the specified job."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Latest persisted AI job description analysis returned",
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
                    description = "Job or AI analysis record not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<JobAiAnalysisDto> getLatestJobDescriptionAnalysis(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric job identifier", example = "1042")
            @PathVariable("id") @Positive Long id
    ) {
        return ResponseEntity.ok(jobAiAnalysisService.getLatestAnalysis(currentUser, id));
    }

    @GetMapping("/{id}/intelligence/analyze-jd/history")
    @Operation(
            summary = "List immutable AI job description analysis history for a job",
            operationId = "getJobDescriptionAnalysisHistory",
            description = "Returns all versioned AI extraction snapshots persisted for the specified job, ordered newest version first."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Versioned AI job description analysis history returned",
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
            )
    })
    public ResponseEntity<List<JobAiAnalysisDto>> getJobDescriptionAnalysisHistory(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric job identifier", example = "1042")
            @PathVariable("id") @Positive Long id
    ) {
        return ResponseEntity.ok(jobAiAnalysisService.getAnalysisHistory(currentUser, id));
    }
}

