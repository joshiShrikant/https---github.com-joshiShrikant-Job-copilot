package com.eurojobcopilot.modules.application.service;

import com.eurojobcopilot.modules.application.dto.CreateApplicationRequest;
import com.eurojobcopilot.modules.application.dto.JobApplicationDetailResponse;
import com.eurojobcopilot.modules.application.dto.UpdateApplicationRequest;
import com.eurojobcopilot.modules.application.dto.UpdateApplicationStatusRequest;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;

import java.util.List;

public interface ApplicationService {

    List<JobApplicationDetailResponse> listApplications(
            AuthenticatedUserPrincipal currentUser,
            ApplicationStatus status,
            String query,
            Boolean dueFollowUpOnly,
            Boolean hasInterviewOnly
    );

    JobApplicationDetailResponse getApplication(AuthenticatedUserPrincipal currentUser, String uuid);

    JobApplicationDetailResponse createApplication(
            AuthenticatedUserPrincipal currentUser,
            CreateApplicationRequest request
    );

    JobApplicationDetailResponse updateApplication(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            UpdateApplicationRequest request
    );

    JobApplicationDetailResponse updateApplicationStatus(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            UpdateApplicationStatusRequest request
    );

    void deleteApplication(AuthenticatedUserPrincipal currentUser, String uuid);
}

