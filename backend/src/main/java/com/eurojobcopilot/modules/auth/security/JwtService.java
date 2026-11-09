package com.eurojobcopilot.modules.auth.security;

import com.eurojobcopilot.modules.auth.config.JwtProperties;
import com.eurojobcopilot.modules.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Service
public class JwtService {

    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final String BASE64_PREFIX = "base64:";

    private final JwtProperties jwtProperties;
    private final Environment environment;
    private final Clock clock;

    private SecretKey signingKey;

    @Autowired
    public JwtService(JwtProperties jwtProperties, Environment environment) {
        this(jwtProperties, environment, Clock.systemUTC());
    }

    JwtService(JwtProperties jwtProperties, Environment environment, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.environment = environment;
        this.clock = clock;
    }

    @PostConstruct
    public void initSigningKey() {
        String rawSecret = jwtProperties.getSecret();
        boolean allowsEphemeral = environment.acceptsProfiles(Profiles.of("local", "test"));

        if (rawSecret == null || rawSecret.isBlank()) {
            if (!allowsEphemeral) {
                throw new IllegalStateException(
                        "JWT_SECRET must be explicitly configured with at least 256 bits (32 bytes) for active profiles"
                );
            }
            this.signingKey = Jwts.SIG.HS256.key().build();
            log.warn("JWT_SECRET is unset; generated ephemeral HS256 signing key for local/test execution");
            return;
        }

        if (environment.acceptsProfiles(Profiles.of("prod")) && rawSecret.startsWith("replace-with")) {
            throw new IllegalStateException("Default placeholder JWT_SECRET cannot be used in production");
        }

        byte[] keyBytes = rawSecret.startsWith(BASE64_PREFIX)
                ? Decoders.BASE64.decode(rawSecret.substring(BASE64_PREFIX.length()))
                : rawSecret.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes (256 bits)");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        return buildAccessToken(user.getUuid(), user.getEmail(), user.getRole().name());
    }

    public String generateAccessToken(AuthenticatedUserPrincipal principal) {
        return buildAccessToken(principal.getUuid(), principal.getEmail(), principal.getRole().name());
    }

    private String buildAccessToken(String uuid, String email, String role) {
        Instant now = clock.instant();
        Instant expiry = now.plusSeconds(jwtProperties.getAccessTokenExpirationSeconds());

        return Jwts.builder()
                .subject(uuid)
                .claim("email", email)
                .claim("role", role)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseAndValidateAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(jwtProperties.getIssuer())
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessTokenValid(String token) {
        try {
            parseAndValidateAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String extractUserUuid(String token) {
        return parseAndValidateAccessToken(token).getSubject();
    }
}
