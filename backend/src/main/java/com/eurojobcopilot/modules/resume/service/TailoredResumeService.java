package com.eurojobcopilot.modules.resume.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.resume.dto.GenerateTailoredResumeRequest;
import com.eurojobcopilot.modules.resume.dto.ResumeDownloadResource;
import com.eurojobcopilot.modules.resume.dto.ResumeExportFormat;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeResponse;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeSummaryDto;
import com.eurojobcopilot.modules.resume.dto.UpdateTailoredResumeRequest;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;

import java.util.List;

public interface TailoredResumeService {

    TailoredResumeResponse generateTailoredResume(
            AuthenticatedUserPrincipal currentUser,
            GenerateTailoredResumeRequest request
    );

    List<TailoredResumeSummaryDto> listTailoredResumes(
            AuthenticatedUserPrincipal currentUser,
            Long jobId
    );

    TailoredResumeResponse getTailoredResume(
            AuthenticatedUserPrincipal currentUser,
            String uuid
    );

    TailoredResumeResponse updateTailoredResume(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            UpdateTailoredResumeRequest request
    );

    ResumeDownloadResource exportTailoredResume(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            ResumeExportFormat format,
            ResumeTemplate templateOverride,
            boolean regenerate
    );

    void deleteTailoredResume(
            AuthenticatedUserPrincipal currentUser,
            String uuid
    );
}

