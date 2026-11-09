package com.eurojobcopilot.modules.auth.repository;

import com.eurojobcopilot.modules.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    long deleteByUserIdAndUsedFalseAndExpiresAtBefore(Long userId, Instant cutoff);
}

