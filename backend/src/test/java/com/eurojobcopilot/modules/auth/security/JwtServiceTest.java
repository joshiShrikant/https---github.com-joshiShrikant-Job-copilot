package com.eurojobcopilot.modules.auth.security;

import com.eurojobcopilot.modules.auth.config.JwtProperties;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String TEST_SECRET = "0123456789ABCDEF0123456789ABCDEF";

    @Test
    void generateAndValidateAccessToken_containsExpectedClaims() {
        Instant fixedNow = Instant.parse("2026-06-01T10:00:00Z");
        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

        JwtProperties properties = defaultProperties(TEST_SECRET);
        MockEnvironment env = new MockEnvironment().withProperty("dummy", "1");
        env.setActiveProfiles("test");

        JwtService jwtService = new JwtService(properties, env, fixedClock);
        jwtService.initSigningKey();

        User user = User.builder()
                .id(1L)
                .uuid("user-uuid-123")
                .email("alex@eurojob.eu")
                .passwordHash("hash")
                .firstName("Alex")
                .lastName("Mueller")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        String token = jwtService.generateAccessToken(user);

        Claims claims = jwtService.parseAndValidateAccessToken(token);
        assertThat(claims.getSubject()).isEqualTo("user-uuid-123");
        assertThat(claims.get("email", String.class)).isEqualTo("alex@eurojob.eu");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_USER");
        assertThat(claims.getIssuer()).isEqualTo("eurojob-copilot");
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(fixedNow);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(fixedNow.plusSeconds(900));
        assertThat(jwtService.extractUserUuid(token)).isEqualTo("user-uuid-123");
        assertThat(jwtService.isAccessTokenValid(token)).isTrue();
    }

    @Test
    void parseAndValidateAccessToken_expiredToken_throwsExpiredJwtException() {
        AtomicReference<Instant> currentInstant = new AtomicReference<>(Instant.parse("2026-06-01T10:00:00Z"));
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
                return currentInstant.get();
            }
        };

        JwtProperties properties = defaultProperties(TEST_SECRET);
        properties.setAccessTokenExpirationSeconds(60);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        JwtService jwtService = new JwtService(properties, env, mutableClock);
        jwtService.initSigningKey();

        User user = User.builder()
                .id(2L)
                .uuid("uuid-exp")
                .email("exp@eurojob.eu")
                .passwordHash("hash")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.isAccessTokenValid(token)).isTrue();

        currentInstant.set(Instant.parse("2026-06-01T10:05:00Z"));

        assertThat(jwtService.isAccessTokenValid(token)).isFalse();
        assertThatThrownBy(() -> jwtService.parseAndValidateAccessToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseAndValidateAccessToken_tamperedSignature_throwsSignatureException() {
        JwtProperties properties = defaultProperties(TEST_SECRET);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        JwtService jwtService = new JwtService(properties, env, Clock.systemUTC());
        jwtService.initSigningKey();

        User user = User.builder()
                .id(3L)
                .uuid("uuid-sig")
                .email("sig@eurojob.eu")
                .passwordHash("hash")
                .role(UserRole.ROLE_ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        String token = jwtService.generateAccessToken(user);
        String[] parts = token.split("\\.");
        char firstChar = parts[2].charAt(0);
        String tamperedSig = (firstChar == 'A' ? 'B' : 'A') + parts[2].substring(1);
        String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSig;

        assertThat(jwtService.isAccessTokenValid(tamperedToken)).isFalse();
        assertThatThrownBy(() -> jwtService.parseAndValidateAccessToken(tamperedToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void parseAndValidateAccessToken_wrongIssuer_throwsIncorrectClaimException() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        JwtProperties issuerA = defaultProperties(TEST_SECRET);
        issuerA.setIssuer("other-issuer");
        JwtService serviceA = new JwtService(issuerA, env, Clock.systemUTC());
        serviceA.initSigningKey();

        JwtProperties issuerB = defaultProperties(TEST_SECRET);
        issuerB.setIssuer("eurojob-copilot");
        JwtService serviceB = new JwtService(issuerB, env, Clock.systemUTC());
        serviceB.initSigningKey();

        User user = User.builder()
                .id(4L)
                .uuid("uuid-iss")
                .email("iss@eurojob.eu")
                .passwordHash("hash")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        String tokenFromOtherIssuer = serviceA.generateAccessToken(user);

        assertThat(serviceB.isAccessTokenValid(tokenFromOtherIssuer)).isFalse();
        assertThatThrownBy(() -> serviceB.parseAndValidateAccessToken(tokenFromOtherIssuer))
                .isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void initSigningKey_enforcesSecretStrengthAndProfileRules() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        JwtService blankProd = new JwtService(defaultProperties(""), prodEnv, Clock.systemUTC());
        assertThatThrownBy(blankProd::initSigningKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be explicitly configured");

        JwtService placeholderProd = new JwtService(
                defaultProperties("replace-with-a-very-long-256-bit-placeholder-secret"), prodEnv, Clock.systemUTC());
        assertThatThrownBy(placeholderProd::initSigningKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");

        MockEnvironment localEnv = new MockEnvironment();
        localEnv.setActiveProfiles("local");

        JwtService shortSecret = new JwtService(defaultProperties("too-short"), localEnv, Clock.systemUTC());
        assertThatThrownBy(shortSecret::initSigningKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");

        JwtService ephemeralLocal = new JwtService(defaultProperties(null), localEnv, Clock.systemUTC());
        ephemeralLocal.initSigningKey();

        String base64Secret = "base64:" + Base64.getEncoder().encodeToString(
                "0123456789ABCDEF0123456789ABCDEF".getBytes(StandardCharsets.UTF_8));
        JwtService validBase64Prod = new JwtService(defaultProperties(base64Secret), prodEnv, Clock.systemUTC());
        validBase64Prod.initSigningKey();

        User user = User.builder()
                .id(5L)
                .uuid("uuid-b64")
                .email("b64@eurojob.eu")
                .passwordHash("hash")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        assertThat(validBase64Prod.isAccessTokenValid(validBase64Prod.generateAccessToken(user))).isTrue();
    }

    private JwtProperties defaultProperties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setAccessTokenExpirationSeconds(900);
        properties.setRefreshTokenExpirationSeconds(604800);
        properties.setIssuer("eurojob-copilot");
        return properties;
    }
}

