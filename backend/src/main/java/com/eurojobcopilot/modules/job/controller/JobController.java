package com.eurojobcopilot.modules.job.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobDetailDto;
import com.eurojobcopilot.modules.job.dto.JobSearchCriteria;
import com.eurojobcopilot.modules.job.dto.PagedJobsResponse;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.service.JobQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Validated
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Jobs", description = "Job search, multi-facet filtering, personalized match ranking, and job discovery APIs")
public class JobController {

    private final JobQueryService jobQueryService;

    @GetMapping
    @Operation(
            summary = "Search and filter active jobs",
            operationId = "searchJobs",
            description = "Returns paginated active jobs filtered by keyword query, European country/city, work arrangement, employment type, seniority, visa sponsorship status, minimum salary, minimum user match score, and user interaction state."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Paginated job search results returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid filter, pagination, enum, or sort query parameter",
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
    public ResponseEntity<PagedJobsResponse> searchJobs(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Keyword query matched across title, company, location, and job description")
            @RequestParam(value = "query", required = false) @Size(max = 200) String query,
            @Parameter(description = "Country filter or ISO/common alias (e.g. Netherlands, Germany, DE, NL)")
            @RequestParam(value = "country", required = false) @Size(max = 100) String country,
            @Parameter(description = "Case-insensitive partial city or location match")
            @RequestParam(value = "city", required = false) @Size(max = 100) String city,
            @Parameter(description = "Work arrangement filter")
            @RequestParam(value = "workArrangement", required = false) WorkArrangement workArrangement,
            @Parameter(description = "Employment contract type filter")
            @RequestParam(value = "employmentType", required = false) EmploymentType employmentType,
            @Parameter(description = "Experience level filter")
            @RequestParam(value = "experienceLevel", required = false) ExperienceLevel experienceLevel,
            @Parameter(description = "Visa sponsorship verification status filter")
            @RequestParam(value = "sponsorStatus", required = false) JobSponsorStatus sponsorStatus,
            @Parameter(description = "Minimum user match score threshold (0..100)")
            @RequestParam(value = "minMatchScore", required = false) @Min(0) @Max(100) Integer minMatchScore,
            @Parameter(description = "Minimum advertised salary threshold")
            @RequestParam(value = "minSalary", required = false) @DecimalMin("0") BigDecimal minSalary,
            @Parameter(description = "Return only jobs saved by the current user")
            @RequestParam(value = "savedOnly", defaultValue = "false") boolean savedOnly,
            @Parameter(description = "Return only jobs marked interested by the current user")
            @RequestParam(value = "interestedOnly", defaultValue = "false") boolean interestedOnly,
            @Parameter(description = "Include jobs hidden by the current user (excluded by default)")
            @RequestParam(value = "includeHidden", defaultValue = "false") boolean includeHidden,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1..100)", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort order: postedAt,desc | postedAt,asc | matchScore,desc | salaryMax,desc", example = "postedAt,desc")
            @RequestParam(value = "sort", defaultValue = "postedAt,desc") @Size(max = 50) String sort
    ) {
        JobSearchCriteria criteria = JobSearchCriteria.builder()
                .query(query)
                .country(country)
                .city(city)
                .workArrangement(workArrangement)
                .employmentType(employmentType)
                .experienceLevel(experienceLevel)
                .sponsorStatus(sponsorStatus)
                .minMatchScore(minMatchScore)
                .minSalary(minSalary)
                .savedOnly(savedOnly)
                .interestedOnly(interestedOnly)
                .includeHidden(includeHidden)
                .page(page)
                .size(size)
                .sort(sort)
                .build();

        return ResponseEntity.ok(jobQueryService.searchJobs(currentUser, criteria));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get full job detail with sponsorship and match breakdown",
            operationId = "getJobDetail",
            description = "Retrieves the full job description, employer company profile, official visa sponsorship registry evidence, user-scoped compatibility sub-scores, cached AI analysis, and current interaction/application state."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Full job detail retrieved successfully",
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
    public ResponseEntity<JobDetailDto> getJobDetail(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Numeric job identifier", example = "1042")
            @PathVariable("id") @Positive Long id
    ) {
        return ResponseEntity.ok(jobQueryService.getJobDetail(currentUser, id));
    }
}

