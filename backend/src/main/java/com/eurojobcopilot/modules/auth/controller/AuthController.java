package com.eurojobcopilot.modules.auth.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.core.dto.ApiResponse;
import com.eurojobcopilot.modules.auth.config.AuthProperties;
import com.eurojobcopilot.modules.auth.config.JwtProperties;
import com.eurojobcopilot.modules.auth.dto.AuthResponse;
import com.eurojobcopilot.modules.auth.dto.AuthSessionResult;
import com.eurojobcopilot.modules.auth.dto.LoginRequest;
import com.eurojobcopilot.modules.auth.dto.LogoutRequest;
import com.eurojobcopilot.modules.auth.dto.RefreshTokenRequest;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.dto.TokenRefreshResponse;
import com.eurojobcopilot.modules.auth.dto.TokenRefreshResult;
import com.eurojobcopilot.modules.auth.dto.UserSummaryDto;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, login, JWT issuance, token rotation, and logout endpoints")
public class AuthController {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final String AUTH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final AuthProperties authProperties;
    private final JwtProperties jwtProperties;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            operationId = "registerUser",
            description = "Validates email uniqueness, hashes password using BCrypt, issues a short-lived JWT access token, and sets an HttpOnly SameSite=Strict refresh_token cookie."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "User registered successfully",
                    headers = @Header(name = HttpHeaders.SET_COOKIE,
                            description = "HttpOnly refresh_token cookie scoped to /api/v1/auth",
                            schema = @Schema(type = "string")),
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid registration input or password complexity violation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Email address is already registered",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthSessionResult result = authService.register(request);
        ResponseCookie cookie = buildRefreshTokenCookie(result.getRawRefreshToken());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.getResponse());
    }

    @PostMapping("/login")
    @Operation(
            summary = "Authenticate user",
            operationId = "loginUser",
            description = "Verifies BCrypt credentials and account status, and issues a JWT access token + HttpOnly refresh_token cookie."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Successfully authenticated",
                    headers = @Header(name = HttpHeaders.SET_COOKIE,
                            description = "HttpOnly refresh_token cookie scoped to /api/v1/auth",
                            schema = @Schema(type = "string")),
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid email format or missing required credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid email or password, or account suspended/pending verification",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthSessionResult result = authService.login(request);
        ResponseCookie cookie = buildRefreshTokenCookie(result.getRawRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.getResponse());
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh JWT access token",
            operationId = "refreshToken",
            description = "Rotates the refresh token supplied via HttpOnly cookie or optional request body, enforces replay detection, and issues a new JWT access token."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Access token refreshed and refresh token rotated",
                    headers = @Header(name = HttpHeaders.SET_COOKIE,
                            description = "Rotated HttpOnly refresh_token cookie",
                            schema = @Schema(type = "string")),
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Malformed JSON payload",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Refresh token missing, invalid, expired, or replayed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<TokenRefreshResponse> refresh(
            @Parameter(in = ParameterIn.COOKIE, name = REFRESH_TOKEN_COOKIE_NAME,
                    description = "HttpOnly refresh token cookie issued at registration/login")
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshTokenCookie,
            @RequestBody(required = false) RefreshTokenRequest refreshTokenRequest
    ) {
        String rawToken = (refreshTokenRequest != null && refreshTokenRequest.getRefreshToken() != null
                && !refreshTokenRequest.getRefreshToken().isBlank())
                ? refreshTokenRequest.getRefreshToken()
                : refreshTokenCookie;

        TokenRefreshResult result = authService.refreshToken(rawToken);
        ResponseCookie rotatedCookie = buildRefreshTokenCookie(result.getRawRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rotatedCookie.toString())
                .body(result.getResponse());
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Logout and invalidate session",
            operationId = "logoutUser",
            description = "Revokes the active refresh token(s) in the database (single session or all sessions) and expires the refresh_token cookie."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Logged out successfully",
                    headers = @Header(name = HttpHeaders.SET_COOKIE,
                            description = "Cleared refresh_token cookie (Max-Age=0)",
                            schema = @Schema(type = "string")),
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(in = ParameterIn.COOKIE, name = REFRESH_TOKEN_COOKIE_NAME,
                    description = "HttpOnly refresh token cookie to revoke")
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshTokenCookie,
            @RequestBody(required = false) LogoutRequest logoutRequest
    ) {
        String tokenToRevoke = (logoutRequest != null && logoutRequest.getRefreshToken() != null
                && !logoutRequest.getRefreshToken().isBlank())
                ? logoutRequest.getRefreshToken()
                : refreshTokenCookie;
        boolean revokeAll = logoutRequest != null && logoutRequest.isRevokeAllSessions();

        authService.logout(currentUser, tokenToRevoke, revokeAll);

        ResponseCookie clearCookie = clearRefreshTokenCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(ApiResponse.ok("Logged out successfully"));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Get current authenticated user",
            operationId = "getCurrentUser",
            description = "Returns summary information for the authenticated JWT principal."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Current authenticated user summary",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<UserSummaryDto> getCurrentUser(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(authService.getCurrentUser(currentUser));
    }

    private ResponseCookie buildRefreshTokenCookie(String rawToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(authProperties.isCookieSecure())
                .path(AUTH_COOKIE_PATH)
                .maxAge(Duration.ofSeconds(jwtProperties.getRefreshTokenExpirationSeconds()))
                .sameSite("Strict")
                .build();
    }

    private ResponseCookie clearRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authProperties.isCookieSecure())
                .path(AUTH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .sameSite("Strict")
                .build();
    }
}
