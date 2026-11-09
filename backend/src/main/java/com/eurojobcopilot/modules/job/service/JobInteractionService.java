package com.eurojobcopilot.modules.job.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.ApplyRedirectRequest;
import com.eurojobcopilot.modules.job.dto.ApplyRedirectResponse;
import com.eurojobcopilot.modules.job.dto.JobInteractionResponse;

public interface JobInteractionService {

    JobInteractionResponse saveJob(AuthenticatedUserPrincipal currentUser, Long jobId);

    JobInteractionResponse unsaveJob(AuthenticatedUserPrincipal currentUser, Long jobId);

    JobInteractionResponse markInterested(AuthenticatedUserPrincipal currentUser, Long jobId);

    JobInteractionResponse clearInterested(AuthenticatedUserPrincipal currentUser, Long jobId);

    JobInteractionResponse hideJob(AuthenticatedUserPrincipal currentUser, Long jobId);

    JobInteractionResponse unhideJob(AuthenticatedUserPrincipal currentUser, Long jobId);

    ApplyRedirectResponse trackApplyRedirect(
            AuthenticatedUserPrincipal currentUser,
            Long jobId,
            ApplyRedirectRequest request
    );
}

