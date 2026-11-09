package com.eurojobcopilot.modules.matching.ai.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobMatchBreakdownDto;
import com.eurojobcopilot.modules.matching.ai.engine.SemanticMatchPromptBuilder;
import com.eurojobcopilot.modules.matching.ai.engine.SemanticMatchValidator;
import com.eurojobcopilot.modules.matching.ai.model.RawAiSemanticMatchPayload;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot;
import com.eurojobcopilot.modules.matching.ai.model.ValidatedSemanticMatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticJobMatchingServiceImpl implements SemanticJobMatchingService {

    private final SemanticJobMatchingPersistenceHelper persistenceHelper;
    private final SemanticMatchPromptBuilder promptBuilder;
    private final AiGatewayService aiGatewayService;
    private final SemanticMatchValidator semanticMatchValidator;

    @Override
    public JobMatchBreakdownDto evaluateSemanticMatch(
            AuthenticatedUserPrincipal currentUser,
            Long jobId,
            Boolean forceRefresh
    ) {
        Long userId = requireUserId(currentUser);
        if (jobId == null || jobId <= 0) {
            throw new BusinessValidationException("Job ID must be a positive integer");
        }

        SemanticMatchContextSnapshot snapshot = persistenceHelper.loadEvaluationSnapshot(userId, jobId);

        if (Boolean.FALSE.equals(forceRefresh) && snapshot.cachedBreakdown() != null) {
            log.debug("Returning cached AI semantic match userId={} jobId={} aiScore={}",
                    userId, jobId, snapshot.cachedBreakdown().getAiScore());
            return snapshot.cachedBreakdown();
        }

        if (!snapshot.hasCandidateEvidence()) {
            throw new BusinessValidationException(
                    "Candidate profile, skills, work experience, and master resume are empty. "
                            + "Complete your profile or upload a master resume before running AI semantic matching."
            );
        }

        if (!snapshot.hasJobContent()) {
            throw new BusinessValidationException(
                    "Job posting lacks description and structured requirements for AI semantic matching."
            );
        }

        AiJsonPromptRequest promptRequest = promptBuilder.buildPromptRequest(snapshot);
        AiStructuredResponse<RawAiSemanticMatchPayload> aiResponse =
                aiGatewayService.executeStructuredJsonPrompt(promptRequest, RawAiSemanticMatchPayload.class);

        ValidatedSemanticMatchResult validated = semanticMatchValidator.validate(aiResponse.payload());

        JobMatchBreakdownDto saved = persistenceHelper.saveValidatedSemanticMatch(
                userId,
                jobId,
                validated,
                aiResponse
        );
        log.info("Stored AI semantic match evaluation userUuid={} jobId={} deterministicScore={} aiScore={} finalScore={} provider={} model={}",
                currentUser.getUuid(),
                jobId,
                saved.getDeterministicScore(),
                saved.getAiScore(),
                saved.getFinalScore(),
                saved.getAiProvider(),
                saved.getAiModelUsed());
        return saved;
    }

    private Long requireUserId(AuthenticatedUserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return currentUser.getId();
    }
}

