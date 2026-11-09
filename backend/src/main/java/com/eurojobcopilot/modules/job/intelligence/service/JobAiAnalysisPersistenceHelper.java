package com.eurojobcopilot.modules.job.intelligence.service;

import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.dto.JobAiAnalysisDto;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysisHistory;
import com.eurojobcopilot.modules.job.intelligence.analyzer.RawAiJdExtractionPayload;
import com.eurojobcopilot.modules.job.intelligence.analyzer.ValidatedJobDescriptionAnalysis;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisHistoryRepository;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles short transactional writes updating the latest AI analysis cache and appending
 * an immutable versioned history snapshot after external LLM calls and validation complete.
 */
@Component
@RequiredArgsConstructor
public class JobAiAnalysisPersistenceHelper {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final JobAiAnalysisRepository jobAiAnalysisRepository;
    private final JobAiAnalysisHistoryRepository jobAiAnalysisHistoryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public JobAiAnalysisDto saveValidatedAnalysis(
            Long jobId,
            Long triggeredByUserId,
            ValidatedJobDescriptionAnalysis validated,
            AiStructuredResponse<RawAiJdExtractionPayload> aiResponse
    ) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        User triggeredByUser = triggeredByUserId != null
                ? userRepository.findById(triggeredByUserId).orElse(null)
                : null;

        JobAiAnalysis currentAnalysis = jobAiAnalysisRepository.findByJobId(jobId)
                .orElseGet(() -> JobAiAnalysis.builder().job(job).build());

        int existingCacheVersion = currentAnalysis.getId() != null && currentAnalysis.getAnalysisVersion() != null
                ? currentAnalysis.getAnalysisVersion()
                : 0;
        int maxHistoryVersion = jobAiAnalysisHistoryRepository.findTopByJobIdOrderByVersionNumberDesc(jobId)
                .map(JobAiAnalysisHistory::getVersionNumber)
                .orElse(0);
        int nextVersion = Math.max(existingCacheVersion, maxHistoryVersion) + 1;

        Map<String, Object> rawPayload = parseRawPayload(aiResponse.normalizedJson());
        Instant analyzedAt = Instant.now();

        currentAnalysis.setExtractedTitle(validated.extractedTitle());
        currentAnalysis.setExtractedCompany(validated.extractedCompany());
        currentAnalysis.setExtractedCountry(validated.extractedCountry());
        currentAnalysis.setExtractedLocation(validated.extractedLocation());
        currentAnalysis.setRequiredExperienceYears(validated.requiredExperienceYears());
        currentAnalysis.setRequiredExperienceLevel(validated.requiredExperienceLevel());
        currentAnalysis.setRequiredExperienceSummary(validated.requiredExperienceSummary());
        currentAnalysis.setRequiredSkills(new ArrayList<>(validated.requiredSkills()));
        currentAnalysis.setNiceToHaveSkills(new ArrayList<>(validated.niceToHaveSkills()));
        currentAnalysis.setResponsibilities(new ArrayList<>(validated.responsibilities()));
        currentAnalysis.setBenefits(new ArrayList<>(validated.benefits()));
        currentAnalysis.setLanguageRequirements(new ArrayList<>(validated.languageRequirements()));
        currentAnalysis.setSalaryMin(validated.salaryMin());
        currentAnalysis.setSalaryMax(validated.salaryMax());
        currentAnalysis.setSalaryCurrency(validated.salaryCurrency());
        currentAnalysis.setSalaryPeriod(validated.salaryPeriod());
        currentAnalysis.setWorkArrangement(validated.workArrangement());
        currentAnalysis.setEmploymentType(validated.employmentType());
        currentAnalysis.setSponsorshipOffered(validated.sponsorshipOffered());
        currentAnalysis.setVisaSponsorshipAnalysis(validated.visaSponsorshipAnalysis());
        currentAnalysis.setRelocationAssistanceOffered(validated.relocationAssistanceOffered());
        currentAnalysis.setRelocationAnalysis(validated.relocationAnalysis());
        currentAnalysis.setSummary(validated.summary());
        currentAnalysis.setAiProvider(aiResponse.provider());
        currentAnalysis.setAiModelUsed(aiResponse.model());
        currentAnalysis.setAnalysisVersion(nextVersion);
        currentAnalysis.setRawPayloadJson(new LinkedHashMap<>(rawPayload));

        JobAiAnalysis savedAnalysis = jobAiAnalysisRepository.saveAndFlush(currentAnalysis);

