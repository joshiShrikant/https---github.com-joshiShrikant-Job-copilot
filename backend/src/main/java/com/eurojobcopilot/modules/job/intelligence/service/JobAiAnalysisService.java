package com.eurojobcopilot.modules.job.intelligence.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobAiAnalysisDto;

import java.util.List;

public interface JobAiAnalysisService {

    JobAiAnalysisDto analyzeJobDescription(
            AuthenticatedUserPrincipal currentUser,
            Long jobId,
            Boolean forceRefresh
    );

    JobAiAnalysisDto getLatestAnalysis(
            AuthenticatedUserPrincipal currentUser,
            Long jobId
    );

    List<JobAiAnalysisDto> getAnalysisHistory(
            AuthenticatedUserPrincipal currentUser,
            Long jobId
    );
}

