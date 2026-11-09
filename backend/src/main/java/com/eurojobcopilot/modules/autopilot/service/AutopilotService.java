package com.eurojobcopilot.modules.autopilot.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.autopilot.dto.AutopilotRunLogDto;
import com.eurojobcopilot.modules.autopilot.dto.AutopilotStatusResponse;
import com.eurojobcopilot.modules.autopilot.dto.PagedAutopilotHistoryResponse;
import com.eurojobcopilot.modules.autopilot.dto.PagedRecommendedJobsResponse;
import com.eurojobcopilot.modules.autopilot.dto.RunAutopilotRequest;

public interface AutopilotService {

    AutopilotStatusResponse getStatus(AuthenticatedUserPrincipal currentUser);

    AutopilotRunLogDto runManualScan(AuthenticatedUserPrincipal currentUser, RunAutopilotRequest request);

    AutopilotRunLogDto executeScheduledScan(AuthenticatedUserPrincipal currentUser);

    AutopilotStatusResponse pauseAutopilot(AuthenticatedUserPrincipal currentUser);

    AutopilotStatusResponse resumeAutopilot(AuthenticatedUserPrincipal currentUser);

    PagedRecommendedJobsResponse getRecommendedJobs(
            AuthenticatedUserPrincipal currentUser,
            Integer minMatchScore,
            boolean verifiedSponsorsOnly,
            boolean excludeApplied,
            int page,
            int size
    );

    PagedAutopilotHistoryResponse getRunHistory(
            AuthenticatedUserPrincipal currentUser,
            int page,
            int size
    );
}