        JobAiAnalysisHistory historyEntry = JobAiAnalysisHistory.builder()
                .job(job)
                .versionNumber(nextVersion)
                .triggeredByUser(triggeredByUser)
                .extractedTitle(validated.extractedTitle())
                .extractedCompany(validated.extractedCompany())
                .extractedCountry(validated.extractedCountry())
                .extractedLocation(validated.extractedLocation())
                .requiredExperienceYears(validated.requiredExperienceYears())
                .requiredExperienceLevel(validated.requiredExperienceLevel())
                .requiredExperienceSummary(validated.requiredExperienceSummary())
                .requiredSkills(new ArrayList<>(validated.requiredSkills()))
                .niceToHaveSkills(new ArrayList<>(validated.niceToHaveSkills()))
                .responsibilities(new ArrayList<>(validated.responsibilities()))
                .benefits(new ArrayList<>(validated.benefits()))
                .languageRequirements(new ArrayList<>(validated.languageRequirements()))
                .salaryMin(validated.salaryMin())
                .salaryMax(validated.salaryMax())
                .salaryCurrency(validated.salaryCurrency())
                .salaryPeriod(validated.salaryPeriod())
                .workArrangement(validated.workArrangement())
                .employmentType(validated.employmentType())
                .sponsorshipOffered(validated.sponsorshipOffered())
                .visaSponsorshipAnalysis(validated.visaSponsorshipAnalysis())
                .relocationAssistanceOffered(validated.relocationAssistanceOffered())
                .relocationAnalysis(validated.relocationAnalysis())
                .summary(validated.summary())
                .aiProvider(aiResponse.provider())
                .aiModelUsed(aiResponse.model())
                .rawPayloadJson(new LinkedHashMap<>(rawPayload))
                .analyzedAt(analyzedAt)
                .build();

        JobAiAnalysisHistory savedHistory = jobAiAnalysisHistoryRepository.saveAndFlush(historyEntry);

        JobAiAnalysisDto dto = toDto(savedAnalysis);
        dto.setAnalyzedAt(savedHistory.getAnalyzedAt());
        return dto;
    }

    public JobAiAnalysisDto toDto(JobAiAnalysis entity) {
        if (entity == null) {
            return null;
        }
        return JobAiAnalysisDto.builder()
                .id(entity.getId())
                .jobId(entity.getJob() != null ? entity.getJob().getId() : null)
                .analysisVersion(entity.getAnalysisVersion())
                .extractedTitle(entity.getExtractedTitle())
                .extractedCompany(entity.getExtractedCompany())
                .extractedCountry(entity.getExtractedCountry())
                .extractedLocation(entity.getExtractedLocation())
                .requiredExperienceYears(entity.getRequiredExperienceYears())
                .requiredExperienceLevel(entity.getRequiredExperienceLevel())
                .requiredExperienceSummary(entity.getRequiredExperienceSummary())
                .requiredSkills(safeCopy(entity.getRequiredSkills()))
                .niceToHaveSkills(safeCopy(entity.getNiceToHaveSkills()))
                .responsibilities(safeCopy(entity.getResponsibilities()))
                .benefits(safeCopy(entity.getBenefits()))
                .languageRequirements(safeCopy(entity.getLanguageRequirements()))
                .salaryMin(entity.getSalaryMin())
                .salaryMax(entity.getSalaryMax())
                .salaryCurrency(entity.getSalaryCurrency())
                .salaryPeriod(entity.getSalaryPeriod())
                .workArrangement(entity.getWorkArrangement())
                .employmentType(entity.getEmploymentType())
                .sponsorshipOffered(entity.getSponsorshipOffered())
                .visaSponsorshipAnalysis(entity.getVisaSponsorshipAnalysis())
                .relocationAssistanceOffered(entity.getRelocationAssistanceOffered())
                .relocationAnalysis(entity.getRelocationAnalysis())
                .summary(entity.getSummary())
                .aiProvider(entity.getAiProvider())
                .aiModelUsed(entity.getAiModelUsed())
                .analyzedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt() : entity.getCreatedAt())
                .build();
    }

    public JobAiAnalysisDto toDto(JobAiAnalysisHistory history) {
        if (history == null) {
            return null;
        }
        return JobAiAnalysisDto.builder()
                .id(history.getId())
                .jobId(history.getJob() != null ? history.getJob().getId() : null)
                .analysisVersion(history.getVersionNumber())
                .extractedTitle(history.getExtractedTitle())
                .extractedCompany(history.getExtractedCompany())
                .extractedCountry(history.getExtractedCountry())
                .extractedLocation(history.getExtractedLocation())
                .requiredExperienceYears(history.getRequiredExperienceYears())
                .requiredExperienceLevel(history.getRequiredExperienceLevel())
                .requiredExperienceSummary(history.getRequiredExperienceSummary())
                .requiredSkills(safeCopy(history.getRequiredSkills()))
                .niceToHaveSkills(safeCopy(history.getNiceToHaveSkills()))
                .responsibilities(safeCopy(history.getResponsibilities()))
                .benefits(safeCopy(history.getBenefits()))
                .languageRequirements(safeCopy(history.getLanguageRequirements()))
                .salaryMin(history.getSalaryMin())
                .salaryMax(history.getSalaryMax())
                .salaryCurrency(history.getSalaryCurrency())
                .salaryPeriod(history.getSalaryPeriod())
                .workArrangement(history.getWorkArrangement())
                .employmentType(history.getEmploymentType())
                .sponsorshipOffered(history.getSponsorshipOffered())
                .visaSponsorshipAnalysis(history.getVisaSponsorshipAnalysis())
                .relocationAssistanceOffered(history.getRelocationAssistanceOffered())
                .relocationAnalysis(history.getRelocationAnalysis())
                .summary(history.getSummary())
                .aiProvider(history.getAiProvider())
                .aiModelUsed(history.getAiModelUsed())
                .analyzedAt(history.getAnalyzedAt())
                .build();
    }

    private Map<String, Object> parseRawPayload(String normalizedJson) {
        if (normalizedJson == null || normalizedJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(normalizedJson, MAP_TYPE_REF);
            return parsed != null ? parsed : new LinkedHashMap<>();
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private List<String> safeCopy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}

