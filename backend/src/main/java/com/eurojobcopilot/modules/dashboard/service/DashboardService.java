package com.eurojobcopilot.modules.dashboard.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.dashboard.dto.DashboardSummaryResponse;

public interface DashboardService {

    DashboardSummaryResponse getSummary(AuthenticatedUserPrincipal currentUser);
}

