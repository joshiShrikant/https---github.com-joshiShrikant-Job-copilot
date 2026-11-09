package com.eurojobcopilot.modules.resume.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.resume.dto.GenerateTailoredResumeRequest;
import com.eurojobcopilot.modules.resume.dto.ResumeDownloadResource;
import com.eurojobcopilot.modules.resume.dto.ResumeExportFormat;
import com.eurojobcopilot.modules.resume.dto.StoredResumeFile;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeResponse;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeSummaryDto;
import com.eurojobcopilot.modules.resume.dto.UpdateTailoredResumeRequest;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import com.eurojobcopilot.modules.resume.exporter.DocxResumeExporter;
import com.eurojobcopilot.modules.resume.exporter.PdfResumeExporter;
import com.eurojobcopilot.modules.resume.parser.ExtractedResumeText;
import com.eurojobcopilot.modules.resume.parser.ResumeTextExtractor;
import com.eurojobcopilot.modules.resume.tailored.RawAiTailoredResumePayload;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumePersistenceHelper;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumePersistenceHelper.EditContextLoad;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumePersistenceHelper.EditSaveResult;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumePersistenceHelper.ExportFilePaths;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumePersistenceHelper.ExportSnapshot;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumePromptBuilder;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeValidator;
import com.eurojobcopilot.modules.resume.tailored.ValidatedTailoredResume;
import com.eurojobcopilot.modules.resume.template.ResumeExportTextFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TailoredResumeServiceImpl implements TailoredResumeService {

    private final TailoredResumePersistenceHelper persistenceHelper;
    private final FileStorageService fileStorageService;
    private final ResumeTextExtractor resumeTextExtractor;
    private final TailoredResumePromptBuilder promptBuilder;
    private final AiGatewayService aiGatewayService;
    private final TailoredResumeValidator validator;
    private final PdfResumeExporter pdfResumeExporter;
    private final DocxResumeExporter docxResumeExporter;

    @Override
    public TailoredResumeResponse generateTailoredResume(
            AuthenticatedUserPrincipal currentUser,
            GenerateTailoredResumeRequest request
    ) {
        Long userId = requireUserId(currentUser);
        if (request == null || request.getJobId() == null || request.getJobId() <= 0) {
            throw new BusinessValidationException("Target job ID must be a positive integer");
        }

        TailoredResumeContextSnapshot snapshot = persistenceHelper.loadGenerationSnapshot(userId, request);

        if (Boolean.FALSE.equals(request.getForceRefresh()) && snapshot.cachedDraft() != null) {
            log.debug("Returning cached tailored resume uuid={} version={} for userId={} jobId={}",
                    snapshot.cachedDraft().getUuid(),
                    snapshot.cachedDraft().getVersionNumber(),
                    userId,
                    request.getJobId());
            return snapshot.cachedDraft();
        }

        if (snapshot.masterResume() != null && snapshot.masterResume().parsedResume() == null) {
            snapshot = enrichWithRawMasterResumeText(snapshot);
        }

        if (!snapshot.hasCandidateEvidence()) {
            throw new BusinessValidationException(
                    "Candidate profile, skills, work history, and master resume contain no extractable evidence. "
                            + "Complete your profile or upload a readable master resume before generating a tailored CV."
            );
        }

        if (!snapshot.hasJobContent()) {
            throw new BusinessValidationException(
                    "Target job posting lacks description and structured requirements for resume tailoring."
            );
        }

        AiJsonPromptRequest promptRequest = promptBuilder.buildPromptRequest(snapshot);
        AiStructuredResponse<RawAiTailoredResumePayload> aiResponse = aiGatewayService.executeStructuredJsonPrompt(
                promptRequest,
                RawAiTailoredResumePayload.class
        );

        ValidatedTailoredResume validated = validator.validateAiDraft(aiResponse.payload(), snapshot);
        return persistenceHelper.saveGeneratedDraft(snapshot, validated, aiResponse);
    }

    @Override
    public List<TailoredResumeSummaryDto> listTailoredResumes(
            AuthenticatedUserPrincipal currentUser,
            Long jobId
    ) {
        Long userId = requireUserId(currentUser);
        if (jobId != null && jobId <= 0) {
            throw new BusinessValidationException("Job ID filter must be a positive integer");
        }
        return persistenceHelper.listTailoredResumes(userId, jobId);
    }

    @Override
    public TailoredResumeResponse getTailoredResume(
            AuthenticatedUserPrincipal currentUser,
            String uuid
    ) {
        Long userId = requireUserId(currentUser);
        return persistenceHelper.getTailoredResume(userId, uuid);
    }

    @Override
    public TailoredResumeResponse updateTailoredResume(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            UpdateTailoredResumeRequest request
    ) {
        Long userId = requireUserId(currentUser);
        if (request == null) {
            throw new BusinessValidationException("Updated tailored resume payload is required");
        }
        EditContextLoad loaded = persistenceHelper.loadEditContextSnapshot(userId, uuid);
        ValidatedTailoredResume revalidated = validator.revalidateEditedDraft(
                loaded.existingData(),
                request,
                loaded.snapshot()
        );
        EditSaveResult saved = persistenceHelper.saveEditedDraft(userId, uuid, request, revalidated);
        deleteStoredFilesQuietly(saved.invalidatedExports());
        return saved.response();
    }

    @Override
    public ResumeDownloadResource exportTailoredResume(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            ResumeExportFormat format,
            ResumeTemplate templateOverride,
            boolean regenerate
    ) {
        Long userId = requireUserId(currentUser);
        if (format == null) {
            throw new BusinessValidationException("Export format query parameter is required (supported: pdf, docx)");
        }

        ExportSnapshot snapshot = persistenceHelper.loadExportSnapshot(userId, uuid);
        ResumeTemplate effectiveTemplate = templateOverride != null
                ? templateOverride
                : (snapshot.templateName() != null ? snapshot.templateName() : ResumeTemplate.MODERN);
        boolean usingSavedTemplate = effectiveTemplate == snapshot.templateName();

        String downloadFilename = ResumeExportTextFormatter.buildDownloadFilename(
                snapshot.title(),
                snapshot.resumeData() != null ? snapshot.resumeData().getHeader() : null,
                effectiveTemplate,
                format
        );

        String cachedStorageKey = format == ResumeExportFormat.PDF
                ? snapshot.pdfFilePath()
                : snapshot.docxFilePath();

        if (!regenerate && usingSavedTemplate && cachedStorageKey != null && fileStorageService.resumeFileExists(cachedStorageKey)) {
            Resource cachedResource = fileStorageService.loadResumeAsResource(cachedStorageKey);
            long size = resolveResourceLength(cachedResource);
            log.debug("Serving cached {} export for tailored resume uuid={} template={}", format, snapshot.uuid(), effectiveTemplate);
            return new ResumeDownloadResource(cachedResource, downloadFilename, format.mimeType(), size);
        }

        byte[] renderedBytes = switch (format) {
            case PDF -> pdfResumeExporter.export(snapshot.resumeData(), effectiveTemplate, snapshot.title());
            case DOCX -> docxResumeExporter.export(snapshot.resumeData(), effectiveTemplate, snapshot.title());
        };

        if (usingSavedTemplate && snapshot.userUuid() != null && !snapshot.userUuid().isBlank()) {
            StoredResumeFile stored = fileStorageService.storeGeneratedResumeFile(
                    renderedBytes,
                    snapshot.userUuid(),
                    format,
                    downloadFilename
            );
            String previousKey = persistenceHelper.persistExportedFilePath(
                    userId,
                    snapshot.uuid(),
                    format,
                    stored.storageKey()
            );
            if (previousKey != null && !previousKey.equals(stored.storageKey())) {
                deleteStoredFileQuietly(previousKey);
            }
            return new ResumeDownloadResource(
                    new ByteArrayResource(renderedBytes),
                    stored.sanitizedFileName(),
                    format.mimeType(),
                    renderedBytes.length
            );
        }

        return new ResumeDownloadResource(
                new ByteArrayResource(renderedBytes),
                downloadFilename,
                format.mimeType(),
                renderedBytes.length
        );
    }

    @Override
    public void deleteTailoredResume(
            AuthenticatedUserPrincipal currentUser,
            String uuid
    ) {
        Long userId = requireUserId(currentUser);
        ExportFilePaths deletedPaths = persistenceHelper.deleteTailoredResume(userId, uuid);
        deleteStoredFilesQuietly(deletedPaths);
    }

    private void deleteStoredFilesQuietly(ExportFilePaths paths) {
        if (paths == null) {
            return;
        }
        deleteStoredFileQuietly(paths.pdfFilePath());
        deleteStoredFileQuietly(paths.docxFilePath());
    }

    private void deleteStoredFileQuietly(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            fileStorageService.deleteResumeFile(storageKey);
        } catch (RuntimeException ex) {
            log.warn("Could not delete stale exported resume file {}", storageKey, ex);
        }
    }

    private long resolveResourceLength(Resource resource) {
        try {
            return resource.contentLength();
        } catch (IOException ex) {
            return -1L;
        }
    }

    private TailoredResumeContextSnapshot enrichWithRawMasterResumeText(TailoredResumeContextSnapshot snapshot) {
        var mr = snapshot.masterResume();
        if (mr == null || mr.filePath() == null || mr.filePath().isBlank()) {
            return snapshot;
        }
        try {
            Resource resource = fileStorageService.loadResumeAsResource(mr.filePath());
            ExtractedResumeText extracted = resumeTextExtractor.extract(
                    resource,
                    mr.mimeType(),
                    mr.fileName()
            );
            if (extracted != null && extracted.text() != null && !extracted.text().isBlank()) {
                return snapshot.withRawMasterResumeText(extracted.text());
            }
            return snapshot;
        } catch (RuntimeException ex) {
            if (snapshot.hasCandidateEvidence()) {
                log.warn("Could not extract raw text fallback from unparsed master resume uuid={}; continuing with structured profile evidence",
                        mr.uuid(), ex);
                return snapshot;
            }
            throw ex;
        }
    }

    private Long requireUserId(AuthenticatedUserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return currentUser.getId();
    }
}

