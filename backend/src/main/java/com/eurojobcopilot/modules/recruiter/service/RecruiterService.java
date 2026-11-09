package com.eurojobcopilot.modules.recruiter.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.recruiter.dto.CreateRecruiterInteractionRequest;
import com.eurojobcopilot.modules.recruiter.dto.CreateRecruiterRequest;
import com.eurojobcopilot.modules.recruiter.dto.FollowUpRemindersResponse;
import com.eurojobcopilot.modules.recruiter.dto.GenerateOutreachRequest;
import com.eurojobcopilot.modules.recruiter.dto.OutreachDraftResponse;
import com.eurojobcopilot.modules.recruiter.dto.PagedRecruitersResponse;
import com.eurojobcopilot.modules.recruiter.dto.RecruiterInteractionDto;
import com.eurojobcopilot.modules.recruiter.dto.RecruiterResponse;
import com.eurojobcopilot.modules.recruiter.dto.UpdateRecruiterInteractionRequest;
import com.eurojobcopilot.modules.recruiter.dto.UpdateRecruiterRequest;

public interface RecruiterService {

    PagedRecruitersResponse listRecruiters(
            AuthenticatedUserPrincipal currentUser,
            String query,
            Long companyId,
            int page,
            int size
    );

    RecruiterResponse getRecruiter(AuthenticatedUserPrincipal currentUser, Long recruiterId);

    RecruiterResponse createRecruiter(AuthenticatedUserPrincipal currentUser, CreateRecruiterRequest request);

    RecruiterResponse updateRecruiter(
            AuthenticatedUserPrincipal currentUser,
            Long recruiterId,
            UpdateRecruiterRequest request
    );

    void deleteRecruiter(AuthenticatedUserPrincipal currentUser, Long recruiterId);

    OutreachDraftResponse generateOutreach(
            AuthenticatedUserPrincipal currentUser,
            Long recruiterId,
            GenerateOutreachRequest request
    );

    RecruiterInteractionDto createInteraction(
            AuthenticatedUserPrincipal currentUser,
            Long recruiterId,
            CreateRecruiterInteractionRequest request
    );

    RecruiterInteractionDto updateInteraction(
            AuthenticatedUserPrincipal currentUser,
            Long recruiterId,
            Long interactionId,
            UpdateRecruiterInteractionRequest request
    );

    void deleteInteraction(
            AuthenticatedUserPrincipal currentUser,
            Long recruiterId,
            Long interactionId
    );

    FollowUpRemindersResponse getFollowUpReminders(
            AuthenticatedUserPrincipal currentUser,
            boolean includeCompleted
    );
}

