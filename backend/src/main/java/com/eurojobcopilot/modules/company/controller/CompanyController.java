package com.eurojobcopilot.modules.company.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.company.dto.CompanyDetailDto;
import com.eurojobcopilot.modules.company.dto.CompanySaveResponse;
import com.eurojobcopilot.modules.company.dto.CompanySearchCriteria;
import com.eurojobcopilot.modules.company.dto.PagedCompaniesResponse;
import com.eurojobcopilot.modules.company.dto.SaveCompanyRequest;
import com.eurojobcopilot.modules.company.service.CompanyDirectoryService;
import com.eurojobcopilot.modules.job.dto.PagedJobsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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

@Validated
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Companies", description = "European employer intelligence directory, sponsorship verification dossiers, open roles, and target company bookmarks")
public class CompanyController {

    private final CompanyDirectoryService companyDirectoryService;

    @GetMapping
    @Operation(
            summary = "Search and filter European employers",
            operationId = "searchCompanies",
            description = "Returns paginated employer intelligence summaries filtered by keyword query, European country alias/name, industry vertical, technology stack, recognized sponsor verification, active openings, and current-user target bookmarks."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Paginated employer intelligence directory results returned",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid filter, pagination, or sort query parameter",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<PagedCompaniesResponse> searchCompanies(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Keyword query matched across employer name, headquarters location, industry, overview, and sponsor registry names")
            @RequestParam(value = "query", required = false) @Size(max = 200) String query,
            @Parameter(description = "European country filter or ISO/common alias (e.g. Netherlands, NL, Germany, DE)")
            @RequestParam(value = "country", required = false) @Size(max = 100) String country,
            @Parameter(description = "Case-insensitive partial industry match (e.g. FinTech, Cloud, E-Commerce)")
            @RequestParam(value = "industry", required = false) @Size(max = 100) String industry,
            @Parameter(description = "Technology or programming language keyword matched against curated company stack and active role requirements")
            @RequestParam(value = "tech", required = false) @Size(max = 100) String tech,
            @Parameter(description = "Return only companies with recognized sponsor verification")
            @RequestParam(value = "verifiedSponsorOnly", defaultValue = "false") boolean verifiedSponsorOnly,
            @Parameter(description = "Return only target employers bookmarked by the current user")
            @RequestParam(value = "savedOnly", defaultValue = "false") boolean savedOnly,
            @Parameter(description = "Return only companies with at least one active non-expired job listing")
            @RequestParam(value = "withOpenJobsOnly", defaultValue = "false") boolean withOpenJobsOnly,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1..100)", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort order: activeJobs,desc | verified,desc | name,asc | savedAt,desc", example = "activeJobs,desc")
            @RequestParam(value = "sort", defaultValue = "activeJobs,desc") @Size(max = 50) String sort
    ) {
        CompanySearchCriteria criteria = CompanySearchCriteria.builder()
                .query(query)
                .country(country)
                .industry(industry)
                .tech(tech)
                .verifiedSponsorOnly(verifiedSponsorOnly)
                .savedOnly(savedOnly)
                .withOpenJobsOnly(withOpenJobsOnly)
                .page(page)
                .size(size)
                .sort(sort)
                .build();

        return ResponseEntity.ok(companyDirectoryService.searchCompanies(currentUser, criteria));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get employer intelligence dossier",
            operationId = "getCompanyDetail",
            description = "Returns comprehensive employer profile metadata, sanitized external links, aggregated tech stack, active European hiring footprint, official sponsor registry records, mandatory vacancy non-assumption caveat, and user bookmark state."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Employer intelligence dossier returned",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid non-positive or malformed company identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Company with the specified ID was not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<CompanyDetailDto> getCompanyDetail(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric company identifier", example = "12")
            @PathVariable("id") @Positive Long id
    ) {
        return ResponseEntity.ok(companyDirectoryService.getCompanyDetail(currentUser, id));
    }

    @GetMapping("/{id}/jobs")
    @Operation(
            summary = "List active open jobs for an employer",
            operationId = "getCompanyJobs",
            description = "Returns paginated active, non-expired openings associated with the specified employer, enriched with user-scoped compatibility scores and interaction flags."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Paginated active open jobs for the employer returned",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid company identifier, pagination, or sort parameter",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Company with the specified ID was not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<PagedJobsResponse> getCompanyJobs(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric company identifier", example = "12")
            @PathVariable("id") @Positive Long id,
            @Parameter(description = "Include jobs hidden by the current user (excluded by default)")
            @RequestParam(value = "includeHidden", defaultValue = "false") boolean includeHidden,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1..100)", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort order: postedAt,desc | postedAt,asc | matchScore,desc | salaryMax,desc", example = "postedAt,desc")
            @RequestParam(value = "sort", defaultValue = "postedAt,desc") @Size(max = 50) String sort
    ) {
        return ResponseEntity.ok(companyDirectoryService.getCompanyJobs(
                currentUser, id, includeHidden, page, size, sort
        ));
    }

    @PostMapping("/{id}/save")
    @Operation(
            summary = "Bookmark or update a target company",
            operationId = "saveCompany",
            description = "Idempotently bookmarks the specified employer for the authenticated user with an optional target priority tier and personal notes."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Target company bookmark created or updated",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid company identifier, priority tier, or notes exceeding 2000 characters",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Company with the specified ID was not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<CompanySaveResponse> saveCompany(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric company identifier", example = "12")
            @PathVariable("id") @Positive Long id,
            @RequestBody(required = false) @Valid SaveCompanyRequest request
    ) {
        return ResponseEntity.ok(companyDirectoryService.saveCompany(currentUser, id, request));
    }

    @DeleteMapping("/{id}/save")
    @Operation(
            summary = "Remove a target company bookmark",
            operationId = "unsaveCompany",
            description = "Idempotently removes the current user's bookmark for the specified employer."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Target company bookmark removed",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid non-positive or malformed company identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Company with the specified ID was not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<CompanySaveResponse> unsaveCompany(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric company identifier", example = "12")
            @PathVariable("id") @Positive Long id
    ) {
        return ResponseEntity.ok(companyDirectoryService.unsaveCompany(currentUser, id));
    }
}

