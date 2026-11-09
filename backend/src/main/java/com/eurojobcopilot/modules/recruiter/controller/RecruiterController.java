package com.eurojobcopilot.modules.recruiter.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.core.dto.ApiResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.recruiter.dto.CreateRecruiterInteractionRequest;
import com.eurojobcopilot.modules.recruiter.dto.CreateRecruiterRequest;
import com.eurojobcopilot.modules.recruiter.dto.FollowUpRemindersResponse;
import com.eurojobcopilot.modules.recruiter.dto.GenerateOutreachRequest;
import com.eurojobcopilot.modules.recruiter.dto.OutreachDraftResponse;
import com.eurojobcopilot.modules.recruiter.dto.PagedRecruitersResponse;
import com.eurojobcopilot.modules.recruiter.dto.RecruiterInteractionDto;
import com.eurojobcopilot.modules.recruiter.dto.RecruiterResponse;
import com.eurojobcopilot.modules.recruiter.dto.UpdateRecruiterInteractionRequest;
import com.eurojobcopilot.modules.recruiter.dto.UpdateRecruiterRequest;
import com.eurojobcopilot.modules.recruiter.service.RecruiterService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/recruiters")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Recruiter CRM")
public class RecruiterController {

    private final RecruiterService recruiterService;

    @GetMapping
    @Operation(
            summary = "List recruiter contacts",
            operationId = "listRecruiters",
            description = "Retrieves the user's paginated recruiter CRM contacts with interaction counts and upcoming follow-up reminders."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Paginated recruiter directory",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination or filter parameters",
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
    public ResponseEntity<PagedRecruitersResponse> listRecruiters(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Optional search term matching recruiter name, title, company, or email")
            @RequestParam(name = "query", required = false) String query,
            @Parameter(description = "Optional linked company ID filter")
            @RequestParam(name = "companyId", required = false)
            @Positive(message = "Company ID filter must be positive") Long companyId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(recruiterService.listRecruiters(currentUser, query, companyId, page, size));
    }

    @PostMapping
    @Operation(
            summary = "Create recruiter contact",
            operationId = "createRecruiter",
            description = "Creates a new recruiter or hiring manager contact in the authenticated user's CRM."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Recruiter contact created",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation error in recruiter fields",
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
                    description = "Referenced company ID not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RecruiterResponse> createRecruiter(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Valid @RequestBody CreateRecruiterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recruiterService.createRecruiter(currentUser, request));
    }

    @GetMapping("/reminders")
    @Operation(
            summary = "Get follow-up reminders",
            operationId = "getFollowUpReminders",
            description = "Aggregates overdue, due-today, and upcoming follow-up reminders across recruiter communications and tracked applications."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Follow-up reminder schedule and urgency counts",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<FollowUpRemindersResponse> getFollowUpReminders(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Whether completed reminders are included in items")
            @RequestParam(name = "includeCompleted", defaultValue = "false") boolean includeCompleted
    ) {
        return ResponseEntity.ok(recruiterService.getFollowUpReminders(currentUser, includeCompleted));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get recruiter contact detail",
            operationId = "getRecruiter",
            description = "Retrieves recruiter contact details and full chronological contact/draft history."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Recruiter detail and communication history",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Non-positive recruiter ID",
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
                    description = "Recruiter not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RecruiterResponse> getRecruiter(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @PathVariable("id") @Positive(message = "Recruiter ID must be positive") Long id
    ) {
        return ResponseEntity.ok(recruiterService.getRecruiter(currentUser, id));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update recruiter contact",
            operationId = "updateRecruiter",
            description = "Updates recruiter contact details owned by the authenticated user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Recruiter updated",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation error in update payload or recruiter ID",
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
                    description = "Recruiter or referenced company not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RecruiterResponse> updateRecruiter(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @PathVariable("id") @Positive(message = "Recruiter ID must be positive") Long id,
            @Valid @RequestBody UpdateRecruiterRequest request
    ) {
        return ResponseEntity.ok(recruiterService.updateRecruiter(currentUser, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete recruiter contact",
            operationId = "deleteRecruiter",
            description = "Deletes a recruiter contact owned by the authenticated user along with its interaction history."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Recruiter deleted successfully",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Non-positive recruiter ID",
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
                    description = "Recruiter not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Void>> deleteRecruiter(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @PathVariable("id") @Positive(message = "Recruiter ID must be positive") Long id
    ) {
        recruiterService.deleteRecruiter(currentUser, id);
        return ResponseEntity.ok(ApiResponse.ok("Recruiter contact deleted successfully"));
    }

    @PostMapping("/{id}/outreach/generate")
    @Operation(
            summary = "Generate editable AI outreach draft",
            operationId = "generateRecruiterOutreach",
            description = "Generates an editable LinkedIn connection message, recruiter introduction, application follow-up, or interview thank-you draft grounded in verified candidate and application evidence. Saved strictly as an unsent DRAFT; never auto-sent without explicit user approval."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Unsent AI outreach draft generated and persisted",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request or missing verified candidate evidence",
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
                    description = "Recruiter, linked application, or job not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "Upstream AI provider failure or invalid generated output",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "AI service disabled, unconfigured, or temporarily unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<OutreachDraftResponse> generateOutreach(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @PathVariable("id") @Positive(message = "Recruiter ID must be positive") Long id,
            @Valid @RequestBody(required = false) GenerateOutreachRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recruiterService.generateOutreach(currentUser, id, request));
    }

    @PostMapping("/{id}/interactions")
    @Operation(
            summary = "Log or compose recruiter interaction",
            operationId = "createRecruiterInteraction",
            description = "Creates a manual outreach draft or logs an explicitly confirmed sent communication in recruiter contact history."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Interaction recorded",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid payload or markAsSent=true without explicitApprovalConfirmed=true",
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
                    description = "Recruiter or linked application not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RecruiterInteractionDto> createInteraction(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @PathVariable("id") @Positive(message = "Recruiter ID must be positive") Long id,
            @Valid @RequestBody CreateRecruiterInteractionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recruiterService.createInteraction(currentUser, id, request));
    }

    @PutMapping("/{recruiterId}/interactions/{interactionId}")
    @Operation(
            summary = "Edit, approve, or mark outreach interaction as sent",
            operationId = "updateRecruiterInteraction",
            description = "Saves edits to a draft message, explicitly approves it without auto-sending (APPROVE), or logs manual dispatch only with explicitApprovalConfirmed=true (MARK_SENT)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Interaction updated",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid input or MARK_SENT requested without explicitApprovalConfirmed=true",
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
                    description = "Recruiter, interaction, or linked application not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RecruiterInteractionDto> updateInteraction(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @PathVariable("recruiterId") @Positive(message = "Recruiter ID must be positive") Long recruiterId,
            @PathVariable("interactionId") @Positive(message = "Interaction ID must be positive") Long interactionId,
            @Valid @RequestBody UpdateRecruiterInteractionRequest request
    ) {
        return ResponseEntity.ok(
                recruiterService.updateInteraction(currentUser, recruiterId, interactionId, request)
        );
    }

    @DeleteMapping("/{recruiterId}/interactions/{interactionId}")
    @Operation(
            summary = "Delete recruiter interaction",
            operationId = "deleteRecruiterInteraction",
            description = "Deletes an interaction or outreach draft owned by the authenticated user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Interaction deleted",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid ID parameter",
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
                    description = "Recruiter or interaction not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Void>> deleteInteraction(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @PathVariable("recruiterId") @Positive(message = "Recruiter ID must be positive") Long recruiterId,
            @PathVariable("interactionId") @Positive(message = "Interaction ID must be positive") Long interactionId
    ) {
        recruiterService.deleteInteraction(currentUser, recruiterId, interactionId);
        return ResponseEntity.ok(ApiResponse.ok("Recruiter interaction deleted successfully"));
    }
}

