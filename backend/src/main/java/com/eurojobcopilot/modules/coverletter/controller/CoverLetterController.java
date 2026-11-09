package com.eurojobcopilot.modules.coverletter.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.core.dto.ApiResponse;
import com.eurojobcopilot.core.validation.ValidUuid;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterDownloadResource;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterExportFormat;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterResponse;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterSummaryDto;
import com.eurojobcopilot.modules.coverletter.dto.GenerateCoverLetterRequest;
import com.eurojobcopilot.modules.coverletter.dto.UpdateCoverLetterRequest;
import com.eurojobcopilot.modules.coverletter.service.CoverLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
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

import java.nio.charset.StandardCharsets;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/tailored-documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Tailored Documents")
public class CoverLetterController {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String MIME_TEXT = MediaType.TEXT_PLAIN_VALUE;

    private final CoverLetterService coverLetterService;

    @PostMapping("/cover-letter/generate")
    @Operation(
            summary = "Generate JD-tailored cover letter",
            operationId = "generateCoverLetter",
            description = "Drafts a grounded European cover letter connecting the authenticated candidate's verified profile, skills, work history, optional linked tailored CV, and visa/relocation readiness to a target company and job posting."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Cover letter generated, verified against candidate evidence, persisted, and returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload or empty candidate/job evidence",
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
                    description = "Target job or specified tailored resume not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "Upstream AI provider failure or generated cover letter failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "AI cover letter generation service is disabled, missing API credentials, or temporarily unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<CoverLetterResponse> generateCoverLetter(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Valid @RequestBody GenerateCoverLetterRequest request
    ) {
        CoverLetterResponse response = coverLetterService.generateCoverLetter(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/cover-letters")
    @Operation(
            summary = "List tailored cover letters",
            operationId = "listCoverLetters",
            description = "Lists persisted AI cover letter versions owned by the authenticated user, optionally filtered by target job ID."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "List of cover letter summaries owned by the user",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid non-positive jobId filter parameter",
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
    public ResponseEntity<List<CoverLetterSummaryDto>> listCoverLetters(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Optional target job ID filter", example = "1042")
            @RequestParam(name = "jobId", required = false)
            @Positive(message = "Job ID filter must be positive")
            Long jobId
    ) {
        return ResponseEntity.ok(coverLetterService.listCoverLetters(currentUser, jobId));
    }

    @GetMapping("/cover-letter/{uuid}")
    @Operation(
            summary = "Get cover letter detail",
            operationId = "getCoverLetter",
            description = "Retrieves the full cover letter prose, alignment metadata, verified highlighted skills, work-authorization readiness note, and grounding notices."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Full cover letter payload",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed cover letter UUID path variable",
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
                    description = "Cover letter not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<CoverLetterResponse> getCoverLetter(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical cover letter UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Cover letter UUID must be a valid canonical UUID") String uuid
    ) {
        return ResponseEntity.ok(coverLetterService.getCoverLetter(currentUser, uuid));
    }

    @PutMapping("/cover-letter/{uuid}")
    @Operation(
            summary = "Save cover letter draft edits",
            operationId = "updateCoverLetter",
            description = "Persists candidate edits to the cover letter title, tone, personal notes, or letter content, marks editedByUser=true, recalculates word count, and refreshes grounding checks."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Cover letter updated and revalidated",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed UUID or validation failure in edited cover letter fields",
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
                    description = "Cover letter not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<CoverLetterResponse> updateCoverLetter(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical cover letter UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Cover letter UUID must be a valid canonical UUID") String uuid,
            @Valid @RequestBody UpdateCoverLetterRequest request
    ) {
        return ResponseEntity.ok(coverLetterService.updateCoverLetter(currentUser, uuid, request));
    }

    @GetMapping(value = "/cover-letter/{uuid}/export", produces = {MIME_PDF, MIME_DOCX, MIME_TEXT, MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            summary = "Export cover letter to PDF, DOCX, or TXT",
            operationId = "exportCoverLetter",
            description = "Renders the owned cover letter draft as a formal A4 European business letter in PDF, OOXML DOCX, or UTF-8 plain text format."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Exported PDF, DOCX, or TXT cover letter stream",
                    headers = {
                            @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                                    description = "Attachment content disposition with sanitized export filename",
                                    schema = @Schema(type = "string")),
                            @Header(name = "X-Content-Type-Options",
                                    description = "Set to nosniff to prevent MIME sniffing",
                                    schema = @Schema(type = "string"))
                    },
                    content = {
                            @Content(mediaType = MIME_PDF,
                                    schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = MIME_DOCX,
                                    schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = MIME_TEXT,
                                    schema = @Schema(type = "string", format = "binary"))
                    }
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed UUID or missing/unsupported export format",
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
                    description = "Cover letter not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<Resource> exportCoverLetter(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical cover letter UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Cover letter UUID must be a valid canonical UUID") String uuid,
            @Parameter(description = "Export file format (`pdf`, `docx`, or `txt`)", required = true,
                    schema = @Schema(allowableValues = {"pdf", "docx", "txt"}), example = "pdf")
            @RequestParam("format") String format
    ) {
        CoverLetterExportFormat exportFormat = CoverLetterExportFormat.fromQueryParam(format);
        CoverLetterDownloadResource export = coverLetterService.exportCoverLetter(currentUser, uuid, exportFormat);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(export.mimeType());
        } catch (InvalidMediaTypeException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(export.fileName(), StandardCharsets.UTF_8)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.noStore().mustRevalidate());

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaType);
        if (export.fileSizeBytes() >= 0) {
            responseBuilder.contentLength(export.fileSizeBytes());
        }
        return responseBuilder.body(export.resource());
    }

    @DeleteMapping("/cover-letter/{uuid}")
    @Operation(
            summary = "Delete cover letter draft",
            operationId = "deleteCoverLetter",
            description = "Deletes a cover letter draft owned by the authenticated user and clears any application draft linkage."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Cover letter deleted successfully",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed cover letter UUID path variable",
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
                    description = "Cover letter not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Void>> deleteCoverLetter(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical cover letter UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Cover letter UUID must be a valid canonical UUID") String uuid
    ) {
        coverLetterService.deleteCoverLetter(currentUser, uuid);
        return ResponseEntity.ok(ApiResponse.ok("Cover letter deleted successfully"));
    }
}

