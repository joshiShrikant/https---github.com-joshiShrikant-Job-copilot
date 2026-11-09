package com.eurojobcopilot.config;

import com.eurojobcopilot.modules.auth.dto.LoginRequest;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.window-seconds=60",
        "app.rate-limit.auth-max-requests=3",
        "app.rate-limit.ai-max-requests=2",
        "app.rate-limit.api-max-requests=4"
})
class RateLimitingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.reset();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void authAndApiRateLimits_enforce429WithStandardHeadersWhileHealthRemainsUnthrottled() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("ratelimit@eurojob.eu")
                .password("SecureP@ssw0rd2026!")
                .firstName("Rate")
                .lastName("Limiter")
                .build();

        // 1/3 auth requests (register)
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "2"))
                .andReturn();

        JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String accessToken = registerJson.get("accessToken").asText();

        // 2/3 & 3/3 auth requests (login attempts)
        LoginRequest login = new LoginRequest("ratelimit@eurojob.eu", "SecureP@ssw0rd2026!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "1"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));

        // 4th auth request within window returns 429 JSON error payload
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", notNullValue()))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));

        // Separate API bucket remains available for authenticated user up to api-max-requests=4
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "4"));
        }

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));

        // Health endpoint is exempt from throttling even when buckets are saturated
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-RateLimit-Limit"));
    }
}

