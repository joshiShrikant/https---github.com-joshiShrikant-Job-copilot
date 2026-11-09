package com.eurojobcopilot.modules.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /**
     * Refresh/session token persistence lifetime in days.
     */
    private long refreshTokenExpirationDays = 7;

    /**
     * Enables the Secure attribute on HTTP auth cookies (enabled in production HTTPS environments).
     */
    private boolean cookieSecure = false;
}

