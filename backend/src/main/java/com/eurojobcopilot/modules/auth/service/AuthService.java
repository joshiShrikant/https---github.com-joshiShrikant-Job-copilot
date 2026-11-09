package com.eurojobcopilot.modules.auth.service;

import com.eurojobcopilot.modules.auth.dto.AuthSessionResult;
import com.eurojobcopilot.modules.auth.dto.LoginRequest;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.dto.TokenRefreshResult;
import com.eurojobcopilot.modules.auth.dto.UserSummaryDto;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;

public interface AuthService {

    AuthSessionResult register(RegisterRequest request);

    AuthSessionResult login(LoginRequest request);

    TokenRefreshResult refreshToken(String rawRefreshToken);

    void logout(AuthenticatedUserPrincipal currentUser, String rawRefreshToken, boolean revokeAllSessions);

    UserSummaryDto getCurrentUser(AuthenticatedUserPrincipal principal);
}
