package com.eurojobcopilot.modules.auth.service;

import com.eurojobcopilot.modules.auth.config.JwtProperties;
import com.eurojobcopilot.modules.auth.dto.AuthSessionResult;
import com.eurojobcopilot.modules.auth.dto.LoginRequest;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private JwtProperties jwtProperties;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setAccessTokenExpirationSeconds(900);
        jwtProperties.setRefreshTokenExpirationSeconds(604800);
        authService = new AuthServiceImpl(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                authenticationManager,
                jwtService,
                jwtProperties
        );
    }

    @Test
    void register_validRequest_normalizesEmailHashesPasswordIssuesJwtAndPersistsHashedToken() {
        RegisterRequest request = RegisterRequest.builder()
                .email("  Alex.Mueller@EuroJob.EU ")
                .password("SecureP@ssw0rd2026!")
                .firstName(" Alex ")
                .lastName(" Mueller ")
                .build();

        when(userRepository.existsByEmailIgnoreCase("alex.mueller@eurojob.eu")).thenReturn(false);
        when(passwordEncoder.encode("SecureP@ssw0rd2026!")).thenReturn("$2a$10$encodedBcryptHash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            u.setUuid(UUID.randomUUID().toString());
            return u;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("signed.jwt.access");

        AuthSessionResult result = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("alex.mueller@eurojob.eu");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("$2a$10$encodedBcryptHash");
        assertThat(userCaptor.getValue().getFirstName()).isEqualTo("Alex");
        assertThat(userCaptor.getValue().getLastName()).isEqualTo("Mueller");

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenHash())
                .isEqualTo(TokenHashUtil.sha256Hex(result.getRawRefreshToken()));
        assertThat(tokenCaptor.getValue().isRevoked()).isFalse();

        assertThat(result.getResponse().getAccessToken()).isEqualTo("signed.jwt.access");
        assertThat(result.getResponse().getTokenType()).isEqualTo("Bearer");
        assertThat(result.getResponse().getExpiresInSeconds()).isEqualTo(900L);
        assertThat(result.getResponse().getUser().getEmail()).isEqualTo("alex.mueller@eurojob.eu");
        assertThat(result.getResponse().getUser().getRole()).isEqualTo(UserRole.ROLE_USER.name());
    }

    @Test
    void register_duplicateEmail_throwsEmailAlreadyExistsException() {
        RegisterRequest request = RegisterRequest.builder()
                .email("taken@eurojob.eu")
                .password("SecureP@ssw0rd2026!")
                .firstName("Alex")
                .lastName("Mueller")
                .build();

        when(userRepository.existsByEmailIgnoreCase("taken@eurojob.eu")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void login_validCredentials_authenticatesViaManagerAndReturnsJwtSession() {
        User user = buildActiveUser(1L, "uuid-1", "alex@eurojob.eu", UserRole.ROLE_USER);
        AuthenticatedUserPrincipal principal = AuthenticatedUserPrincipal.from(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(principal)).thenReturn("login.jwt.token");

        AuthSessionResult result = authService.login(new LoginRequest("  ALEX@eurojob.eu ", "SecureP@ssw0rd2026!"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> authCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(authCaptor.capture());
        assertThat(authCaptor.getValue().getName()).isEqualTo("alex@eurojob.eu");

        verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertThat(result.getResponse().getAccessToken()).isEqualTo("login.jwt.token");
        assertThat(result.getResponse().getTokenType()).isEqualTo("Bearer");
        assertThat(result.getResponse().getExpiresInSeconds()).isEqualTo(900L);
        assertThat(result.getResponse().getUser().getUuid()).isEqualTo("uuid-1");
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsException() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = new LoginRequest("alex@eurojob.eu", "WrongP@ss1!");
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_suspendedAccount_throwsAccountInactiveException() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new LockedException("User account is locked"));

        LoginRequest request = new LoginRequest("suspended@eurojob.eu", "SecureP@ssw0rd2026!");
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void login_pendingVerificationAccount_throwsAccountInactiveException() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("User is disabled"));

        LoginRequest request = new LoginRequest("pending@eurojob.eu", "SecureP@ssw0rd2026!");
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessageContaining("pending verification");
    }

    @Test
    void refreshToken_validToken_rotatesTokenAndIssuesNewAccessToken() {
        User user = buildActiveUser(7L, "uuid-7", "alex@eurojob.eu", UserRole.ROLE_USER);
        String oldRawToken = "old-refresh-token";
        String oldHash = TokenHashUtil.sha256Hex(oldRawToken);

        RefreshToken existingToken = RefreshToken.builder()
                .id(100L)
                .user(user)
                .tokenHash(oldHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(oldHash)).thenReturn(Optional.of(existingToken));
        when(jwtService.generateAccessToken(user)).thenReturn("rotated.jwt.access");

        TokenRefreshResult result = authService.refreshToken(oldRawToken);

        assertThat(result.getResponse().getAccessToken()).isEqualTo("rotated.jwt.access");
        assertThat(result.getResponse().getTokenType()).isEqualTo("Bearer");
        assertThat(result.getResponse().getExpiresInSeconds()).isEqualTo(900L);
        assertThat(result.getRawRefreshToken()).isNotBlank().isNotEqualTo(oldRawToken);

        String expectedNewHash = TokenHashUtil.sha256Hex(result.getRawRefreshToken());
        assertThat(existingToken.isRevoked()).isTrue();
        assertThat(existingToken.getRevokedAt()).isNotNull();
        assertThat(existingToken.getReplacedByToken()).isEqualTo(expectedNewHash);

        ArgumentCaptor<RefreshToken> savedTokens = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(savedTokens.capture());
        RefreshToken newPersisted = savedTokens.getAllValues().get(1);
        assertThat(newPersisted.getTokenHash()).isEqualTo(expectedNewHash);
        assertThat(newPersisted.isRevoked()).isFalse();
        assertThat(newPersisted.getUser()).isSameAs(user);
    }

    @Test
    void refreshToken_reusedRotatedToken_revokesAllUserSessionsAndThrows401() {
        User user = buildActiveUser(9L, "uuid-9", "victim@eurojob.eu", UserRole.ROLE_USER);
        String replayedRaw = "replayed-token";
        String replayedHash = TokenHashUtil.sha256Hex(replayedRaw);

        RefreshToken compromisedToken = RefreshToken.builder()
                .id(200L)
                .user(user)
                .tokenHash(replayedHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true)
                .revokedAt(Instant.now().minusSeconds(60))
                .replacedByToken("child-hash")
                .build();

        RefreshToken activeSibling = RefreshToken.builder()
                .id(201L)
                .user(user)
                .tokenHash("child-hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(replayedHash)).thenReturn(Optional.of(compromisedToken));
        when(refreshTokenRepository.findAllByUserIdAndRevokedFalse(9L)).thenReturn(List.of(activeSibling));

        assertThatThrownBy(() -> authService.refreshToken(replayedRaw))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("revoked");

        assertThat(activeSibling.isRevoked()).isTrue();
        assertThat(activeSibling.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).saveAll(List.of(activeSibling));
    }

    @Test
    void refreshToken_expiredToken_marksRevokedAndThrowsInvalidRefreshTokenException() {
        User user = buildActiveUser(10L, "uuid-10", "expired@eurojob.eu", UserRole.ROLE_USER);
        String raw = "expired-token";
        String hash = TokenHashUtil.sha256Hex(raw);

        RefreshToken expired = RefreshToken.builder()
                .id(300L)
                .user(user)
                .tokenHash(hash)
                .expiresAt(Instant.now().minusSeconds(30))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refreshToken(raw))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("expired");

        assertThat(expired.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(expired);
    }

    @Test
    void refreshToken_missingOrBlank_throwsInvalidRefreshTokenException() {
        assertThatThrownBy(() -> authService.refreshToken("  "))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void logout_revokeAllSessions_revokesEveryActiveTokenForCurrentUser() {
        User user = buildActiveUser(42L, "uuid-42", "user@eurojob.eu", UserRole.ROLE_USER);
        AuthenticatedUserPrincipal principal = AuthenticatedUserPrincipal.from(user);
        String rawToken = "opaque-session-token";
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);

        RefreshToken token1 = RefreshToken.builder()
                .id(10L)
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        RefreshToken token2 = RefreshToken.builder()
                .id(11L)
                .user(user)
                .tokenHash("other-hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findAllByUserIdAndRevokedFalse(42L)).thenReturn(List.of(token1, token2));

        authService.logout(principal, rawToken, true);

        verify(refreshTokenRepository).saveAll(List.of(token1, token2));
        assertThat(token1.isRevoked()).isTrue();
        assertThat(token2.isRevoked()).isTrue();
        assertThat(token1.getRevokedAt()).isNotNull();
    }

    @Test
    void logout_crossUserToken_ignoresRevocationOfOtherUsersToken() {
        User currentUser = buildActiveUser(1L, "uuid-1", "me@eurojob.eu", UserRole.ROLE_USER);
        User otherUser = buildActiveUser(2L, "uuid-2", "other@eurojob.eu", UserRole.ROLE_USER);
        AuthenticatedUserPrincipal principal = AuthenticatedUserPrincipal.from(currentUser);

        String otherRawToken = "other-user-token";
        String otherHash = TokenHashUtil.sha256Hex(otherRawToken);
        RefreshToken otherToken = RefreshToken.builder()
                .id(50L)
                .user(otherUser)
                .tokenHash(otherHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(otherHash)).thenReturn(Optional.of(otherToken));

        authService.logout(principal, otherRawToken, false);

        assertThat(otherToken.isRevoked()).isFalse();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void getCurrentUser_authenticatedPrincipal_returnsUserSummary() {
        User user = buildActiveUser(5L, "uuid-5", "profile@eurojob.eu", UserRole.ROLE_ADMIN);
        AuthenticatedUserPrincipal principal = AuthenticatedUserPrincipal.from(user);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        UserSummaryDto summary = authService.getCurrentUser(principal);

        assertThat(summary.getUuid()).isEqualTo("uuid-5");
        assertThat(summary.getEmail()).isEqualTo("profile@eurojob.eu");
        assertThat(summary.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(summary.getAccountStatus()).isEqualTo("ACTIVE");
    }

    private User buildActiveUser(Long id, String uuid, String email, UserRole role) {
        return User.builder()
                .id(id)
                .uuid(uuid)
                .email(email)
                .passwordHash("$2a$10$encoded")
                .firstName("Test")
                .lastName("User")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(false)
                .build();
    }
}
