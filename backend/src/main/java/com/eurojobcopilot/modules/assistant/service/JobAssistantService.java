package com.eurojobcopilot.modules.assistant.service;

import com.eurojobcopilot.modules.assistant.dto.JobAssistantMessageResponse;
import com.eurojobcopilot.modules.assistant.dto.JobAssistantRespondRequest;
import com.eurojobcopilot.modules.assistant.dto.UpdateJobAssistantMessageRequest;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;

import java.util.List;

public interface JobAssistantService {

    List<JobAssistantMessageResponse> getHistory(AuthenticatedUserPrincipal currentUser, Long jobId);

    JobAssistantMessageResponse respond(
            AuthenticatedUserPrincipal currentUser,
            Long jobId,
            JobAssistantRespondRequest request
    );

    JobAssistantMessageResponse updateEditableResponse(
            AuthenticatedUserPrincipal currentUser,
            Long jobId,
            Long messageId,
            UpdateJobAssistantMessageRequest request
    );

    void deleteMessage(AuthenticatedUserPrincipal currentUser, Long jobId, Long messageId);
}

