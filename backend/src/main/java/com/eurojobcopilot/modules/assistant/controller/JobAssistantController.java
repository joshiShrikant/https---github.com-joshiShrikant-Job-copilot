package com.eurojobcopilot.modules.assistant.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.core.dto.ApiResponse;
import com.eurojobcopilot.modules.assistant.dto.JobAssistantMessageResponse;
import com.eurojobcopilot.modules.assistant.dto.JobAssistantRespondRequest;
import com.eurojobcopilot.modules.assistant.dto.UpdateJobAssistantMessageRequest;
import com.eurojobcopilot.modules.assistant.service.JobAssistantService;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/jobs/{jobId}/assistant")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "AI Application Assistant")
public class JobAssistantController {

    private final JobAssistantService jobAssistantService;

    @GetMapping("/history")
    @Operation(
            summary = "Get persisted job assistant conversation history",
            operationId = "getJobAssistantHistory",
            description = "Retrieves the authenticated user's persisted contextual AI assistant turns and edited drafts for a specific job posting."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Persisted assistant conversation turns returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid job identifier",
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
            )
    })
    public ResponseEntity<List<JobAssistantMessageResponse>> getHistory(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric job identifier", example = "1042")
            @PathVariable("jobId") @Positive Long jobId
    ) {
        return ResponseEntity.ok(jobAssistantService.getHistory(currentUser, jobId));
    }

    @PostMapping("/respond")
    @Operation(
            summary = "Invoke contextual AI Application Assistant for a job",
            operationId = "invokeJobAssistant",
            description = "Generates grounded contextual job guidance or editable outreach/application drafts anchored in verified candidate profile data, sponsorship status, and match scores."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Assistant guidance or draft generated and persisted",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid action type or missing custom prompt",
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
                    description = "AI provider returned invalid or malformed structured output",
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
    public ResponseEntity<JobAssistantMessageResponse> respond(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric job identifier", example = "1042")
            @PathVariable("jobId") @Positive Long jobId,
            @Valid @RequestBody JobAssistantRespondRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobAssistantService.respond(currentUser, jobId, request));
    }

    @PutMapping("/{messageId}")
    @Operation(
            summary = "Save candidate-edited assistant output",
            operationId = "updateJobAssistantMessage",
            description = "Updates the editable text of a persisted AI Application Assistant message owned by the current user for that job."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Edited assistant content saved",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid identifier or blank edited content",
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
                    description = "Assistant message not found for the current user and job",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<JobAssistantMessageResponse> updateEditableMessage(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric job identifier", example = "1042")
            @PathVariable("jobId") @Positive Long jobId,
            @Parameter(description = "Assistant message identifier", example = "15")
            @PathVariable("messageId") @Positive Long messageId,
            @Valid @RequestBody UpdateJobAssistantMessageRequest request
    ) {
        return ResponseEntity.ok(jobAssistantService.updateEditableResponse(currentUser, jobId, messageId, request));
    }

    @DeleteMapping("/{messageId}")
    @Operation(
            summary = "Delete an assistant conversation message",
            operationId = "deleteJobAssistantMessage",
            description = "Deletes a persisted assistant conversation turn owned by the current user for that job."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Assistant message deleted",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid identifier",
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
                    description = "Assistant message not found for the current user and job",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric job identifier", example = "1042")
            @PathVariable("jobId") @Positive Long jobId,
            @Parameter(description = "Assistant message identifier", example = "15")
            @PathVariable("messageId") @Positive Long messageId
    ) {
        jobAssistantService.deleteMessage(currentUser, jobId, messageId);
        return ResponseEntity.ok(ApiResponse.ok("Assistant message deleted successfully"));
    }
}

