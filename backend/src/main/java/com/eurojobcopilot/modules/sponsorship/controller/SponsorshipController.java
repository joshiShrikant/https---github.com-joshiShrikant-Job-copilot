package com.eurojobcopilot.modules.sponsorship.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.sponsorship.dto.CountrySponsorshipProfileDto;
import com.eurojobcopilot.modules.sponsorship.dto.PagedIndRegistryResponse;
import com.eurojobcopilot.modules.sponsorship.dto.PagedSponsorshipHistoryResponse;
import com.eurojobcopilot.modules.sponsorship.dto.SponsorshipVerificationResponseDto;
import com.eurojobcopilot.modules.sponsorship.service.SponsorshipIntelligenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/sponsorship")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(
        name = "Sponsorship Intelligence",
        description = "Country-specific European visa sponsorship rules across 14 countries, Netherlands IND register lookup, company verification, and audit history"
)
public class SponsorshipController {

    private final SponsorshipIntelligenceService sponsorshipService;

    @GetMapping("/countries")
    @Operation(
            summary = "List supported countries and official sponsor register guidance",
            operationId = "getSponsorshipCountries",
            description = "Returns country-specific visa sponsorship guidance, statutory pathways, official government URLs, and synced register counts for all 14 supported European target countries."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Country sponsorship profiles returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<List<CountrySponsorshipProfileDto>> getCountries() {
        return ResponseEntity.ok(sponsorshipService.getCountryProfiles());
    }

    @GetMapping("/verify")
    @Operation(
            summary = "Verify company visa sponsorship status in a target European country",
            operationId = "verifySponsorshipStatus",
            description = "Evaluates official government sponsor registers (including Netherlands IND), employer intelligence, and optional vacancy text or jobId across all 6 sponsorship statuses while enforcing that recognized sponsorship never assumes every vacancy offers sponsorship. Persists a verification history audit snapshot."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Sponsorship verification completed and history snapshot stored",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid or unsupported country, blank companyName, or invalid jobId",
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
                    description = "Optional jobId was specified but does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<SponsorshipVerificationResponseDto> verifySponsorship(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Employer name to verify", example = "Adyen N.V.")
            @RequestParam("companyName") @NotBlank @Size(max = 255) String companyName,
            @Parameter(description = "European country name or ISO code (14 supported countries)", example = "Netherlands")
            @RequestParam("country") @NotBlank @Size(max = 100) String country,
            @Parameter(description = "Optional job listing ID to evaluate vacancy-level exclusions or confirmations")
            @RequestParam(value = "jobId", required = false) @Positive Long jobId,
            @Parameter(description = "Optional vacancy posting snippet to inspect for explicit sponsorship or exclusion cues")
            @RequestParam(value = "postingText", required = false) @Size(max = 8000) String postingText
    ) {
        return ResponseEntity.ok(
                sponsorshipService.verifyCompanySponsorship(currentUser, companyName, country, jobId, postingText)
        );
    }

    @GetMapping("/history")
    @Operation(
            summary = "Get user verification history",
            operationId = "listSponsorshipVerificationHistory",
            description = "Returns paginated sponsorship verification history created by the authenticated user, optionally filtered by company name or country."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Paginated verification history returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination or unsupported country filter",
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
    public ResponseEntity<PagedSponsorshipHistoryResponse> listHistory(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Optional partial company name filter")
            @RequestParam(value = "companyName", required = false) @Size(max = 255) String companyName,
            @Parameter(description = "Optional country or ISO filter")
            @RequestParam(value = "country", required = false) @Size(max = 100) String country,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1..100)", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(
                sponsorshipService.getVerificationHistory(currentUser, companyName, country, page, size)
        );
    }

    @GetMapping("/registry/ind")
    @Operation(
            summary = "Search synced Netherlands IND recognized sponsor register",
            operationId = "searchIndRecognizedSponsors",
            description = "Searches active official sponsor entries synchronized from the Netherlands IND Public Register along with latest sync status telemetry."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Paginated IND sponsor entries returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination or query parameter",
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
    public ResponseEntity<PagedIndRegistryResponse> searchIndRegistry(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Optional company name or KvK identifier filter")
            @RequestParam(value = "query", required = false) @Size(max = 200) String query,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1..100)", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(sponsorshipService.searchIndRegistry(currentUser, query, page, size));
    }
}

