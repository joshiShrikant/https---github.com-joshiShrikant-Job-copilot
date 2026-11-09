package com.eurojobcopilot.modules.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * Signing secret (UTF-8 >= 32 bytes or "base64:" prefixed encoded 256-bit key).
     * Never hardcode in version control; supplied via JWT_SECRET environment variable.
     */
    private String secret;

    /**
     * Access token lifetime in seconds (default 15 minutes).
     */
    private long accessTokenExpirationSeconds = 900;

    /**
     * Refresh token lifetime in seconds (default 7 days).
     */
    private long refreshTokenExpirationSeconds = 604800;

    /**
     * Expected JWT issuer claim.
     */
    private String issuer = "eurojob-copilot";
}

