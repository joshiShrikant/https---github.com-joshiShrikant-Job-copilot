package com.eurojobcopilot.modules.coverletter.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterDownloadResource;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterExportFormat;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterResponse;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterSummaryDto;
import com.eurojobcopilot.modules.coverletter.dto.GenerateCoverLetterRequest;
import com.eurojobcopilot.modules.coverletter.dto.UpdateCoverLetterRequest;
import com.eurojobcopilot.modules.coverletter.exporter.CoverLetterDocumentExporter;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterPersistenceHelper;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterPersistenceHelper.EditContextLoad;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterPersistenceHelper.ExportSnapshot;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterPromptBuilder;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterValidator;
import com.eurojobcopilot.modules.coverletter.generator.RawAiCoverLetterPayload;
import com.eurojobcopilot.modules.coverletter.generator.ValidatedCoverLetter;
import com.eurojobcopilot.modules.resume.parser.ExtractedResumeText;
import com.eurojobcopilot.modules.resume.parser.ResumeTextExtractor;
import com.eurojobcopilot.modules.resume.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverLetterServiceImpl implements CoverLetterService {

    private final CoverLetterPersistenceHelper persistenceHelper;
    private final FileStorageService fileStorageService;
    private final ResumeTextExtractor resumeTextExtractor;
    private final CoverLetterPromptBuilder promptBuilder;
    private final AiGatewayService aiGatewayService;
    private final CoverLetterValidator validator;
    private final CoverLetterDocumentExporter documentExporter;

    @Override
    public CoverLetterResponse generateCoverLetter(
            AuthenticatedUserPrincipal currentUser,
            GenerateCoverLetterRequest request
    ) {
        Long userId = requireUserId(currentUser);
        if (request == null || request.getJobId() == null || request.getJobId() <= 0) {
            throw new BusinessValidationException("Target job ID must be a positive integer");
        }

        CoverLetterContextSnapshot snapshot = persistenceHelper.loadGenerationSnapshot(userId, request);

        if (Boolean.FALSE.equals(request.getForceRefresh()) && snapshot.cachedDraft() != null) {
            log.debug("Returning cached cover letter uuid={} version={} for userId={} jobId={}",
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
                    "Candidate profile, skills, work history, tailored resume, and master resume contain no extractable evidence. "
                            + "Complete your profile or upload a readable master resume before generating a cover letter."
            );
        }

        if (!snapshot.hasJobContent()) {
            throw new BusinessValidationException(
                    "Target job posting lacks description and structured requirements for cover letter generation."
            );
        }

        AiJsonPromptRequest promptRequest = promptBuilder.buildPromptRequest(snapshot);
        AiStructuredResponse<RawAiCoverLetterPayload> aiResponse = aiGatewayService.executeStructuredJsonPrompt(
                promptRequest,
                RawAiCoverLetterPayload.class
        );

        ValidatedCoverLetter validated = validator.validateAiDraft(aiResponse.payload(), snapshot);
        return persistenceHelper.saveGeneratedDraft(snapshot, validated, aiResponse);
    }

    @Override
    public List<CoverLetterSummaryDto> listCoverLetters(
            AuthenticatedUserPrincipal currentUser,
            Long jobId
    ) {
        Long userId = requireUserId(currentUser);
        if (jobId != null && jobId <= 0) {
            throw new BusinessValidationException("Job ID filter must be a positive integer");
        }
        return persistenceHelper.listCoverLetters(userId, jobId);
    }

    @Override
    public CoverLetterResponse getCoverLetter(
            AuthenticatedUserPrincipal currentUser,
            String uuid
    ) {
        Long userId = requireUserId(currentUser);
        return persistenceHelper.getCoverLetter(userId, uuid);
    }

    @Override
    public CoverLetterResponse updateCoverLetter(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            UpdateCoverLetterRequest request
    ) {
        Long userId = requireUserId(currentUser);
        if (request == null) {
            throw new BusinessValidationException("Updated cover letter payload is required");
        }
        EditContextLoad loaded = persistenceHelper.loadEditContextSnapshot(userId, uuid);
        ValidatedCoverLetter revalidated = validator.revalidateEditedDraft(
                loaded.existingEntity(),
                loaded.existingMetadata(),
                request,
                loaded.snapshot()
        );
        return persistenceHelper.saveEditedDraft(userId, uuid, request, revalidated);
    }

    @Override
    public CoverLetterDownloadResource exportCoverLetter(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            CoverLetterExportFormat format
    ) {
        Long userId = requireUserId(currentUser);
        if (format == null) {
            throw new BusinessValidationException("Export format query parameter is required (supported: pdf, docx, txt)");
        }
        ExportSnapshot snapshot = persistenceHelper.loadExportSnapshot(userId, uuid);
        byte[] renderedBytes = documentExporter.export(snapshot, format);
        String downloadFilename = documentExporter.buildDownloadFilename(snapshot, format);
        return new CoverLetterDownloadResource(
                new ByteArrayResource(renderedBytes),
                downloadFilename,
                format.mimeType(),
                renderedBytes.length
        );
    }

    @Override
    public void deleteCoverLetter(
            AuthenticatedUserPrincipal currentUser,
            String uuid
    ) {
        Long userId = requireUserId(currentUser);
        persistenceHelper.deleteCoverLetter(userId, uuid);
    }

    private CoverLetterContextSnapshot enrichWithRawMasterResumeText(CoverLetterContextSnapshot snapshot) {
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

