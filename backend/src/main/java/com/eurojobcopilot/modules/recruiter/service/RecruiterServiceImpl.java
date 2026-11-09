package com.eurojobcopilot.modules.recruiter.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.recruiter.ai.RawAiOutreachPayload;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachPersistenceHelper;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachPromptBuilder;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachValidator;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachValidator.ValidatedOutreachDraft;
import com.eurojobcopilot.modules.recruiter.dto.CreateRecruiterInteractionRequest;
import com.eurojobcopilot.modules.recruiter.dto.CreateRecruiterRequest;
import com.eurojobcopilot.modules.recruiter.dto.FollowUpReminderItemDto;
import com.eurojobcopilot.modules.recruiter.dto.FollowUpRemindersResponse;
import com.eurojobcopilot.modules.recruiter.dto.GenerateOutreachRequest;
import com.eurojobcopilot.modules.recruiter.dto.OutreachDraftResponse;
import com.eurojobcopilot.modules.recruiter.dto.PagedRecruitersResponse;
import com.eurojobcopilot.modules.recruiter.dto.RecruiterInteractionAction;
import com.eurojobcopilot.modules.recruiter.dto.RecruiterInteractionDto;
import com.eurojobcopilot.modules.recruiter.dto.RecruiterResponse;
import com.eurojobcopilot.modules.recruiter.dto.UpdateRecruiterInteractionRequest;
import com.eurojobcopilot.modules.recruiter.dto.UpdateRecruiterRequest;
import com.eurojobcopilot.modules.recruiter.entity.Recruiter;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterInteraction;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageStatus;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterInteractionRepository;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecruiterServiceImpl implements RecruiterService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RecruiterRepository recruiterRepository;
    private final RecruiterInteractionRepository interactionRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RecruiterOutreachPersistenceHelper outreachPersistenceHelper;
    private final RecruiterOutreachPromptBuilder promptBuilder;
    private final RecruiterOutreachValidator outreachValidator;
    private final AiGatewayService aiGatewayService;

    @Override
    @Transactional(readOnly = true)
    public PagedRecruitersResponse listRecruiters(
            AuthenticatedUserPrincipal currentUser,
            String query,
            Long companyId,
            int page,
            int size
    ) {
        Long userId = requireUserId(currentUser);
        if (companyId != null && companyId <= 0) {
            throw new BusinessValidationException("Company ID filter must be a positive integer");
        }
        int safePage = Math.max(0, page);
        int requestedSize = size > 0 ? size : DEFAULT_PAGE_SIZE;
        int safeSize = Math.min(MAX_PAGE_SIZE, requestedSize);

        String trimmedQuery = trimToNull(query);
        Page<Recruiter> recruiterPage = recruiterRepository.searchByUserId(
                userId,
                companyId,
                trimmedQuery,
                PageRequest.of(safePage, safeSize)
        );

        List<Recruiter> recruiters = recruiterPage.getContent();
        if (recruiters.isEmpty()) {
            return PagedRecruitersResponse.builder()
                    .content(List.of())
                    .pageNumber(recruiterPage.getNumber())
                    .pageSize(recruiterPage.getSize())
                    .totalElements(recruiterPage.getTotalElements())
                    .totalPages(recruiterPage.getTotalPages())
                    .first(recruiterPage.isFirst())
                    .last(recruiterPage.isLast())
                    .hasNext(recruiterPage.hasNext())
                    .hasPrevious(recruiterPage.hasPrevious())
                    .build();
        }

        List<Long> recruiterIds = recruiters.stream().map(Recruiter::getId).toList();
        List<RecruiterInteraction> allInteractions = interactionRepository
                .findAllWithFollowUpReminderByUserId(userId);
        Map<Long, List<RecruiterInteraction>> remindersByRecruiter = allInteractions.stream()
                .filter(i -> i.getRecruiter() != null && recruiterIds.contains(i.getRecruiter().getId()))
                .collect(Collectors.groupingBy(i -> i.getRecruiter().getId()));

        Map<Long, Long> countsByRecruiterId = interactionRepository
                .countGroupedByRecruiterIdIn(recruiterIds)
                .stream()
                .filter(row -> row != null && row.length >= 2 && row[0] instanceof Long && row[1] instanceof Number)
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a
                ));

        List<RecruiterResponse> summaries = recruiters.stream()
                .map(r -> {
                    List<RecruiterInteraction> reminderList = remindersByRecruiter.getOrDefault(r.getId(), List.of());
                    return toSummaryResponse(r, countsByRecruiterId.getOrDefault(r.getId(), 0L), reminderList);
                })
                .toList();

        return PagedRecruitersResponse.builder()
                .content(summaries)
                .pageNumber(recruiterPage.getNumber())
                .pageSize(recruiterPage.getSize())
                .totalElements(recruiterPage.getTotalElements())
                .totalPages(recruiterPage.getTotalPages())
                .first(recruiterPage.isFirst())
                .last(recruiterPage.isLast())
                .hasNext(recruiterPage.hasNext())
                .hasPrevious(recruiterPage.hasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RecruiterResponse getRecruiter(AuthenticatedUserPrincipal currentUser, Long recruiterId) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(recruiterId, "Recruiter ID");

        Recruiter recruiter = recruiterRepository.findWithCompanyByIdAndUserId(recruiterId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter contact not found"));

        List<RecruiterInteraction> interactions = interactionRepository.findAllWithDetailsByRecruiterId(recruiterId);
        return toDetailResponse(recruiter, interactions);
    }

    @Override
    @Transactional
    public RecruiterResponse createRecruiter(
            AuthenticatedUserPrincipal currentUser,
            CreateRecruiterRequest request
    ) {
        User user = requireAuthenticatedUser(currentUser);
        String validatedLinkedin = validateOptionalHttpUrl(request.getLinkedinUrl());

        Company company = null;
        if (request.getCompanyId() != null) {
            company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + request.getCompanyId()));
        }

        String companyName = trimToNull(request.getCompanyName());
        if (companyName == null && company != null) {
            companyName = company.getName();
        }

        Recruiter recruiter = Recruiter.builder()
                .user(user)
                .company(company)
                .fullName(request.getFullName().trim())
                .roleTitle(trimToNull(request.getRoleTitle()))
                .companyName(companyName)
                .email(trimToNull(request.getEmail()))
                .linkedinUrl(validatedLinkedin)
                .phone(trimToNull(request.getPhone()))
                .notes(trimToNull(request.getNotes()))
                .build();

        recruiter = recruiterRepository.saveAndFlush(recruiter);
        log.info("Created recruiter id={} userId={}", recruiter.getId(), user.getId());
        return toDetailResponse(recruiter, List.of());
    }

    @Override
    @Transactional
    public RecruiterResponse updateRecruiter(
            AuthenticatedUserPrincipal currentUser,
            Long recruiterId,
            UpdateRecruiterRequest request
    ) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(recruiterId, "Recruiter ID");

        Recruiter recruiter = recruiterRepository.findWithCompanyByIdAndUserId(recruiterId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter contact not found"));

        String validatedLinkedin = validateOptionalHttpUrl(request.getLinkedinUrl());

        if (request.isClearCompany()) {
            recruiter.setCompany(null);
        } else if (request.getCompanyId() != null) {
            Company company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + request.getCompanyId()));
            recruiter.setCompany(company);
        }

        String resolvedCompanyName = trimToNull(request.getCompanyName());
        if (resolvedCompanyName == null && recruiter.getCompany() != null && !request.isClearCompany()) {
            resolvedCompanyName = recruiter.getCompany().getName();
        }

        recruiter.setFullName(request.getFullName().trim());
        recruiter.setRoleTitle(trimToNull(request.getRoleTitle()));
        recruiter.setCompanyName(resolvedCompanyName);
        recruiter.setEmail(trimToNull(request.getEmail()));
        recruiter.setLinkedinUrl(validatedLinkedin);
        recruiter.setPhone(trimToNull(request.getPhone()));
        recruiter.setNotes(trimToNull(request.getNotes()));

        recruiter = recruiterRepository.saveAndFlush(recruiter);
        List<RecruiterInteraction> interactions = interactionRepository.findAllWithDetailsByRecruiterId(recruiterId);
        return toDetailResponse(recruiter, interactions);
    }

    @Override
    @Transactional
    public void deleteRecruiter(AuthenticatedUserPrincipal currentUser, Long recruiterId) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(recruiterId, "Recruiter ID");

        Recruiter recruiter = recruiterRepository.findWithCompanyByIdAndUserId(recruiterId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter contact not found"));

        interactionRepository.deleteAllByRecruiterId(recruiter.getId());
        recruiterRepository.delete(recruiter);
        recruiterRepository.flush();
        log.info("Deleted recruiter id={} userId={}", recruiterId, userId);
    }

    @Override
    public OutreachDraftResponse generateOutreach(
            AuthenticatedUserPrincipal currentUser,
            Long recruiterId,
            GenerateOutreachRequest request
    ) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(recruiterId, "Recruiter ID");
        GenerateOutreachRequest effectiveRequest = request != null ? request : new GenerateOutreachRequest();

        RecruiterOutreachContextSnapshot snapshot = outreachPersistenceHelper.loadContextSnapshot(
                userId, recruiterId, effectiveRequest
        );

        AiJsonPromptRequest promptRequest = promptBuilder.buildPromptRequest(snapshot);
        AiStructuredResponse<RawAiOutreachPayload> aiResponse = aiGatewayService.executeStructuredJsonPrompt(
                promptRequest,
                RawAiOutreachPayload.class
        );

        ValidatedOutreachDraft validatedDraft = outreachValidator.validateAndRefine(aiResponse.payload(), snapshot);

        RecruiterInteractionDto savedDraft = outreachPersistenceHelper.saveAiOutreachDraft(
                userId,
                recruiterId,
                snapshot,
                validatedDraft,
                aiResponse,
                effectiveRequest.getFollowUpReminderDate()
        );

        log.info("Generated editable unsent AI outreach draft id={} recruiterId={} type={} userId={}",
                savedDraft.getId(), recruiterId, snapshot.messageType(), userId);

        return OutreachDraftResponse.builder()
                .interaction(savedDraft)
                .keyTalkingPoints(validatedDraft.keyTalkingPoints())
                .suggestedFollowUpDays(validatedDraft.suggestedFollowUpDays())
                .warnings(validatedDraft.warnings())
                .requiresManualApproval(true)
                .autoSent(false)
                .build();
    }

    @Override
    @Transactional
    public RecruiterInteractionDto createInteraction(
            AuthenticatedUserPrincipal currentUser,
            Long recruiterId,
            CreateRecruiterInteractionRequest request
    ) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(recruiterId, "Recruiter ID");

        if (request.isMarkAsSent() && !request.isExplicitApprovalConfirmed()) {
            throw new BusinessValidationException(
                    "Explicit user confirmation is required before marking a recruiter communication as sent."
            );
        }

        Recruiter recruiter = recruiterRepository.findWithCompanyByIdAndUserId(recruiterId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter contact not found"));

        JobApplication linkedApp = resolveOwnedApplication(userId, request.getApplicationUuid());

        Instant now = Instant.now();
        RecruiterMessageStatus status = request.isMarkAsSent()
                ? RecruiterMessageStatus.SENT
                : RecruiterMessageStatus.DRAFT;

        RecruiterInteraction entity = RecruiterInteraction.builder()
                .recruiter(recruiter)
                .application(linkedApp)
                .channel(request.getChannel())
                .messageType(request.getMessageType())
                .status(status)
                .subjectLine(trimToNull(request.getSubjectLine()))
                .messageBody(request.getMessageBody().trim())
                .approvedAt(request.isMarkAsSent() ? now : null)
                .sentAt(request.isMarkAsSent() ? (request.getSentAt() != null ? request.getSentAt() : now) : null)
                .followUpReminderDate(request.getFollowUpReminderDate())
                .reminderCompleted(false)
                .aiGenerated(false)
                .editedByUser(true)
                .build();

        entity = interactionRepository.saveAndFlush(entity);
        return outreachPersistenceHelper.toDto(entity);
    }

    @Override
    @Transactional
    public RecruiterInteractionDto updateInteraction(
            AuthenticatedUserPrincipal currentUser,
            Long recruiterId,
            Long interactionId,
            UpdateRecruiterInteractionRequest request
    ) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(recruiterId, "Recruiter ID");
        validatePositiveId(interactionId, "Interaction ID");

        RecruiterInteraction entity = interactionRepository
                .findWithDetailsByIdAndRecruiterIdAndUserId(interactionId, recruiterId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter interaction not found"));

        if (request.isClearApplication()) {
            entity.setApplication(null);
        } else if (hasText(request.getApplicationUuid())) {
            entity.setApplication(resolveOwnedApplication(userId, request.getApplicationUuid()));
        }

        if (request.getChannel() != null) {
            entity.setChannel(request.getChannel());
        }
        if (request.getMessageType() != null) {
            entity.setMessageType(request.getMessageType());
        }

        boolean contentChanged = false;
        if (request.getSubjectLine() != null) {
            String nextSubject = trimToNull(request.getSubjectLine());
            if (!Objects.equals(nextSubject, entity.getSubjectLine())) {
                entity.setSubjectLine(nextSubject);
                contentChanged = true;
            }
        }
        if (request.getMessageBody() != null) {
            String nextBody = request.getMessageBody().trim();
            if (nextBody.isEmpty()) {
                throw new BusinessValidationException("Message body cannot be empty");
            }
            if (!Objects.equals(nextBody, entity.getMessageBody())) {
                entity.setMessageBody(nextBody);
                contentChanged = true;
            }
        }
        if (contentChanged) {
            entity.setEditedByUser(true);
        }

        if (request.isClearFollowUpReminderDate()) {
            entity.setFollowUpReminderDate(null);
            entity.setReminderCompleted(false);
        } else if (request.getFollowUpReminderDate() != null) {
            boolean dateChanged = !Objects.equals(entity.getFollowUpReminderDate(), request.getFollowUpReminderDate());
            entity.setFollowUpReminderDate(request.getFollowUpReminderDate());
            if (dateChanged && request.getReminderCompleted() == null) {
                entity.setReminderCompleted(false);
            }
        }

        if (request.getReminderCompleted() != null) {
            entity.setReminderCompleted(request.getReminderCompleted());
        }

        RecruiterInteractionAction action = request.getAction() != null
                ? request.getAction()
                : RecruiterInteractionAction.SAVE_DRAFT;

        Instant now = Instant.now();
        switch (action) {
            case SAVE_DRAFT -> {
                // Deliberately leaves sentAt untouched and never auto-sends.
            }
            case APPROVE -> {
                if (entity.getStatus() != RecruiterMessageStatus.SENT) {
                    entity.setStatus(RecruiterMessageStatus.APPROVED);
                    if (entity.getApprovedAt() == null) {
                        entity.setApprovedAt(now);
                    }
                }
            }
            case MARK_SENT -> {
                if (!request.isExplicitApprovalConfirmed()) {
                    throw new BusinessValidationException(
                            "Explicit user approval (explicitApprovalConfirmed=true) is required before marking a message as sent."
                    );
                }
                entity.setStatus(RecruiterMessageStatus.SENT);
                if (entity.getApprovedAt() == null) {
                    entity.setApprovedAt(now);
                }
                entity.setSentAt(request.getSentAt() != null ? request.getSentAt() : now);
            }
        }

        entity = interactionRepository.saveAndFlush(entity);
        return outreachPersistenceHelper.toDto(entity);
    }

    @Override
    @Transactional
    public void deleteInteraction(
            AuthenticatedUserPrincipal currentUser,
            Long recruiterId,
            Long interactionId
    ) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(recruiterId, "Recruiter ID");
        validatePositiveId(interactionId, "Interaction ID");

        RecruiterInteraction entity = interactionRepository
                .findWithDetailsByIdAndRecruiterIdAndUserId(interactionId, recruiterId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter interaction not found"));

        interactionRepository.delete(entity);
        interactionRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public FollowUpRemindersResponse getFollowUpReminders(
            AuthenticatedUserPrincipal currentUser,
            boolean includeCompleted
    ) {
        Long userId = requireUserId(currentUser);
        LocalDate today = LocalDate.now();
        List<FollowUpReminderItemDto> items = new ArrayList<>();

        List<RecruiterInteraction> interactions = interactionRepository.findAllWithFollowUpReminderByUserId(userId);
        for (RecruiterInteraction i : interactions) {
            if (!includeCompleted && i.isReminderCompleted()) {
                continue;
            }
            String urgency = resolveUrgency(i.getFollowUpReminderDate(), i.isReminderCompleted(), today);
            JobApplication app = i.getApplication();
            items.add(FollowUpReminderItemDto.builder()
                    .sourceType("RECRUITER_INTERACTION")
                    .urgency(urgency)
                    .reminderDate(i.getFollowUpReminderDate())
                    .completed(i.isReminderCompleted())
                    .applicationUuid(app != null ? app.getUuid() : null)
                    .applicationStatus(app != null ? app.getStatus() : null)
                    .recruiterId(i.getRecruiter().getId())
                    .interactionId(i.getId())
                    .contactLabel(i.getRecruiter().getFullName()
                            + (hasText(i.getRecruiter().getCompanyName()) ? " · " + i.getRecruiter().getCompanyName() : ""))
                    .roleTitle(app != null && app.getJob() != null ? app.getJob().getTitle() : i.getRecruiter().getRoleTitle())
                    .channel(i.getChannel())
                    .messageType(i.getMessageType())
                    .messageStatus(i.getStatus())
                    .build());
        }

        List<JobApplication> appsWithReminders = jobApplicationRepository.findAllWithFollowUpReminderByUserId(userId);
        for (JobApplication app : appsWithReminders) {
            if (!includeCompleted && app.isFollowUpCompleted()) {
                continue;
            }
            String urgency = resolveUrgency(app.getNextFollowUpDate(), app.isFollowUpCompleted(), today);
            items.add(FollowUpReminderItemDto.builder()
                    .sourceType("APPLICATION")
                    .urgency(urgency)
                    .reminderDate(app.getNextFollowUpDate())
                    .completed(app.isFollowUpCompleted())
                    .applicationUuid(app.getUuid())
                    .applicationStatus(app.getStatus())
                    .contactLabel(app.getJob().getCompanyName())
                    .roleTitle(app.getJob().getTitle())
                    .build());
        }

        items.sort(Comparator
                .comparingInt((FollowUpReminderItemDto item) -> urgencySortRank(item.getUrgency()))
                .thenComparing(FollowUpReminderItemDto::getReminderDate));

        long overdueCount = items.stream().filter(i -> !i.isCompleted() && "OVERDUE".equals(i.getUrgency())).count();
        long dueTodayCount = items.stream().filter(i -> !i.isCompleted() && "DUE_TODAY".equals(i.getUrgency())).count();
        long upcomingCount = items.stream().filter(i -> !i.isCompleted() && "UPCOMING".equals(i.getUrgency())).count();

        return FollowUpRemindersResponse.builder()
                .overdueCount(overdueCount)
                .dueTodayCount(dueTodayCount)
                .upcomingCount(upcomingCount)
                .totalPendingCount(overdueCount + dueTodayCount + upcomingCount)
                .items(List.copyOf(items))
                .build();
    }

    private RecruiterResponse toSummaryResponse(
            Recruiter r,
            long interactionsCount,
            List<RecruiterInteraction> reminderInteractions
    ) {
        LocalDate nextReminder = reminderInteractions.stream()
                .filter(i -> !i.isReminderCompleted() && i.getFollowUpReminderDate() != null)
                .map(RecruiterInteraction::getFollowUpReminderDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        long pendingCount = reminderInteractions.stream()
                .filter(i -> !i.isReminderCompleted() && i.getFollowUpReminderDate() != null)
                .count();

        return RecruiterResponse.builder()
                .id(r.getId())
                .fullName(r.getFullName())
                .roleTitle(r.getRoleTitle())
                .companyId(r.getCompany() != null ? r.getCompany().getId() : null)
                .companyName(r.getCompanyName())
                .companyVerifiedSponsor(r.getCompany() != null && r.getCompany().isVerifiedSponsor())
                .email(r.getEmail())
                .linkedinUrl(r.getLinkedinUrl())
                .phone(r.getPhone())
                .notes(r.getNotes())
                .interactionsCount(interactionsCount)
                .nextFollowUpReminderDate(nextReminder)
                .pendingRemindersCount(pendingCount)
                .interactions(Collections.emptyList())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private RecruiterResponse toDetailResponse(Recruiter r, List<RecruiterInteraction> interactions) {
        List<RecruiterInteractionDto> dtos = interactions.stream()
                .map(outreachPersistenceHelper::toDto)
                .toList();

        Instant lastInteractionAt = interactions.stream()
                .map(i -> i.getSentAt() != null ? i.getSentAt() : i.getCreatedAt())
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        LocalDate nextReminder = interactions.stream()
                .filter(i -> !i.isReminderCompleted() && i.getFollowUpReminderDate() != null)
                .map(RecruiterInteraction::getFollowUpReminderDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        long pendingCount = interactions.stream()
                .filter(i -> !i.isReminderCompleted() && i.getFollowUpReminderDate() != null)
                .count();

        return RecruiterResponse.builder()
                .id(r.getId())
                .fullName(r.getFullName())
                .roleTitle(r.getRoleTitle())
                .companyId(r.getCompany() != null ? r.getCompany().getId() : null)
                .companyName(r.getCompanyName())
                .companyVerifiedSponsor(r.getCompany() != null && r.getCompany().isVerifiedSponsor())
                .email(r.getEmail())
                .linkedinUrl(r.getLinkedinUrl())
                .phone(r.getPhone())
                .notes(r.getNotes())
                .interactionsCount(dtos.size())
                .lastInteractionAt(lastInteractionAt)
                .nextFollowUpReminderDate(nextReminder)
                .pendingRemindersCount(pendingCount)
                .interactions(dtos)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private JobApplication resolveOwnedApplication(Long userId, String applicationUuid) {
        if (!hasText(applicationUuid)) {
            return null;
        }
        return jobApplicationRepository.findWithDetailsByUuidAndUserId(applicationUuid.trim(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Linked application not found"));
    }

    private String resolveUrgency(LocalDate reminderDate, boolean completed, LocalDate today) {
        if (completed) {
            return "COMPLETED";
        }
        if (reminderDate.isBefore(today)) {
            return "OVERDUE";
        }
        if (reminderDate.isEqual(today)) {
            return "DUE_TODAY";
        }
        return "UPCOMING";
    }

    private int urgencySortRank(String urgency) {
        return switch (urgency) {
            case "OVERDUE" -> 0;
            case "DUE_TODAY" -> 1;
            case "UPCOMING" -> 2;
            default -> 3;
        };
    }

    private String validateOptionalHttpUrl(String rawUrl) {
        String trimmed = trimToNull(rawUrl);
        if (trimmed == null) {
            return null;
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BusinessValidationException("LinkedIn URL must be a valid http or https URL");
            }
            return trimmed;
        } catch (URISyntaxException ex) {
            throw new BusinessValidationException("LinkedIn URL must be a valid http or https URL");
        }
    }

    private User requireAuthenticatedUser(AuthenticatedUserPrincipal principal) {
        Long userId = requireUserId(principal);
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
    }

    private Long requireUserId(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return principal.getId();
    }

    private void validatePositiveId(Long id, String label) {
        if (id == null || id <= 0) {
            throw new BusinessValidationException(label + " must be a positive integer");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

