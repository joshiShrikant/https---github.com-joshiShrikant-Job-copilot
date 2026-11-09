package com.eurojobcopilot.modules.interview.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.interview.dto.CreateInterviewPrepSessionRequest;
import com.eurojobcopilot.modules.interview.dto.InterviewPerformanceHistoryResponse;
import com.eurojobcopilot.modules.interview.dto.InterviewPrepSessionDetailResponse;
import com.eurojobcopilot.modules.interview.dto.InterviewPrepSessionSummaryDto;
import com.eurojobcopilot.modules.interview.dto.SubmitInterviewAnswerRequest;

import java.util.List;

public interface InterviewPrepService {

    InterviewPrepSessionDetailResponse createSession(
            AuthenticatedUserPrincipal currentUser,
            CreateInterviewPrepSessionRequest request
    );

    List<InterviewPrepSessionSummaryDto> listSessions(AuthenticatedUserPrincipal currentUser, Long jobId);

    InterviewPrepSessionDetailResponse getSession(AuthenticatedUserPrincipal currentUser, Long sessionId);

    InterviewPrepSessionDetailResponse submitAnswer(
            AuthenticatedUserPrincipal currentUser,
            Long sessionId,
            SubmitInterviewAnswerRequest request
    );

    void deleteSession(AuthenticatedUserPrincipal currentUser, Long sessionId);

    InterviewPerformanceHistoryResponse getPerformanceHistory(AuthenticatedUserPrincipal currentUser);
}

