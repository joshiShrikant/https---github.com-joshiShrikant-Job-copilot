package com.eurojobcopilot.config;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Bounded-memory per-client HTTP rate limiter protecting authentication, AI generation,
 * and general REST endpoints from brute-force and resource exhaustion abuse.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER_LIMIT = "X-RateLimit-Limit";
    private static final String AUTH_HEADER_REMAINING = "X-RateLimit-Remaining";
    private static final String AUTH_HEADER_RESET = "X-RateLimit-Reset";
    private static final String HEADER_RETRY_AFTER = "Retry-After";

    private static final Pattern AI_POST_ENDPOINT_PATTERN = Pattern.compile(
            "^/api/v1/(?:"
                    + "resumes/[^/]+/parse"
                    + "|tailored-documents/(?:resume|cover-letter)/generate"
                    + "|jobs/\\d+/intelligence/(?:analyze-jd|ai-match)"
                    + "|jobs/\\d+/assistant/respond"
                    + "|recruiters/\\d+/outreach/generate"
                    + "|interview-prep/sessions(?:/\\d+/answers)?"
                    + "|autopilot/run"
                    + ")/?$"
    );

    private enum BucketType {
        AUTH,
        AI,
        API
    }

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, WindowCounter> counters;

    @Autowired
    public RateLimitingFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    RateLimitingFilter(RateLimitProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        int capacity = Math.max(1, properties.getMaxTrackedClients());
        this.counters = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, WindowCounter> eldest) {
                return size() > capacity;
            }
        });
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = normalizePath(request);
        if (!path.startsWith("/api/v1/")) {
            return true;
        }
        return "/api/v1/health".equals(path);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String path = normalizePath(request);
        BucketType bucket = classifyBucket(request.getMethod(), path);
        int maxRequests = resolveLimit(bucket);
        if (maxRequests <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        long windowSeconds = Math.max(1L, properties.getWindowSeconds());
        String clientKey = bucket.name() + ":" + resolveClientKey(request, bucket);

        EvaluationResult result = evaluateAndIncrement(clientKey, maxRequests, windowSeconds);
        response.setHeader(AUTH_HEADER_LIMIT, String.valueOf(result.limit()));
        response.setHeader(AUTH_HEADER_REMAINING, String.valueOf(result.remaining()));
        response.setHeader(AUTH_HEADER_RESET, String.valueOf(result.resetEpochSeconds()));

        if (!result.allowed()) {
            response.setHeader(HEADER_RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiErrorResponse errorPayload = ApiErrorResponse.builder()
                    .timestamp(clock.instant())
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                    .message("Rate limit exceeded for this endpoint. Please retry later.")
                    .path(path)
                    .build();

            objectMapper.writeValue(response.getWriter(), errorPayload);
            return;
        }

        filterChain.doFilter(request, response);
    }

    public void reset() {
        synchronized (counters) {
            counters.clear();
        }
    }

    int trackedKeyCount() {
        synchronized (counters) {
            return counters.size();
        }
    }

    private EvaluationResult evaluateAndIncrement(String clientKey, int maxRequests, long windowSeconds) {
        long nowSeconds = clock.instant().getEpochSecond();
        long currentWindowStart = nowSeconds - (nowSeconds % windowSeconds);
        long resetEpochSeconds = currentWindowStart + windowSeconds;
        long retryAfterSeconds = Math.max(1L, resetEpochSeconds - nowSeconds);

        synchronized (counters) {
            WindowCounter counter = counters.get(clientKey);
            if (counter == null || counter.windowStartEpochSeconds != currentWindowStart) {
                counter = new WindowCounter(currentWindowStart, 0);
                counters.put(clientKey, counter);
            }

            if (counter.count >= maxRequests) {
                return new EvaluationResult(false, maxRequests, 0, resetEpochSeconds, retryAfterSeconds);
            }

            counter.count++;
            int remaining = Math.max(0, maxRequests - counter.count);
            return new EvaluationResult(true, maxRequests, remaining, resetEpochSeconds, retryAfterSeconds);
        }
    }

    private BucketType classifyBucket(String method, String path) {
        if (HttpMethod.POST.matches(method)) {
            if ("/api/v1/auth/login".equals(path)
                    || "/api/v1/auth/register".equals(path)
                    || "/api/v1/auth/refresh".equals(path)) {
                return BucketType.AUTH;
            }
            if (AI_POST_ENDPOINT_PATTERN.matcher(path).matches()) {
                return BucketType.AI;
            }
        }
        return BucketType.API;
    }

    private int resolveLimit(BucketType bucket) {
        return switch (bucket) {
            case AUTH -> properties.getAuthMaxRequests();
            case AI -> properties.getAiMaxRequests();
            case API -> properties.getApiMaxRequests();
        };
    }

    private String resolveClientKey(HttpServletRequest request, BucketType bucket) {
        if (bucket != BucketType.AUTH) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                    && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal
                    && principal.getId() != null) {
                return "user:" + principal.getId();
            }
        }
        return "ip:" + resolveClientIp(request);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.isTrustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String candidate = forwarded.split(",")[0].trim();
                if (!candidate.isEmpty() && candidate.length() <= 64) {
                    return candidate;
                }
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank() && realIp.trim().length() <= 64) {
                return realIp.trim();
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "unknown";
    }

    private String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            return "/";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri.isEmpty() ? "/" : uri;
    }

    private static final class WindowCounter {
        private final long windowStartEpochSeconds;
        private int count;

        private WindowCounter(long windowStartEpochSeconds, int count) {
            this.windowStartEpochSeconds = windowStartEpochSeconds;
            this.count = count;
        }
    }

    private record EvaluationResult(
            boolean allowed,
            int limit,
            int remaining,
            long resetEpochSeconds,
            long retryAfterSeconds
    ) {
    }
}

