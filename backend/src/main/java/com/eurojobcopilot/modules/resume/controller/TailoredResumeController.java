package com.eurojobcopilot.modules.resume.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.core.dto.ApiResponse;
import com.eurojobcopilot.core.validation.ValidUuid;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.resume.dto.GenerateTailoredResumeRequest;
import com.eurojobcopilot.modules.resume.dto.ResumeDownloadResource;
import com.eurojobcopilot.modules.resume.dto.ResumeExportFormat;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeResponse;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeSummaryDto;
import com.eurojobcopilot.modules.resume.dto.UpdateTailoredResumeRequest;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import com.eurojobcopilot.modules.resume.service.TailoredResumeService;
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
public class TailoredResumeController {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final TailoredResumeService tailoredResumeService;

    @PostMapping("/resume/generate")
    @Operation(
            summary = "Generate JD-tailored resume",
            operationId = "generateTailoredResume",
            description = "Tailors the authenticated candidate's verified profile, skills, work experience, education, and master CV to a target job description using AI with strict anti-hallucination grounding and deterministic ATS keyword coverage calculation."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Tailored resume generated, verified against candidate credentials, persisted, and returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload, missing master resume, or empty candidate/job evidence",
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
                    description = "Target job or specified master resume not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Referenced resume is not a MASTER resume",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "Upstream AI provider failure or generated resume failed anti-hallucination grounding verification",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "AI tailoring service is disabled, missing API credentials, or temporarily unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<TailoredResumeResponse> generateTailoredResume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Valid @RequestBody GenerateTailoredResumeRequest request
    ) {
        TailoredResumeResponse response = tailoredResumeService.generateTailoredResume(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/resumes")
    @Operation(
            summary = "List tailored resumes",
            operationId = "listTailoredResumes",
            description = "Lists persisted JD-tailored resume versions owned by the authenticated user, optionally filtered by target job ID."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "List of tailored resume summaries owned by the user",
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
    public ResponseEntity<List<TailoredResumeSummaryDto>> listTailoredResumes(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Optional target job ID filter", example = "1042")
            @RequestParam(name = "jobId", required = false)
            @Positive(message = "Job ID filter must be positive")
            Long jobId
    ) {
        return ResponseEntity.ok(tailoredResumeService.listTailoredResumes(currentUser, jobId));
    }

    @GetMapping("/resume/{uuid}")
    @Operation(
            summary = "Get tailored resume detail",
            operationId = "getTailoredResume",
            description = "Retrieves the full structured tailored resume draft, ATS keyword coverage, matched/missing keywords, and grounding notices."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Full tailored resume payload",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed tailored resume UUID path variable",
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
                    description = "Tailored resume not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<TailoredResumeResponse> getTailoredResume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical tailored resume UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Tailored resume UUID must be a valid canonical UUID") String uuid
    ) {
        return ResponseEntity.ok(tailoredResumeService.getTailoredResume(currentUser, uuid));
    }

    @PutMapping("/resume/{uuid}")
    @Operation(
            summary = "Save tailored resume draft edits",
            operationId = "updateTailoredResume",
            description = "Persists candidate edits to the tailored resume draft (title, template, summary, skills, experience bullets, projects), marks editedByUser=true, and recalculates ATS keyword coverage against the target job."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Tailored resume updated and ATS keyword coverage recalculated",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed UUID or validation failure in edited resume fields",
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
                    description = "Tailored resume not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<TailoredResumeResponse> updateTailoredResume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical tailored resume UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Tailored resume UUID must be a valid canonical UUID") String uuid,
            @Valid @RequestBody UpdateTailoredResumeRequest request
    ) {
        return ResponseEntity.ok(tailoredResumeService.updateTailoredResume(currentUser, uuid, request));
    }

    @GetMapping(value = "/resume/{uuid}/export", produces = {MIME_PDF, MIME_DOCX, MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            summary = "Export tailored resume to PDF or DOCX",
            operationId = "exportTailoredResume",
            description = "Renders the owned tailored resume draft using its configured (or overridden) visual template into an ATS-friendly PDF or OOXML DOCX binary stream, caching default-template outputs until the draft is edited or deleted."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Exported PDF or DOCX resume binary stream",
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
                                    schema = @Schema(type = "string", format = "binary"))
                    }
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed UUID, missing/unsupported export format, or invalid template parameter",
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
                    description = "Tailored resume not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<Resource> exportTailoredResume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical tailored resume UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Tailored resume UUID must be a valid canonical UUID") String uuid,
            @Parameter(description = "Export file format (`pdf` or `docx`)", required = true,
                    schema = @Schema(allowableValues = {"pdf", "docx"}), example = "pdf")
            @RequestParam("format") String format,
            @Parameter(description = "Optional template style override (defaults to the draft's saved templateName)")
            @RequestParam(name = "template", required = false) ResumeTemplate template,
            @Parameter(description = "Force binary regeneration instead of returning cached export (default false)")
            @RequestParam(name = "regenerate", defaultValue = "false") boolean regenerate
    ) {
        ResumeExportFormat exportFormat = ResumeExportFormat.fromQueryParam(format);
        ResumeDownloadResource export = tailoredResumeService.exportTailoredResume(
                currentUser,
                uuid,
                exportFormat,
                template,
                regenerate
        );

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

    @DeleteMapping("/resume/{uuid}")
    @Operation(
            summary = "Delete tailored resume draft",
            operationId = "deleteTailoredResume",
            description = "Deletes a tailored resume draft owned by the authenticated user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Tailored resume deleted successfully",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed tailored resume UUID path variable",
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
                    description = "Tailored resume not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Void>> deleteTailoredResume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical tailored resume UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Tailored resume UUID must be a valid canonical UUID") String uuid
    ) {
        tailoredResumeService.deleteTailoredResume(currentUser, uuid);
        return ResponseEntity.ok(ApiResponse.ok("Tailored resume deleted successfully"));
    }
}

