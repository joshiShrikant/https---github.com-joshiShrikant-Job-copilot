package com.eurojobcopilot.modules.job.intelligence.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobAiAnalysisDto;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.intelligence.analyzer.JobDescriptionAnalysisValidator;
import com.eurojobcopilot.modules.job.intelligence.analyzer.JobDescriptionPromptBuilder;
import com.eurojobcopilot.modules.job.intelligence.analyzer.RawAiJdExtractionPayload;
import com.eurojobcopilot.modules.job.intelligence.analyzer.ValidatedJobDescriptionAnalysis;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisHistoryRepository;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobAiAnalysisServiceImpl implements JobAiAnalysisService {

    private final JobRepository jobRepository;
    private final JobAiAnalysisRepository jobAiAnalysisRepository;
    private final JobAiAnalysisHistoryRepository jobAiAnalysisHistoryRepository;
    private final JobDescriptionPromptBuilder promptBuilder;
    private final AiGatewayService aiGatewayService;
    private final JobDescriptionAnalysisValidator analysisValidator;
    private final JobAiAnalysisPersistenceHelper persistenceHelper;

    @Override
    public JobAiAnalysisDto analyzeJobDescription(
            AuthenticatedUserPrincipal currentUser,
            Long jobId,
            Boolean forceRefresh
    ) {
        Long userId = requireUserId(currentUser);
        validateJobId(jobId);

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        if (Boolean.FALSE.equals(forceRefresh)) {
            JobAiAnalysisDto cached = jobAiAnalysisRepository.findByJobId(jobId)
                    .map(persistenceHelper::toDto)
                    .orElse(null);
            if (cached != null) {
                log.debug("Returning cached AI JD analysis jobId={} version={}", jobId, cached.getAnalysisVersion());
                return cached;
            }
        }

        if (job.getDescription() == null || job.getDescription().isBlank()) {
            throw new BusinessValidationException("Job posting description is empty and cannot be analyzed.");
        }

        AiJsonPromptRequest promptRequest = promptBuilder.buildPromptRequest(job);
        AiStructuredResponse<RawAiJdExtractionPayload> aiResponse =
                aiGatewayService.executeStructuredJsonPrompt(promptRequest, RawAiJdExtractionPayload.class);

        ValidatedJobDescriptionAnalysis validated = analysisValidator.validate(aiResponse.payload());

        JobAiAnalysisDto saved = persistenceHelper.saveValidatedAnalysis(jobId, userId, validated, aiResponse);
        log.info("Stored validated AI JD analysis jobId={} version={} provider={} model={}",
                jobId, saved.getAnalysisVersion(), saved.getAiProvider(), saved.getAiModelUsed());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public JobAiAnalysisDto getLatestAnalysis(AuthenticatedUserPrincipal currentUser, Long jobId) {
        requireUserId(currentUser);
        validateJobId(jobId);
        ensureJobExists(jobId);

        return jobAiAnalysisRepository.findByJobId(jobId)
                .map(persistenceHelper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No AI job description analysis found for job id: " + jobId
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobAiAnalysisDto> getAnalysisHistory(AuthenticatedUserPrincipal currentUser, Long jobId) {
        requireUserId(currentUser);
        validateJobId(jobId);
        ensureJobExists(jobId);

        return jobAiAnalysisHistoryRepository.findAllByJobIdOrderByVersionNumberDesc(jobId)
                .stream()
                .map(persistenceHelper::toDto)
                .toList();
    }

    private Long requireUserId(AuthenticatedUserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return currentUser.getId();
    }

    private void validateJobId(Long jobId) {
        if (jobId == null || jobId <= 0) {
            throw new BusinessValidationException("Job ID must be a positive integer");
        }
    }

    private void ensureJobExists(Long jobId) {
        if (!jobRepository.existsById(jobId)) {
            throw new ResourceNotFoundException("Job not found with id: " + jobId);
        }
    }
}

