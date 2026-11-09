package com.eurojobcopilot.modules.resume.service;

import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.resume.dto.ApproveMasterResumeRequest;
import com.eurojobcopilot.modules.resume.dto.ResumeDownloadResource;
import com.eurojobcopilot.modules.resume.dto.ResumeParseResponse;
import com.eurojobcopilot.modules.resume.dto.ResumeResponse;
import com.eurojobcopilot.modules.resume.dto.StoredResumeFile;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.entity.ResumeParseStatus;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import com.eurojobcopilot.modules.resume.entity.StorageProvider;
import com.eurojobcopilot.modules.resume.exception.InvalidResumeFileException;
import com.eurojobcopilot.modules.resume.exception.ResumeConflictException;
import com.eurojobcopilot.modules.resume.exception.ResumeNotFoundException;
import com.eurojobcopilot.modules.resume.parser.ExtractedResumeText;
import com.eurojobcopilot.modules.resume.parser.MasterResumeParsePromptBuilder;
import com.eurojobcopilot.modules.resume.parser.MasterResumeParseValidator;
import com.eurojobcopilot.modules.resume.parser.RawAiParsedResumePayload;
import com.eurojobcopilot.modules.resume.parser.ResumeTextExtractor;
import com.eurojobcopilot.modules.resume.parser.ValidatedMasterResumeParse;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.resume.repository.TailoredResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private static final int MAX_TITLE_LENGTH = 200;

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final FileStorageService fileStorageService;
    private final ResumeTextExtractor resumeTextExtractor;
    private final MasterResumeParsePromptBuilder promptBuilder;
    private final AiGatewayService aiGatewayService;
    private final MasterResumeParseValidator parseValidator;
    private final MasterResumePersistenceHelper persistenceHelper;

    @Override
    @Transactional
    public ResumeResponse uploadMasterResume(
            AuthenticatedUserPrincipal currentUser,
            MultipartFile file,
            String title
    ) {
        User user = requireAuthenticatedUser(currentUser);
        String validatedTitle = validateTitle(title);

        StoredResumeFile storedFile = fileStorageService.storeResumeFile(file, user.getUuid());

        try {
            Integer currentMaxVersion = resumeRepository.findMaxVersionNumberByUserId(user.getId());
            int nextVersion = (currentMaxVersion == null ? 0 : currentMaxVersion) + 1;

            Resume resume = Resume.builder()
                    .uuid(UUID.randomUUID().toString())
                    .user(user)
                    .resumeType(ResumeType.MASTER)
                    .title(validatedTitle)
                    .fileName(storedFile.sanitizedFileName())
                    .filePath(storedFile.storageKey())
                    .fileSizeBytes(storedFile.fileSizeBytes())
                    .mimeType(storedFile.mimeType())
                    .storageProvider(StorageProvider.LOCAL)
                    .versionNumber(nextVersion)
                    .masterApproved(false)
                    .parseStatus(ResumeParseStatus.NOT_PARSED)
                    .parsedContentJson(null)
                    .build();

            Resume saved = resumeRepository.saveAndFlush(resume);
            log.info("Uploaded master resume uuid={} version={} for userUuid={}",
                    saved.getUuid(), saved.getVersionNumber(), user.getUuid());
            return toResponse(saved);
        } catch (RuntimeException ex) {
            deleteFileQuietly(storedFile.storageKey());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumeResponse> listResumes(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        return resumeRepository.findAllByUserIdOrderByVersionNumberDescCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeResponse getResume(AuthenticatedUserPrincipal currentUser, String uuid) {
        User user = requireAuthenticatedUser(currentUser);
        Resume resume = findOwnedResume(uuid, user.getId());
        return toResponse(resume);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeDownloadResource downloadResume(AuthenticatedUserPrincipal currentUser, String uuid) {
        User user = requireAuthenticatedUser(currentUser);
        Resume resume = findOwnedResume(uuid, user.getId());
        Resource resource = fileStorageService.loadResumeAsResource(resume.getFilePath());

        return new ResumeDownloadResource(
                resource,
                resume.getFileName(),
                resume.getMimeType(),
                resume.getFileSizeBytes()
        );
    }

    @Override
    @Transactional
    public void deleteResume(AuthenticatedUserPrincipal currentUser, String uuid) {
        User user = requireAuthenticatedUser(currentUser);
        Resume resume = findOwnedResume(uuid, user.getId());

        if (tailoredResumeRepository.existsByMasterResumeId(resume.getId())) {
            throw new ResumeConflictException(
                    "Cannot delete master resume because one or more tailored resumes depend on it"
            );
        }

        String storageKey = resume.getFilePath();
        try {
            resumeRepository.delete(resume);
            resumeRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResumeConflictException(
                    "Cannot delete master resume because dependent records reference it"
            );
        }

        fileStorageService.deleteResumeFile(storageKey);
        log.info("Deleted resume uuid={} for userUuid={}", resume.getUuid(), user.getUuid());
    }

    @Override
    public ResumeParseResponse parseMasterResume(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            boolean forceRefresh
    ) {
        User user = requireAuthenticatedUser(currentUser);
        MasterResumePersistenceHelper.ResumeSnapshot snapshot = persistenceHelper.loadMasterResumeSnapshot(user.getId(), uuid);

        if (!forceRefresh
                && snapshot.effectiveStatus() != ResumeParseStatus.NOT_PARSED
                && snapshot.existingParsedData() != null) {
            log.debug("Returning cached parsed master resume uuid={} status={}",
                    snapshot.resume().getUuid(), snapshot.effectiveStatus());
            return persistenceHelper.toParseResponse(snapshot.resume(), snapshot.existingParsedData(), null);
        }

        Resource fileResource = fileStorageService.loadResumeAsResource(snapshot.resume().getFilePath());
        ExtractedResumeText extracted = resumeTextExtractor.extract(
                fileResource,
                snapshot.resume().getMimeType(),
                snapshot.resume().getFileName()
        );

        MasterResumeParsePromptBuilder.PromptBuildResult promptResult = promptBuilder.buildPromptRequest(snapshot.resume(), extracted);
        AiStructuredResponse<RawAiParsedResumePayload> aiResponse = aiGatewayService.executeStructuredJsonPrompt(
                promptResult.request(),
                RawAiParsedResumePayload.class
        );

        ValidatedMasterResumeParse validated = parseValidator.validate(
                aiResponse.payload(),
                extracted,
                promptResult.inputTruncated()
        );

        return persistenceHelper.saveParsedDraft(
                user.getId(),
                snapshot.resume().getUuid(),
                validated.parsedData(),
                validated.warnings(),
                aiResponse.provider(),
                aiResponse.model(),
                extracted.charCount()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeParseResponse getParsedResume(AuthenticatedUserPrincipal currentUser, String uuid) {
        User user = requireAuthenticatedUser(currentUser);
        MasterResumePersistenceHelper.ResumeSnapshot snapshot = persistenceHelper.loadMasterResumeSnapshot(user.getId(), uuid);
        return persistenceHelper.toParseResponse(snapshot.resume(), snapshot.existingParsedData(), null);
    }

    @Override
    public ResumeResponse approveMasterResume(
            AuthenticatedUserPrincipal currentUser,
            ApproveMasterResumeRequest request
    ) {
        User user = requireAuthenticatedUser(currentUser);
        return persistenceHelper.approveAndSyncMasterResume(user.getId(), request);
    }

    private User requireAuthenticatedUser(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ResumeNotFoundException("Authenticated user not found");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResumeNotFoundException("Authenticated user no longer exists"));
    }

    private Resume findOwnedResume(String uuid, Long userId) {
        if (uuid == null || uuid.isBlank()) {
            throw new ResumeNotFoundException("Resume not found");
        }
        return resumeRepository.findByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found"));
    }

    private String validateTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            throw new InvalidResumeFileException("Resume title is required and must not be blank");
        }
        String trimmed = rawTitle.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new InvalidResumeFileException("Resume title must not exceed " + MAX_TITLE_LENGTH + " characters");
        }
        return trimmed;
    }

    private void deleteFileQuietly(String storageKey) {
        try {
            fileStorageService.deleteResumeFile(storageKey);
        } catch (RuntimeException cleanupEx) {
            log.warn("Failed to remove orphaned stored resume file key={}", storageKey, cleanupEx);
        }
    }

    private ResumeResponse toResponse(Resume resume) {
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
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }
}
