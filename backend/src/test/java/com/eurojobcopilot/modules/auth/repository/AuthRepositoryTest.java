package com.eurojobcopilot.modules.auth.repository;

import com.eurojobcopilot.config.JpaAuditingConfig;
import com.eurojobcopilot.modules.auth.entity.PasswordResetToken;
import com.eurojobcopilot.modules.auth.entity.RefreshToken;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.support.TestEntityFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class AuthRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistUserWithGeneratedUuidAuditingAndUniqueEmail() {
        User saved = userRepository.saveAndFlush(TestEntityFactory.createUser("candidate@eurojob.eu"));
        entityManager.clear();

        User found = userRepository.findByEmailIgnoreCase("CANDIDATE@EUROJOB.EU").orElseThrow();
        assertThat(found.getId()).isPositive();
        assertThat(found.getUuid()).hasSize(36);
        assertThat(found.getVersion()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(userRepository.existsByEmailIgnoreCase("candidate@eurojob.eu")).isTrue();
        assertThat(userRepository.findByUuid(saved.getUuid())).isPresent();

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(TestEntityFactory.createUser("candidate@eurojob.eu"))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldPersistAndQueryRefreshAndPasswordResetTokens() {
        User user = userRepository.saveAndFlush(TestEntityFactory.createUser("tokens@eurojob.eu"));

        RefreshToken activeToken = RefreshToken.builder()
                .user(user)
                .tokenHash("sha256-active-refresh-token")
                .expiresAt(Instant.now().plusSeconds(86400))
                .revoked(false)
                .build();

        RefreshToken revokedToken = RefreshToken.builder()
                .user(user)
                .tokenHash("sha256-revoked-refresh-token")
                .expiresAt(Instant.now().plusSeconds(86400))
                .revoked(true)
                .revokedAt(Instant.now())
                .build();

        refreshTokenRepository.saveAll(List.of(activeToken, revokedToken));

        PasswordResetToken expiredUnused = PasswordResetToken.builder()
                .user(user)
                .tokenHash("sha256-expired-reset-token")
                .expiresAt(Instant.now().minusSeconds(600))
                .used(false)
                .build();

        PasswordResetToken validReset = PasswordResetToken.builder()
                .user(user)
                .tokenHash("sha256-valid-reset-token")
                .expiresAt(Instant.now().plusSeconds(1800))
                .used(false)
                .build();

        passwordResetTokenRepository.saveAll(List.of(expiredUnused, validReset));
        entityManager.flush();
        entityManager.clear();

        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId());
        assertThat(activeTokens).hasSize(1);
        assertThat(activeTokens.getFirst().getTokenHash()).isEqualTo("sha256-active-refresh-token");
        assertThat(refreshTokenRepository.findByTokenHash("sha256-revoked-refresh-token")).isPresent();

        long deleted = passwordResetTokenRepository.deleteByUserIdAndUsedFalseAndExpiresAtBefore(user.getId(), Instant.now());
        assertThat(deleted).isEqualTo(1L);
        assertThat(passwordResetTokenRepository.findByTokenHash("sha256-valid-reset-token")).isPresent();
    }
}

