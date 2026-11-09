package com.eurojobcopilot.modules.analytics.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.modules.analytics.dto.MarketInsightsResponse;
import com.eurojobcopilot.modules.analytics.service.MarketAnalyticsService;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Dashboard & Analytics")
public class MarketAnalyticsController {

    private final MarketAnalyticsService marketAnalyticsService;

    @GetMapping("/market-insights")
    @Operation(
            summary = "Get European job market analytics across collected countries",
            operationId = "getMarketInsights",
            description = "Compares discovered European engineering jobs across countries including Angular, Frontend, Java, and Full-stack counts, salary bands, visa sponsorship opportunities, English-speaking verification, remote ratios, discovery trends over time, connector source coverage, and explicit data caveats."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "European job market comparison metrics returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid analytics query parameter (e.g., days outside 7..180)",
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
    public ResponseEntity<MarketInsightsResponse> getMarketInsights(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Optional list of European countries to compare (defaults to all discovered countries)")
            @RequestParam(value = "countries", required = false) List<String> countries,
            @Parameter(description = "Discovery trend lookback window in days (7..180)", example = "30")
            @RequestParam(value = "days", defaultValue = "30") @Min(7) @Max(180) int days
    ) {
        return ResponseEntity.ok(marketAnalyticsService.getMarketInsights(currentUser, countries, days));
    }
}

