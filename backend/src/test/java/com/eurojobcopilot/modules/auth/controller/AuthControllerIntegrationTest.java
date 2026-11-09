package com.eurojobcopilot.modules.auth.controller;

import com.eurojobcopilot.modules.auth.dto.LoginRequest;
import com.eurojobcopilot.modules.auth.dto.LogoutRequest;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

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

        @BeforeEach
        void cleanDatabase() {
                refreshTokenRepository.deleteAllInBatch();
                userRepository.deleteAllInBatch();
        }

        @Test
        void register_validPayload_returns201PersistsBcryptIssuesJwtAndSetsHttpOnlyCookie() throws Exception {
                RegisterRequest request = RegisterRequest.builder()
                                .email("Alex.Mueller@EuroJob.EU")
                                .password("SecureP@ssw0rd2026!")
                                .firstName("Alex")
                                .lastName("Mueller")
                                .build();

                MvcResult mvcResult = mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                                .andExpect(jsonPath("$.user.email").value("alex.mueller@eurojob.eu"))
                                .andExpect(jsonPath("$.user.firstName").value("Alex"))
                                .andExpect(jsonPath("$.user.role").value("ROLE_USER"))
                                .andExpect(jsonPath("$.user.accountStatus").value("ACTIVE"))
                                .andExpect(jsonPath("$.user.uuid").isNotEmpty())
                                .andReturn();

                User persistedUser = userRepository.findByEmailIgnoreCase("alex.mueller@eurojob.eu").orElseThrow();
                assertThat(persistedUser.getPasswordHash()).startsWith("$2");
                assertThat(persistedUser.getPasswordHash()).isNotEqualTo("SecureP@ssw0rd2026!");
                assertThat(passwordEncoder.matches("SecureP@ssw0rd2026!", persistedUser.getPasswordHash())).isTrue();

                Cookie refreshCookie = mvcResult.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE_NAME);
                assertThat(refreshCookie).isNotNull();
                assertThat(refreshCookie.isHttpOnly()).isTrue();

                String expectedHash = TokenHashUtil.sha256Hex(refreshCookie.getValue());
                assertThat(refreshTokenRepository.findByTokenHash(expectedHash))
                                .isPresent()
                                .get()
                                .satisfies(token -> assertThat(token.isRevoked()).isFalse());
        }

        @Test
        void register_duplicateEmailCaseInsensitive_returns409Conflict() throws Exception {
                RegisterRequest first = RegisterRequest.builder()
                                .email("duplicate@eurojob.eu")
                                .password("SecureP@ssw0rd2026!")
                                .firstName("Elena")
                                .lastName("Vance")
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(first)))
                                .andExpect(status().isCreated());

                RegisterRequest second = RegisterRequest.builder()
                                .email("DUPLICATE@eurojob.eu")
                                .password("AnotherP@ssw0rd2026!")
                                .firstName("Elena")
                                .lastName("Vance")
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(second)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.status").value(409))
                                .andExpect(jsonPath("$.message").value(containsString("already exists")));
        }

        @Test
        void register_invalidFields_returns400AndRedactsPassword() throws Exception {
                RegisterRequest invalid = RegisterRequest.builder()
                                .email("not-an-email")
                                .password("weak")
                                .firstName("A")
                                .lastName("")
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalid)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("email")))
                                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("password")))
                                .andExpect(jsonPath("$.validationErrors[?(@.field=='password')].rejectedValue",
                                                hasItem("[REDACTED]")));
        }

        @Test
        void login_validAndInvalidCredentials_returnsExpectedStatuses() throws Exception {
                RegisterRequest register = RegisterRequest.builder()
                                .email("login.user@eurojob.eu")
                                .password("SecureP@ssw0rd2026!")
                                .firstName("Marcus")
                                .lastName("Weber")
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(register)))
                                .andExpect(status().isCreated());

                LoginRequest validLogin = new LoginRequest("LOGIN.USER@eurojob.eu", "SecureP@ssw0rd2026!");
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validLogin)))
                                .andExpect(status().isOk())
                                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                                .andExpect(jsonPath("$.user.email").value("login.user@eurojob.eu"))
                                .andExpect(jsonPath("$.message").value("Login successful"));

                LoginRequest wrongPassword = new LoginRequest("login.user@eurojob.eu", "WrongP@ssword1!");
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(wrongPassword)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401))
                                .andExpect(jsonPath("$.message").value("Invalid email or password"));

                LoginRequest unknownEmail = new LoginRequest("nobody@eurojob.eu", "SecureP@ssw0rd2026!");
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(unknownEmail)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401))
                                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        @Test
        void login_inactiveAccountStatuses_areRejectedWith401() throws Exception {
                userRepository.saveAndFlush(User.builder()
                                .email("suspended@eurojob.eu")
                                .passwordHash(passwordEncoder.encode("SecureP@ssw0rd2026!"))
                                .firstName("Susp")
                                .lastName("User")
                                .role(UserRole.ROLE_USER)
                                .accountStatus(AccountStatus.SUSPENDED)
                                .build());

                userRepository.saveAndFlush(User.builder()
                                .email("pending@eurojob.eu")
                                .passwordHash(passwordEncoder.encode("SecureP@ssw0rd2026!"))
                                .firstName("Pend")
                                .lastName("User")
                                .role(UserRole.ROLE_USER)
                                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                                .build());

                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new LoginRequest("suspended@eurojob.eu", "SecureP@ssw0rd2026!"))))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.message").value(containsString("suspended")));

                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new LoginRequest("pending@eurojob.eu", "SecureP@ssw0rd2026!"))))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.message").value(containsString("pending verification")));
        }

        @Test
        void logout_requiresAuthenticationAndInvalidatesRefreshTokensInDatabase() throws Exception {
                mockMvc.perform(post("/api/v1/auth/logout"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401));

                RegisterRequest register = RegisterRequest.builder()
                                .email("logout.user@eurojob.eu")
                                .password("SecureP@ssw0rd2026!")
                                .firstName("Nina")
                                .lastName("Schmidt")
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(register)))
                                .andExpect(status().isCreated());

                MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new LoginRequest("logout.user@eurojob.eu", "SecureP@ssw0rd2026!"))))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
                String accessToken = loginJson.get("accessToken").asText();

                Cookie refreshCookie = loginResult.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE_NAME);
                assertThat(refreshCookie).isNotNull();

                User user = userRepository.findByEmailIgnoreCase("logout.user@eurojob.eu").orElseThrow();
                List<RefreshToken> activeBeforeLogout = refreshTokenRepository
                                .findAllByUserIdAndRevokedFalse(user.getId());
                assertThat(activeBeforeLogout).hasSize(2);

                // Single-session logout revokes only the cookie token
                mockMvc.perform(post("/api/v1/auth/logout")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .cookie(refreshCookie))
                                .andExpect(status().isOk())
                                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                                .andExpect(jsonPath("$.success").value(true));

                RefreshToken loggedOutToken = refreshTokenRepository
                                .findByTokenHash(TokenHashUtil.sha256Hex(refreshCookie.getValue()))
                                .orElseThrow();
                assertThat(loggedOutToken.isRevoked()).isTrue();
                assertThat(loggedOutToken.getRevokedAt()).isNotNull();
                assertThat(refreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId())).hasSize(1);

                // Revoke-all logout using token in request body revokes remaining active
                // session(s)
                MvcResult secondLogin = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new LoginRequest("logout.user@eurojob.eu", "SecureP@ssw0rd2026!"))))
                                .andExpect(status().isOk())
                                .andReturn();

                String secondAccessToken = objectMapper.readTree(secondLogin.getResponse().getContentAsString())
                                .get("accessToken").asText();
                Cookie secondCookie = secondLogin.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE_NAME);
                assertThat(secondCookie).isNotNull();

                mockMvc.perform(post("/api/v1/auth/logout")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                LogoutRequest.builder()
                                                                .refreshToken(secondCookie.getValue())
                                                                .revokeAllSessions(true)
                                                                .build())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true));

                assertThat(refreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId())).isEmpty();
        }
}
