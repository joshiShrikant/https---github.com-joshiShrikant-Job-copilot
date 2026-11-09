package com.eurojobcopilot.modules.resume.service;

import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.matching.service.JobMatchingService;
import com.eurojobcopilot.modules.resume.dto.ApproveMasterResumeRequest;
import com.eurojobcopilot.modules.resume.dto.MasterResumeSyncSummaryDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ResumeParseResponse;
import com.eurojobcopilot.modules.resume.dto.ResumeResponse;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.entity.ResumeParseStatus;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import com.eurojobcopilot.modules.resume.exception.ResumeConflictException;
import com.eurojobcopilot.modules.resume.exception.ResumeNotFoundException;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles short transactional persistence for master resume parsing drafts and human-approved sync commits,
 * keeping slow external AI requests completely outside database transactions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterResumePersistenceHelper {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final MasterResumeApprovalProfileSyncService profileSyncService;
    private final ObjectMapper objectMapper;
    private final JobMatchingService jobMatchingService;

    @Transactional(readOnly = true)
    public ResumeSnapshot loadMasterResumeSnapshot(Long userId, String uuid) {
        Resume resume = findOwnedMasterResume(uuid, userId);
        ParsedResumeJsonDto existingParsed = deserializeParsedContent(resume.getParsedContentJson());
        ResumeParseStatus effectiveStatus = resume.getParseStatus() != null
                ? resume.getParseStatus()
                : ResumeParseStatus.NOT_PARSED;

        return new ResumeSnapshot(
                resume,
                effectiveStatus,
                existingParsed
        );
    }

    @Transactional
    public ResumeParseResponse saveParsedDraft(
            Long userId,
            String uuid,
            ParsedResumeJsonDto parsedDto,
            List<String> warnings,
            String aiProvider,
            String aiModelUsed,
            int extractedCharCount
    ) {
        Resume resume = findOwnedMasterResume(uuid, userId);
        Map<String, Object> serializedMap = objectMapper.convertValue(parsedDto, MAP_TYPE_REF);

        resume.setParsedContentJson(serializedMap != null ? serializedMap : new LinkedHashMap<>());
        resume.setParseStatus(ResumeParseStatus.PARSED);
        resume.setParsedAt(Instant.now());
        resume.setApprovedAt(null);
        resume.setMasterApproved(false);
        resume.setAiProvider(aiProvider);
        resume.setAiModelUsed(aiModelUsed);
        resume.setExtractedCharCount(extractedCharCount);

        Resume saved = resumeRepository.saveAndFlush(resume);
        log.info("Saved parsed draft for master resume uuid={} status={} provider={} model={}",
                saved.getUuid(), saved.getParseStatus(), saved.getAiProvider(), saved.getAiModelUsed());

        return toParseResponse(saved, parsedDto, warnings);
    }

    @Transactional
    public ResumeResponse approveAndSyncMasterResume(Long userId, ApproveMasterResumeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResumeNotFoundException("Authenticated user no longer exists"));
        Resume target = findOwnedMasterResume(request.getResumeUuid(), user.getId());

        MasterResumeApprovalProfileSyncService.SyncResult syncResult = profileSyncService.validateAndSync(user, request);

        List<Resume> previouslyApproved = resumeRepository.findAllByUserIdAndResumeTypeAndMasterApprovedTrue(
                user.getId(), ResumeType.MASTER
        );
        List<Resume> demoted = new java.util.ArrayList<>();
        for (Resume other : previouslyApproved) {
            if (!other.getId().equals(target.getId())) {
                other.setMasterApproved(false);
                if (other.getParseStatus() == ResumeParseStatus.APPROVED) {
                    other.setParseStatus(ResumeParseStatus.PARSED);
                }
                other.setApprovedAt(null);
                demoted.add(other);
            }
        }
        if (!demoted.isEmpty()) {
            resumeRepository.saveAllAndFlush(demoted);
        }

        Map<String, Object> approvedMap = objectMapper.convertValue(syncResult.normalizedApprovedData(), MAP_TYPE_REF);
        target.setParsedContentJson(approvedMap != null ? approvedMap : new LinkedHashMap<>());
        target.setMasterApproved(true);
        target.setParseStatus(ResumeParseStatus.APPROVED);
        target.setApprovedAt(syncResult.summary().getSyncedAt());

        Resume saved = resumeRepository.saveAndFlush(target);
        jobMatchingService.recalculateActiveJobsForUser(user.getId());
        log.info("Approved master resume uuid={} syncMode={} skillsAdded={} expAdded={} eduAdded={}",
                saved.getUuid(),
                syncResult.summary().getSyncMode(),
                syncResult.summary().getSkillsAdded(),
                syncResult.summary().getExperiencesAdded(),
                syncResult.summary().getEducationsAdded());

        return toResumeResponse(saved, syncResult.summary());
    }

    public ResumeParseResponse toParseResponse(Resume resume, ParsedResumeJsonDto parsedDto, List<String> overrideWarnings) {
        List<String> warnings = overrideWarnings != null
                ? List.copyOf(overrideWarnings)
                : (parsedDto != null && parsedDto.getWarnings() != null ? List.copyOf(parsedDto.getWarnings()) : List.of());

        ResumeParseStatus effectiveStatus = resume.getParseStatus() != null
                ? resume.getParseStatus()
                : ResumeParseStatus.NOT_PARSED;

        return ResumeParseResponse.builder()
                .resumeUuid(resume.getUuid())
                .title(resume.getTitle())
                .fileName(resume.getFileName())
                .mimeType(resume.getMimeType())
                .versionNumber(resume.getVersionNumber())
                .masterApproved(resume.isMasterApproved())
                .parseStatus(effectiveStatus)
                .parsedAt(resume.getParsedAt())
                .approvedAt(resume.getApprovedAt())
                .aiProvider(resume.getAiProvider())
                .aiModelUsed(resume.getAiModelUsed())
                .extractedCharCount(resume.getExtractedCharCount())
                .parsedData(parsedDto)
                .warnings(warnings)
                .build();
    }

    public ResumeResponse toResumeResponse(Resume resume, MasterResumeSyncSummaryDto syncSummary) {
        boolean hasParsed = resume.getParsedContentJson() != null && !resume.getParsedContentJson().isEmpty();
        ResumeParseStatus effectiveStatus = resume.getParseStatus() != null
                ? resume.getParseStatus()
                : ResumeParseStatus.NOT_PARSED;

        return ResumeResponse.builder()
                .uuid(resume.getUuid())
                .title(resume.getTitle())
                .fileName(resume.getFileName())
                .fileSizeBytes(resume.getFileSizeBytes())
                .mimeType(resume.getMimeType())
                .resumeType(resume.getResumeType())
                .storageProvider(resume.getStorageProvider())
                .versionNumber(resume.getVersionNumber())
                .masterApproved(resume.isMasterApproved())
                .parseStatus(effectiveStatus)
                .parsedAt(resume.getParsedAt())
                .approvedAt(resume.getApprovedAt())
                .aiProvider(resume.getAiProvider())
                .aiModelUsed(resume.getAiModelUsed())
                .hasParsedContent(hasParsed)
                .profileSyncSummary(syncSummary)
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }

    public ParsedResumeJsonDto deserializeParsedContent(Map<String, Object> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.convertValue(rawMap, ParsedResumeJsonDto.class);
        } catch (IllegalArgumentException ex) {
            log.warn("Stored parsedContentJson could not be converted into ParsedResumeJsonDto", ex);
            return null;
        }
    }

    private Resume findOwnedMasterResume(String uuid, Long userId) {
        if (uuid == null || uuid.isBlank()) {
            throw new ResumeNotFoundException("Resume not found");
        }
        Resume resume = resumeRepository.findByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found"));
        if (resume.getResumeType() != ResumeType.MASTER) {
            throw new ResumeConflictException("Only MASTER resumes can be parsed or approved as master CV");
        }
        return resume;
    }

    public record ResumeSnapshot(
            Resume resume,
            ResumeParseStatus effectiveStatus,
            ParsedResumeJsonDto existingParsedData
    ) {
    }
}
