package com.eurojobcopilot.modules.matching.ai.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobMatchBreakdownDto;

/**
 * Orchestrates AI-assisted semantic job compatibility comparison while preserving
 * deterministic rule-based scores separately.
 */
public interface SemanticJobMatchingService {

    JobMatchBreakdownDto evaluateSemanticMatch(
            AuthenticatedUserPrincipal currentUser,
            Long jobId,
            Boolean forceRefresh
    );
}

