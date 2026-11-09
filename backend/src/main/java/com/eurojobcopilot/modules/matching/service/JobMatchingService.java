package com.eurojobcopilot.modules.matching.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobMatchBreakdownDto;
import com.eurojobcopilot.modules.matching.dto.BatchMatchRecalculationResponse;

public interface JobMatchingService {

    JobMatchBreakdownDto evaluateAndPersistJobMatch(AuthenticatedUserPrincipal currentUser, Long jobId);

    BatchMatchRecalculationResponse recalculateActiveJobs(AuthenticatedUserPrincipal currentUser);

    BatchMatchRecalculationResponse recalculateActiveJobsForUser(Long userId);

    int ensureActiveMatchesUpToDate(Long userId);
}

