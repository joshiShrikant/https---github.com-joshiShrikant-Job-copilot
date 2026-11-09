package com.eurojobcopilot.modules.analytics.service;

import com.eurojobcopilot.modules.analytics.dto.MarketInsightsResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;

import java.util.List;

public interface MarketAnalyticsService {

    MarketInsightsResponse getMarketInsights(
            AuthenticatedUserPrincipal currentUser,
            List<String> countries,
            int trendWindowDays
    );
}

