package com.eurojobcopilot.modules.resume.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.core.dto.ApiResponse;
import com.eurojobcopilot.core.validation.ValidUuid;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.resume.dto.ApproveMasterResumeRequest;
import com.eurojobcopilot.modules.resume.dto.ResumeDownloadResource;
import com.eurojobcopilot.modules.resume.dto.ResumeParseResponse;
import com.eurojobcopilot.modules.resume.dto.ResumeResponse;
import com.eurojobcopilot.modules.resume.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Resumes", description = "Master resume secure upload, AI parsing, human approval, metadata management, download, and deletion APIs")
public class ResumeController {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ResumeService resumeService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload master resume",
            operationId = "uploadMasterResume",
            description = "Validates extension, MIME type, file magic numbers / OOXML ZIP structure, and 10MB size limit; securely stores a PDF or DOCX master resume under a UUID-keyed path."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Master resume uploaded and stored successfully",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid file type, magic-byte mismatch, path-traversal filename, empty file, missing part, or blank title",
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
                    responseCode = "413",
                    description = "Uploaded file exceeds maximum allowed 10MB payload limit",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ResumeResponse> uploadMasterResume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "PDF or DOCX resume file (max 10MB)")
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Display title for the resume (max 200 characters)")
            @RequestParam("title")
            @NotBlank(message = "Resume title is required and must not be blank")
            @Size(max = 200, message = "Resume title must not exceed 200 characters")
            String title
    ) {
        ResumeResponse response = resumeService.uploadMasterResume(currentUser, file, title);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
            summary = "List uploaded resumes",
            operationId = "listResumes",
            description = "Lists all resumes belonging to the authenticated user ordered newest version first."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "List of resume metadata records owned by the user",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<List<ResumeResponse>> listResumes(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(resumeService.listResumes(currentUser));
    }

    @GetMapping("/{uuid}")
    @Operation(
            summary = "Get resume metadata",
            operationId = "getResume",
            description = "Retrieves metadata for a specific resume owned by the authenticated user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Resume metadata record",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed resume UUID path variable",
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
                    description = "Resume not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ResumeResponse> getResume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical resume UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Resume UUID must be a valid canonical UUID") String uuid
    ) {
        return ResponseEntity.ok(resumeService.getResume(currentUser, uuid));
    }

    @PostMapping("/{uuid}/parse")
    @Operation(
            summary = "Parse master resume with AI",
            operationId = "parseMasterResume",
            description = "Extracts text from an uploaded PDF or DOCX master resume, invokes the AI structured parser, validates the extracted JSON draft, and stores it for mandatory human review without auto-approving."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Structured master resume draft extracted and saved for human review",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed UUID, unreadable/encrypted/scanned PDF or DOCX file",
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
                    description = "Resume or underlying stored file not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Target resume is not a MASTER resume",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "AI provider returned invalid or contradictory structured resume JSON",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "AI parsing service is disabled, missing API credentials, or temporarily unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ResumeParseResponse> parseMasterResume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical master resume UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Resume UUID must be a valid canonical UUID") String uuid,
            @Parameter(description = "Re-run extraction and AI parsing even if already parsed (default true)")
            @RequestParam(name = "forceRefresh", defaultValue = "true") boolean forceRefresh
    ) {
        return ResponseEntity.ok(resumeService.parseMasterResume(currentUser, uuid, forceRefresh));
    }

    @GetMapping("/{uuid}/parsed")
    @Operation(
            summary = "Get parsed master resume draft",
            operationId = "getParsedMasterResume",
            description = "Retrieves stored structured master resume draft JSON and parse status for human review."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Stored parse status and structured draft payload (parsedData is null when NOT_PARSED)",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed resume UUID path variable",
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
                    description = "Resume not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Target resume is not a MASTER resume",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ResumeParseResponse> getParsedResume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical master resume UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Resume UUID must be a valid canonical UUID") String uuid
    ) {
        return ResponseEntity.ok(resumeService.getParsedResume(currentUser, uuid));
    }

    @PostMapping("/master/approve")
    @Operation(
            summary = "Approve master resume and sync profile",
            operationId = "approveMasterResume",
            description = "Commits human-reviewed structured CV data onto the master resume, marks isMasterApproved=true (demoting previously approved versions), and synchronizes confirmed profile fields, skills, work experiences, and educations into the candidate profile."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Master resume approved and synchronized into candidate profile",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation error in approved payload (e.g., missing experience startDate, missing education country, inverted date range, invalid URL)",
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
                    description = "Master resume not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Target resume is not a MASTER resume or concurrent modification occurred",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ResumeResponse> approveMasterResume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Valid @RequestBody ApproveMasterResumeRequest request
    ) {
        return ResponseEntity.ok(resumeService.approveMasterResume(currentUser, request));
    }

    @GetMapping(value = "/{uuid}/download", produces = {MIME_PDF, MIME_DOCX, MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            summary = "Download resume file",
            operationId = "downloadResume",
            description = "Streams the stored binary PDF or DOCX resume file owned by the authenticated user with attachment and nosniff headers."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Binary resume file stream",
                    headers = {
                            @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                                    description = "Attachment content disposition containing the sanitized filename",
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
                    description = "Malformed resume UUID path variable",
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
                    description = "Resume or underlying stored file not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<Resource> downloadResume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical resume UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Resume UUID must be a valid canonical UUID") String uuid
    ) {
        ResumeDownloadResource download = resumeService.downloadResume(currentUser, uuid);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.mimeType());
        } catch (InvalidMediaTypeException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.noStore().mustRevalidate());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaType)
                .contentLength(download.fileSizeBytes())
                .body(download.resource());
    }

    @DeleteMapping("/{uuid}")
    @Operation(
            summary = "Delete resume",
            operationId = "deleteResume",
            description = "Deletes the resume metadata record and stored file if owned by the authenticated user and unreferenced by tailored resumes."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Resume deleted successfully",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed resume UUID path variable",
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
                    description = "Resume not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Resume cannot be deleted because one or more tailored resumes reference it",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Void>> deleteResume(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Canonical resume UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("uuid") @ValidUuid(message = "Resume UUID must be a valid canonical UUID") String uuid
    ) {
        resumeService.deleteResume(currentUser, uuid);
        return ResponseEntity.ok(ApiResponse.ok("Resume deleted successfully"));
    }
}
