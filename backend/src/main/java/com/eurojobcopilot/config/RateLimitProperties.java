package com.eurojobcopilot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable per-client HTTP rate limiting settings for auth, AI generation, and API routes.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private int windowSeconds = 60;

    private int authMaxRequests = 20;

    private int aiMaxRequests = 30;

    private int apiMaxRequests = 180;

    private int maxTrackedClients = 10000;

    private boolean trustForwardedHeaders = false;
}

