package com.eurojobcopilot.modules.auth.controller;

import com.eurojobcopilot.modules.auth.dto.LoginRequest;
import com.eurojobcopilot.modules.auth.dto.RefreshTokenRequest;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.RefreshToken;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.service.TokenHashUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    @RequestMapping("/api/v1")
    static class TestSecurityEndpoints {

        @GetMapping("/admin/security-check")
        public Map<String, String> adminUrlProtected() {
            return Map.of("scope", "admin-url");
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/security-test/admin-method")
        public Map<String, String> adminMethodProtected() {
            return Map.of("scope", "admin-method");
        }
    }

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void protectedEndpoints_unauthenticatedAndMalformedBearer_return401Json() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/me"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("Authentication required")));
    }

    @Test
    void currentUserEndpoint_validJwt_returnsUserProfileAndRejectsSuspendedUserAfterIssuance() throws Exception {
        AuthResult session = registerAndGetTokens("me.user@eurojob.eu", "SecureP@ssw0rd2026!");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me.user@eurojob.eu"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        User user = userRepository.findByEmailIgnoreCase("me.user@eurojob.eu").orElseThrow();
        user.setAccountStatus(AccountStatus.SUSPENDED);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void refreshToken_cookieAndBodyRotationAndReplayDetection_worksEndToEnd() throws Exception {
        AuthResult initial = registerAndGetTokens("rotate@eurojob.eu", "SecureP@ssw0rd2026!");
        User user = userRepository.findByEmailIgnoreCase("rotate@eurojob.eu").orElseThrow();

        // 1. Rotate via HttpOnly cookie
        MvcResult cookieRefreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(initial.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andReturn();

        Cookie rotatedCookie = cookieRefreshResult.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(rotatedCookie).isNotNull();
        assertThat(rotatedCookie.getValue()).isNotEqualTo(initial.refreshCookie().getValue());

        String rotatedAccessToken = objectMapper.readTree(cookieRefreshResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rotatedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("rotate@eurojob.eu"));

        RefreshToken oldTokenEntity = refreshTokenRepository
                .findByTokenHash(TokenHashUtil.sha256Hex(initial.refreshCookie().getValue()))
                .orElseThrow();
        assertThat(oldTokenEntity.isRevoked()).isTrue();
        assertThat(oldTokenEntity.getReplacedByToken())
                .isEqualTo(TokenHashUtil.sha256Hex(rotatedCookie.getValue()));

        // 2. Rotate via JSON body using the newly rotated token
        MvcResult bodyRefreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(rotatedCookie.getValue()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        Cookie thirdCookie = bodyRefreshResult.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(thirdCookie).isNotNull();
        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId())).hasSize(1);

        // 3. Replay old (already replaced) refresh token -> must return 401 AND revoke ALL active user tokens
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(initial.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("revoked")));

        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId())).isEmpty();

        // 4. Subsequent attempt with thirdCookie fails because replay wiped the entire session family
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(thirdCookie))
                .andExpect(status().isUnauthorized());

        // 5. Missing refresh token fails with 401
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleBasedAuthorization_enforcesUrlAndMethodGuards() throws Exception {
        AuthResult userSession = registerAndGetTokens("standard@eurojob.eu", "SecureP@ssw0rd2026!");

        userRepository.saveAndFlush(User.builder()
                .email("admin@eurojob.eu")
                .passwordHash(passwordEncoder.encode("AdminP@ssw0rd2026!"))
                .firstName("Super")
                .lastName("Admin")
                .role(UserRole.ROLE_ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build());

        AuthResult adminSession = loginAndGetTokens("admin@eurojob.eu", "AdminP@ssw0rd2026!");

        // Unauthenticated -> 401
        mockMvc.perform(get("/api/v1/admin/security-check"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/security-test/admin-method"))
                .andExpect(status().isUnauthorized());

        // ROLE_USER -> 403 JSON ApiErrorResponse on URL guard, method guard, and non-public actuator endpoints
        mockMvc.perform(get("/api/v1/admin/security-check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userSession.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(containsString("Access denied")));

        mockMvc.perform(get("/api/v1/security-test/admin-method")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userSession.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userSession.accessToken()))
                .andExpect(status().isForbidden());

        // ROLE_ADMIN -> 200 OK
        mockMvc.perform(get("/api/v1/admin/security-check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminSession.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("admin-url"));

        mockMvc.perform(get("/api/v1/security-test/admin-method")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminSession.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("admin-method"));

        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminSession.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void corsPreflightAndPublicEndpoints_enforceConfiguredOriginsAndPermits() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    private AuthResult registerAndGetTokens(String email, String password) throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email(email)
                .password(password)
                .firstName("Test")
                .lastName("User")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthResult(
                json.get("accessToken").asText(),
                result.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE_NAME)
        );
    }

    private AuthResult loginAndGetTokens(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthResult(
                json.get("accessToken").asText(),
                result.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE_NAME)
        );
    }

    private record AuthResult(String accessToken, Cookie refreshCookie) {
    }
}
