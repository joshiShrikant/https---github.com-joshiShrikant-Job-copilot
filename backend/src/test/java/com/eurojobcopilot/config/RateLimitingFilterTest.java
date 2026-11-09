package com.eurojobcopilot.config;

import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authEndpoint_blocksAboveConfiguredLimitAndResetsOnNextWindow() throws Exception {
        AtomicReference<Instant> clockInstant = new AtomicReference<>(Instant.parse("2026-11-09T10:00:05Z"));
        Clock mutableClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return clockInstant.get();
            }
        };

        RateLimitProperties props = new RateLimitProperties();
        props.setEnabled(true);
        props.setWindowSeconds(60);
        props.setAuthMaxRequests(2);
        props.setAiMaxRequests(5);
        props.setApiMaxRequests(10);

        RateLimitingFilter filter = new RateLimitingFilter(props, objectMapper, mutableClock);

        MockHttpServletResponse first = execute(filter, "POST", "/api/v1/auth/login", "10.0.0.15");
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(first.getHeader("X-RateLimit-Limit")).isEqualTo("2");
        assertThat(first.getHeader("X-RateLimit-Remaining")).isEqualTo("1");

        MockHttpServletResponse second = execute(filter, "POST", "/api/v1/auth/login", "10.0.0.15");
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(second.getHeader("X-RateLimit-Remaining")).isEqualTo("0");

        MockHttpServletResponse blocked = execute(filter, "POST", "/api/v1/auth/login", "10.0.0.15");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        JsonNode body = objectMapper.readTree(blocked.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(429);
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/auth/login");

        // Advance past window boundary -> counter resets cleanly
        clockInstant.set(Instant.parse("2026-11-09T10:01:02Z"));
        MockHttpServletResponse nextWindow = execute(filter, "POST", "/api/v1/auth/login", "10.0.0.15");
        assertThat(nextWindow.getStatus()).isEqualTo(200);
        assertThat(nextWindow.getHeader("X-RateLimit-Remaining")).isEqualTo("1");
    }

    @Test
    void aiBucketAndAuthenticatedUsers_areIsolatedAndBoundedInMemory() throws Exception {
        RateLimitProperties props = new RateLimitProperties();
        props.setEnabled(true);
        props.setWindowSeconds(60);
        props.setAuthMaxRequests(10);
        props.setAiMaxRequests(1);
        props.setApiMaxRequests(10);
        props.setMaxTrackedClients(2);

        RateLimitingFilter filter = new RateLimitingFilter(props, objectMapper, Clock.fixed(
                Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));

        setAuthenticatedUser(101L, "u1@eurojob.eu");
        assertThat(execute(filter, "POST", "/api/v1/jobs/5/intelligence/analyze-jd", "10.0.0.1").getStatus())
                .isEqualTo(200);
        assertThat(execute(filter, "POST", "/api/v1/jobs/5/intelligence/analyze-jd", "10.0.0.1").getStatus())
                .isEqualTo(429);

        // Second authenticated user behind same IP gets separate AI bucket
        setAuthenticatedUser(202L, "u2@eurojob.eu");
        assertThat(execute(filter, "POST", "/api/v1/jobs/5/intelligence/analyze-jd", "10.0.0.1").getStatus())
                .isEqualTo(200);

        // Third user triggers bounded LRU eviction so map size stays <= maxTrackedClients (2)
        setAuthenticatedUser(303L, "u3@eurojob.eu");
        assertThat(execute(filter, "POST", "/api/v1/jobs/5/intelligence/analyze-jd", "10.0.0.1").getStatus())
                .isEqualTo(200);
        assertThat(filter.trackedKeyCount()).isLessThanOrEqualTo(2);

        // Health checks are never throttled
        MockHttpServletResponse health = execute(filter, "GET", "/api/v1/health", "10.0.0.1");
        assertThat(health.getStatus()).isEqualTo(200);
        assertThat(health.getHeader("X-RateLimit-Limit")).isNull();
    }

    private MockHttpServletResponse execute(
            RateLimitingFilter filter,
            String method,
            String uri,
            String remoteAddr
    ) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr(remoteAddr);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    private void setAuthenticatedUser(Long userId, String email) {
        User user = User.builder()
                .email(email)
                .passwordHash("hash")
                .firstName("Test")
                .lastName("User")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build();
        user.setId(userId);
        AuthenticatedUserPrincipal principal = AuthenticatedUserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
