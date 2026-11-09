package com.eurojobcopilot.modules.assistant.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot.ApplicationEvidence;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot.CandidateEvidence;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot.ConversationTurnSummary;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot.MatchEvidence;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantContextSnapshot.SponsorshipEvidence;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantPersistenceHelper;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantPromptBuilder;
import com.eurojobcopilot.modules.assistant.ai.JobAssistantValidator;
import com.eurojobcopilot.modules.assistant.ai.RawAiAssistantPayload;
import com.eurojobcopilot.modules.assistant.ai.ValidatedAssistantPayload;
import com.eurojobcopilot.modules.assistant.dto.JobAssistantMessageResponse;
import com.eurojobcopilot.modules.assistant.dto.JobAssistantRespondRequest;
import com.eurojobcopilot.modules.assistant.dto.UpdateJobAssistantMessageRequest;
import com.eurojobcopilot.modules.assistant.entity.AssistantActionType;
import com.eurojobcopilot.modules.assistant.entity.JobAssistantMessage;
import com.eurojobcopilot.modules.assistant.repository.JobAssistantMessageRepository;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobAiAnalysisDto;
import com.eurojobcopilot.modules.job.dto.JobApplicationSummaryDto;
import com.eurojobcopilot.modules.job.dto.JobDetailDto;
import com.eurojobcopilot.modules.job.dto.JobMatchBreakdownDto;
import com.eurojobcopilot.modules.job.dto.SponsorshipBreakdownDto;
import com.eurojobcopilot.modules.job.dto.SponsorshipRegistryRecordDto;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.service.JobQueryService;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.user.entity.UserEducation;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobAssistantServiceImpl implements JobAssistantService {

    private static final int RECENT_TURNS_LIMIT = 5;

    private final JobAssistantMessageRepository messageRepository;
    private final JobAssistantPersistenceHelper persistenceHelper;
    private final JobAssistantPromptBuilder promptBuilder;
    private final JobAssistantValidator validator;
    private final JobQueryService jobQueryService;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserWorkExperienceRepository workExperienceRepository;
    private final UserEducationRepository educationRepository;
    private final UserJobPreferenceRepository preferenceRepository;
    private final ResumeRepository resumeRepository;
    private final AiGatewayService aiGatewayService;

    @Override
    @Transactional(readOnly = true)
    public List<JobAssistantMessageResponse> getHistory(AuthenticatedUserPrincipal currentUser, Long jobId) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(jobId, "Job ID must be a positive integer");
        if (!jobRepository.existsById(jobId)) {
            throw new ResourceNotFoundException("Job not found with id: " + jobId);
        }
        return messageRepository.findAllByUserIdAndJobIdOrderByCreatedAtAscIdAsc(userId, jobId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public JobAssistantMessageResponse respond(
            AuthenticatedUserPrincipal currentUser,
            Long jobId,
            JobAssistantRespondRequest request
    ) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(jobId, "Job ID must be a positive integer");
        if (request == null || request.getActionType() == null) {
            throw new BusinessValidationException("Assistant actionType is required");
        }

        AssistantActionType actionType = request.getActionType();
        String trimmedPrompt = request.getUserPrompt() != null ? request.getUserPrompt().trim() : null;
        if (actionType == AssistantActionType.CUSTOM_QUESTION && (trimmedPrompt == null || trimmedPrompt.isBlank())) {
            throw new BusinessValidationException("Please enter a custom question for CUSTOM_QUESTION requests.");
        }

        JobDetailDto jobDetail = jobQueryService.getJobDetail(currentUser, jobId);
        JobAssistantContextSnapshot snapshot = buildContextSnapshot(
                userId,
                jobId,
                actionType,
                trimmedPrompt,
                request.getAdditionalContext(),
                jobDetail
        );

        AiJsonPromptRequest promptRequest = promptBuilder.buildPromptRequest(snapshot);
        AiStructuredResponse<RawAiAssistantPayload> aiResponse =
                aiGatewayService.executeStructuredJsonPrompt(promptRequest, RawAiAssistantPayload.class);

        ValidatedAssistantPayload validated = validator.validateAndNormalize(aiResponse.payload(), actionType);
        JobAssistantMessage saved = persistenceHelper.saveAssistantTurn(
                userId,
                jobId,
                actionType,
                trimmedPrompt,
                validated,
                aiResponse.provider(),
                aiResponse.model()
        );

        log.info("Generated contextual assistant response userId={} jobId={} action={} provider={} model={}",
                userId, jobId, actionType, aiResponse.provider(), aiResponse.model());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public JobAssistantMessageResponse updateEditableResponse(
            AuthenticatedUserPrincipal currentUser,
            Long jobId,
            Long messageId,
            UpdateJobAssistantMessageRequest request
    ) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(jobId, "Job ID must be a positive integer");
        validatePositiveId(messageId, "Message ID must be a positive integer");
        if (request == null || request.getEditableContent() == null || request.getEditableContent().isBlank()) {
            throw new BusinessValidationException("Edited response content must not be blank");
        }

        JobAssistantMessage message = messageRepository.findByIdAndUserIdAndJobId(messageId, userId, jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Assistant message not found with id: " + messageId));

        message.setEditableContent(request.getEditableContent().trim());
        message.setEditedByUser(true);
        JobAssistantMessage updated = messageRepository.saveAndFlush(message);
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteMessage(AuthenticatedUserPrincipal currentUser, Long jobId, Long messageId) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(jobId, "Job ID must be a positive integer");
        validatePositiveId(messageId, "Message ID must be a positive integer");

        JobAssistantMessage message = messageRepository.findByIdAndUserIdAndJobId(messageId, userId, jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Assistant message not found with id: " + messageId));

        messageRepository.delete(message);
    }

    private JobAssistantContextSnapshot buildContextSnapshot(
            Long userId,
            Long jobId,
            AssistantActionType actionType,
            String userPrompt,
            String additionalContext,
            JobDetailDto jobDetail
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        UserJobPreference preference = preferenceRepository.findByUserId(userId).orElse(null);
        List<UserSkill> skills = userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(userId);
        List<UserWorkExperience> experiences = workExperienceRepository.findAllByUserIdOrderByStartDateDesc(userId);
        List<UserEducation> educations = educationRepository.findAllByUserIdOrderByStartDateDesc(userId);
        Resume masterResume = resumeRepository.findFirstByUserIdAndMasterApprovedTrueOrderByVersionNumberDesc(userId)
                .orElse(null);

        String fullName = ((user.getFirstName() != null ? user.getFirstName().trim() : "") + " "
                + (user.getLastName() != null ? user.getLastName().trim() : "")).trim();
        if (fullName.isEmpty()) {
            fullName = user.getEmail();
        }

        String currentLocation = null;
        if (profile != null) {
            if (hasText(profile.getCurrentCity()) && hasText(profile.getCurrentCountry())) {
                currentLocation = profile.getCurrentCity().trim() + ", " + profile.getCurrentCountry().trim();
            } else if (hasText(profile.getCurrentCountry())) {
                currentLocation = profile.getCurrentCountry().trim();
            } else if (hasText(profile.getCurrentCity())) {
                currentLocation = profile.getCurrentCity().trim();
            }
        }

        List<String> topSkills = skills.stream()
                .limit(20)
                .map(s -> s.getSkillName() + " (" + s.getYearsExperience() + "y)")
                .toList();

        List<String> experienceSummaries = experiences.stream()
                .limit(6)
                .map(exp -> exp.getDesignation() + " at " + exp.getCompanyName()
                        + " (" + exp.getStartDate() + " - " + (exp.isCurrent() || exp.getEndDate() == null ? "Present" : exp.getEndDate()) + ")"
                        + (hasText(exp.getDescription()) ? ": " + truncate(exp.getDescription(), 260) : ""))
                .toList();

        List<String> educationSummaries = educations.stream()
                .limit(4)
                .map(edu -> edu.getDegree() + " in " + edu.getFieldOfStudy() + " at " + edu.getInstitution()
                        + " (" + edu.getCountry() + ")")
                .toList();

        String masterTitle = masterResume != null ? masterResume.getTitle() : null;
        String masterSummary = extractMasterResumeSummary(masterResume);

        CandidateEvidence candidateEvidence = new CandidateEvidence(
                fullName,
                profile != null ? profile.getCurrentDesignation() : null,
                profile != null ? profile.getYearsOfExperience() : null,
                currentLocation,
                profile != null && profile.getWorkAuthorizationStatus() != null ? profile.getWorkAuthorizationStatus().name() : null,
                profile != null && profile.getRelocationPreference() != null ? profile.getRelocationPreference().name() : null,
                profile != null ? profile.getNoticePeriodDays() : null,
                profile != null ? profile.getExpectedSalaryMin() : null,
                profile != null ? profile.getExpectedSalaryCurrency() : null,
                preference == null || preference.isRequiresSponsorship(),
                preference == null || preference.isRequiresRelocation(),
                topSkills,
                experienceSummaries,
                educationSummaries,
                masterTitle,
                masterSummary
        );

        JobAiAnalysisDto ai = jobDetail.getAiAnalysis();
        JobEvidence jobEvidence = new JobEvidence(
                jobDetail.getTitle(),
                jobDetail.getCompanyName(),
                jobDetail.getCountry(),
                jobDetail.getCity(),
                jobDetail.getWorkArrangement() != null ? jobDetail.getWorkArrangement().name() : null,
                jobDetail.getEmploymentType() != null ? jobDetail.getEmploymentType().name() : null,
                jobDetail.getExperienceLevel() != null ? jobDetail.getExperienceLevel().name() : null,
                jobDetail.getSalaryMin(),
                jobDetail.getSalaryMax(),
                jobDetail.getSalaryCurrency(),
                jobDetail.getSalaryPeriod() != null ? jobDetail.getSalaryPeriod().name() : null,
                jobDetail.getSponsorStatus() != null ? jobDetail.getSponsorStatus().name() : null,
                jobDetail.getSkills(),
                jobDetail.getDescription(),
                ai != null ? ai.getSummary() : null,
                ai != null ? ai.getRequiredSkills() : List.of(),
                ai != null ? ai.getNiceToHaveSkills() : List.of(),
                ai != null ? ai.getResponsibilities() : List.of(),
                ai != null ? ai.getLanguageRequirements() : List.of()
        );

        SponsorshipBreakdownDto sp = jobDetail.getSponsorshipBreakdown();
        SponsorshipEvidence sponsorshipEvidence = sp == null ? null : new SponsorshipEvidence(
                sp.getDetailedStatus() != null ? sp.getDetailedStatus().name() : null,
                sp.getConfidenceLevel() != null ? sp.getConfidenceLevel().name() : null,
                sp.getSummary(),
                sp.getCountryGuideSummary(),
                sp.getVacancyCaveat(),
                sp.getEvidenceSignals(),
                sp.getRegistryRecords() != null
                        ? sp.getRegistryRecords().stream()
                        .map(SponsorshipRegistryRecordDto::getOfficialRegisteredName)
                        .filter(this::hasText)
                        .toList()
                        : List.of()
        );

        JobMatchBreakdownDto match = jobDetail.getMatchBreakdown();
        MatchEvidence matchEvidence = match == null ? null : new MatchEvidence(
                match.getMatchPercentage(),
                match.getDeterministicScore(),
                match.getAiScore(),
                match.getExperienceCompatibility(),
                match.getMatchingSkills(),
                match.getMissingSkills(),
                match.getAiMissingSkills(),
                match.getAiPotentialConcerns(),
                match.getRecommendationReasons()
        );

        JobApplicationSummaryDto app = jobDetail.getApplication();
        ApplicationEvidence appEvidence = app == null ? null : new ApplicationEvidence(
                app.getStatus() != null ? app.getStatus().name() : null,
                formatInstant(app.getAppliedDate()),
                formatInstant(app.getNextInterviewDate()),
                app.getNextFollowUpDate() != null ? app.getNextFollowUpDate().toString() : null
        );

        List<JobAssistantMessage> allTurns = messageRepository.findAllByUserIdAndJobIdOrderByCreatedAtAscIdAsc(userId, jobId);
        int fromIdx = Math.max(0, allTurns.size() - RECENT_TURNS_LIMIT);
        List<ConversationTurnSummary> recentConversation = allTurns.subList(fromIdx, allTurns.size()).stream()
                .map(turn -> new ConversationTurnSummary(
                        turn.getActionType().getLabel(),
                        turn.getUserPrompt(),
                        turn.getAssistantTitle(),
                        turn.getEditableContent()
                ))
                .toList();

        return new JobAssistantContextSnapshot(
                userId,
                jobId,
                actionType,
                userPrompt,
                additionalContext,
                candidateEvidence,
                jobEvidence,
                sponsorshipEvidence,
                matchEvidence,
                appEvidence,
                recentConversation
        );
    }

    private String extractMasterResumeSummary(Resume masterResume) {
        if (masterResume == null || masterResume.getParsedContentJson() == null) {
            return null;
        }
        Map<String, Object> parsed = masterResume.getParsedContentJson();
        Object profileObj = parsed.get("profile");
        if (profileObj instanceof Map<?, ?> profileMap) {
            Object summaryObj = profileMap.get("summary");
            if (summaryObj instanceof String summaryText && !summaryText.isBlank()) {
                return summaryText.trim();
            }
        }
        Object directSummary = parsed.get("summary");
        if (directSummary instanceof String summaryText && !summaryText.isBlank()) {
            return summaryText.trim();
        }
        return null;
    }

    private JobAssistantMessageResponse toResponse(JobAssistantMessage entity) {
        return JobAssistantMessageResponse.builder()
                .id(entity.getId())
                .uuid(entity.getUuid())
                .jobId(entity.getJob().getId())
                .actionType(entity.getActionType())
                .actionLabel(entity.getActionType().getLabel())
                .userPrompt(entity.getUserPrompt())
                .assistantTitle(entity.getAssistantTitle())
                .assistantContent(entity.getAssistantContent())
                .editableContent(entity.getEditableContent())
                .highlights(entity.getHighlights() != null ? List.copyOf(entity.getHighlights()) : List.of())
                .editedByUser(entity.isEditedByUser())
                .aiGenerated(entity.isAiGenerated())
                .aiProvider(entity.getAiProvider())
                .aiModelUsed(entity.getAiModelUsed())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private Long requireUserId(AuthenticatedUserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return currentUser.getId();
    }

    private void validatePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BusinessValidationException(message);
        }
    }

    private String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String clean = text.trim().replaceAll("\\s+", " ");
        return clean.length() <= max ? clean : clean.substring(0, max - 3) + "...";
    }

    private boolean hasText(String val) {
        return val != null && !val.isBlank();
    }
}

