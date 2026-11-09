package com.eurojobcopilot.modules.auth.service;

import com.eurojobcopilot.modules.auth.config.JwtProperties;
import com.eurojobcopilot.modules.auth.dto.AuthResponse;
import com.eurojobcopilot.modules.auth.dto.AuthSessionResult;
import com.eurojobcopilot.modules.auth.dto.LoginRequest;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.dto.TokenRefreshResponse;
import com.eurojobcopilot.modules.auth.dto.TokenRefreshResult;
import com.eurojobcopilot.modules.auth.dto.UserSummaryDto;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.RefreshToken;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.exception.AccountInactiveException;
import com.eurojobcopilot.modules.auth.exception.EmailAlreadyExistsException;
import com.eurojobcopilot.modules.auth.exception.InvalidCredentialsException;
import com.eurojobcopilot.modules.auth.exception.InvalidRefreshTokenException;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public AuthSessionResult register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(false)
                .build();

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        String accessToken = jwtService.generateAccessToken(savedUser);
        String rawRefreshToken = issueRefreshToken(savedUser);
        log.info("Registered new user uuid={} status={}", savedUser.getUuid(), savedUser.getAccountStatus());

        AuthResponse response = AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresInSeconds(jwtProperties.getAccessTokenExpirationSeconds())
                .user(toUserSummary(savedUser))
                .message("User registered successfully")
                .build();

        return new AuthSessionResult(response, rawRefreshToken);
    }

    @Override
    @Transactional
    public AuthSessionResult login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );
        } catch (LockedException ex) {
            throw new AccountInactiveException("Account is suspended. Please contact support.");
        } catch (DisabledException ex) {
            throw new AccountInactiveException("Account is pending verification.");
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        AuthenticatedUserPrincipal principal = (AuthenticatedUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        String accessToken = jwtService.generateAccessToken(principal);
        String rawRefreshToken = issueRefreshToken(user);
        log.info("Authenticated user uuid={}", user.getUuid());

        AuthResponse response = AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresInSeconds(jwtProperties.getAccessTokenExpirationSeconds())
                .user(toUserSummary(user))
                .message("Login successful")
                .build();

        return new AuthSessionResult(response, rawRefreshToken);
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenRefreshResult refreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is missing");
        }

        String tokenHash = TokenHashUtil.sha256Hex(rawRefreshToken);
        RefreshToken existingToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));

        User user = existingToken.getUser();
        Instant now = Instant.now();

        if (existingToken.isRevoked()) {
            if (existingToken.getReplacedByToken() != null) {
                log.warn("Refresh token reuse detected for userId={}; revoking all active sessions", user.getId());
                revokeAllActiveTokens(user.getId(), now);
            }
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }

        if (existingToken.getExpiresAt().isBefore(now)) {
            existingToken.setRevoked(true);
            existingToken.setRevokedAt(now);
            refreshTokenRepository.save(existingToken);
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        validateAccountStatus(user);

        String newRawRefreshToken = TokenHashUtil.generateOpaqueToken();
        String newTokenHash = TokenHashUtil.sha256Hex(newRawRefreshToken);

        existingToken.setRevoked(true);
        existingToken.setRevokedAt(now);
        existingToken.setReplacedByToken(newTokenHash);
        refreshTokenRepository.save(existingToken);

        RefreshToken rotatedToken = RefreshToken.builder()
                .user(user)
                .tokenHash(newTokenHash)
                .expiresAt(now.plusSeconds(jwtProperties.getRefreshTokenExpirationSeconds()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(rotatedToken);

        String newAccessToken = jwtService.generateAccessToken(user);
        TokenRefreshResponse response = TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresInSeconds(jwtProperties.getAccessTokenExpirationSeconds())
                .build();

        return new TokenRefreshResult(response, newRawRefreshToken);
    }

    @Override
    @Transactional
    public void logout(AuthenticatedUserPrincipal currentUser, String rawRefreshToken, boolean revokeAllSessions) {
        Instant now = Instant.now();

        if (currentUser != null && revokeAllSessions) {
            revokeAllActiveTokens(currentUser.getId(), now);
            return;
        }

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = TokenHashUtil.sha256Hex(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                if (currentUser != null && !token.getUser().getId().equals(currentUser.getId())) {
                    log.warn("Ignored cross-user refresh token revocation attempt by userId={}", currentUser.getId());
                    return;
                }
                if (revokeAllSessions) {
                    revokeAllActiveTokens(token.getUser().getId(), now);
                } else if (!token.isRevoked()) {
                    token.setRevoked(true);
                    token.setRevokedAt(now);
                    refreshTokenRepository.save(token);
                    log.info("Revoked refresh token session for userId={}", token.getUser().getId());
                }
            });
            return;
        }

        if (currentUser != null) {
            revokeAllActiveTokens(currentUser.getId(), now);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummaryDto getCurrentUser(AuthenticatedUserPrincipal principal) {
        if (principal == null) {
            throw new InvalidCredentialsException("User is not authenticated");
        }
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new InvalidCredentialsException("Authenticated user no longer exists"));
        return toUserSummary(user);
    }

    private void revokeAllActiveTokens(Long userId, Instant now) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedFalse(userId);
        if (activeTokens.isEmpty()) {
            return;
        }
        activeTokens.forEach(active -> {
            active.setRevoked(true);
            active.setRevokedAt(now);
        });
        refreshTokenRepository.saveAll(activeTokens);
        log.info("Revoked {} active sessions for userId={}", activeTokens.size(), userId);
    }

    private void validateAccountStatus(User user) {
        AccountStatus status = user.getAccountStatus();
        if (status == AccountStatus.ACTIVE) {
            return;
        }
        if (status == AccountStatus.SUSPENDED) {
            throw new AccountInactiveException("Account is suspended. Please contact support.");
        }
        throw new AccountInactiveException("Account is pending verification.");
    }

    private String issueRefreshToken(User user) {
        String rawToken = TokenHashUtil.generateOpaqueToken();
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpirationSeconds());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UserSummaryDto toUserSummary(User user) {
        return UserSummaryDto.builder()
                .uuid(user.getUuid())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .accountStatus(user.getAccountStatus().name())
                .emailVerified(user.isEmailVerified())
                .build();
    }
}
