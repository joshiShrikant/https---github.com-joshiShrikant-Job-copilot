package com.eurojobcopilot.modules.job.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceConflictException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.ApplyRedirectRequest;
import com.eurojobcopilot.modules.job.dto.ApplyRedirectResponse;
import com.eurojobcopilot.modules.job.dto.JobInteractionResponse;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobInteractionServiceImpl implements JobInteractionService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final UserJobInteractionRepository userJobInteractionRepository;

    @Override
    @Transactional
    public JobInteractionResponse saveJob(AuthenticatedUserPrincipal currentUser, Long jobId) {
        User user = requireAuthenticatedUser(currentUser);
        Job job = requireJob(jobId);

        UserJobInteraction interaction = findOrCreateInteraction(user, job);
        if (!interaction.isSaved()) {
            interaction.setSaved(true);
            interaction.setSavedAt(Instant.now());
            interaction = persistInteraction(interaction);
            log.info("User uuid={} saved jobId={}", user.getUuid(), jobId);
        }
        return toResponse(jobId, interaction);
    }

    @Override
    @Transactional
    public JobInteractionResponse unsaveJob(AuthenticatedUserPrincipal currentUser, Long jobId) {
        User user = requireAuthenticatedUser(currentUser);
        requireJobExists(jobId);

        Optional<UserJobInteraction> existing = userJobInteractionRepository.findByUserIdAndJobId(user.getId(), jobId);
        if (existing.isEmpty()) {
            return emptyResponse(jobId);
        }

        UserJobInteraction interaction = existing.get();
        if (interaction.isSaved()) {
            interaction.setSaved(false);
            interaction.setSavedAt(null);
            interaction = persistInteraction(interaction);
            log.info("User uuid={} unsaved jobId={}", user.getUuid(), jobId);
        }
        return toResponse(jobId, interaction);
    }

    @Override
    @Transactional
    public JobInteractionResponse markInterested(AuthenticatedUserPrincipal currentUser, Long jobId) {
        User user = requireAuthenticatedUser(currentUser);
        Job job = requireJob(jobId);

        UserJobInteraction interaction = findOrCreateInteraction(user, job);
        if (!interaction.isInterested()) {
            interaction.setInterested(true);
            interaction.setInterestedAt(Instant.now());
            interaction = persistInteraction(interaction);
            log.info("User uuid={} marked interested on jobId={}", user.getUuid(), jobId);
        }
        return toResponse(jobId, interaction);
    }

    @Override
    @Transactional
    public JobInteractionResponse clearInterested(AuthenticatedUserPrincipal currentUser, Long jobId) {
        User user = requireAuthenticatedUser(currentUser);
        requireJobExists(jobId);

        Optional<UserJobInteraction> existing = userJobInteractionRepository.findByUserIdAndJobId(user.getId(), jobId);
        if (existing.isEmpty()) {
            return emptyResponse(jobId);
        }

        UserJobInteraction interaction = existing.get();
        if (interaction.isInterested()) {
            interaction.setInterested(false);
            interaction.setInterestedAt(null);
            interaction = persistInteraction(interaction);
            log.info("User uuid={} cleared interested flag on jobId={}", user.getUuid(), jobId);
        }
        return toResponse(jobId, interaction);
    }

    @Override
    @Transactional
    public JobInteractionResponse hideJob(AuthenticatedUserPrincipal currentUser, Long jobId) {
        User user = requireAuthenticatedUser(currentUser);
        Job job = requireJob(jobId);

        UserJobInteraction interaction = findOrCreateInteraction(user, job);
        if (!interaction.isHidden()) {
            interaction.setHidden(true);
            interaction.setHiddenAt(Instant.now());
            interaction = persistInteraction(interaction);
            log.info("User uuid={} hid jobId={}", user.getUuid(), jobId);
        }
        return toResponse(jobId, interaction);
    }

    @Override
    @Transactional
    public JobInteractionResponse unhideJob(AuthenticatedUserPrincipal currentUser, Long jobId) {
        User user = requireAuthenticatedUser(currentUser);
        requireJobExists(jobId);

        Optional<UserJobInteraction> existing = userJobInteractionRepository.findByUserIdAndJobId(user.getId(), jobId);
        if (existing.isEmpty()) {
            return emptyResponse(jobId);
        }

        UserJobInteraction interaction = existing.get();
        if (interaction.isHidden()) {
            interaction.setHidden(false);
            interaction.setHiddenAt(null);
            interaction = persistInteraction(interaction);
            log.info("User uuid={} unhid jobId={}", user.getUuid(), jobId);
        }
        return toResponse(jobId, interaction);
    }

    @Override
    @Transactional
    public ApplyRedirectResponse trackApplyRedirect(
            AuthenticatedUserPrincipal currentUser,
            Long jobId,
            ApplyRedirectRequest request
    ) {
        User user = requireAuthenticatedUser(currentUser);
        Job job = requireJob(jobId);

        String targetUrl = resolveSafeExternalUrl(job);
        Instant now = Instant.now();

        UserJobInteraction interaction = findOrCreateInteraction(user, job);
        interaction.setRedirectClickedAt(now);
        if (request != null && Boolean.TRUE.equals(request.getMarkApplied()) && !interaction.isHasApplied()) {
            interaction.setHasApplied(true);
            interaction.setAppliedAt(now);
        }
        interaction = persistInteraction(interaction);

        log.info("User uuid={} clicked apply redirect for jobId={} markApplied={}",
                user.getUuid(), jobId, request != null && Boolean.TRUE.equals(request.getMarkApplied()));

        JobInteractionResponse response = toResponse(jobId, interaction);
        return ApplyRedirectResponse.builder()
                .jobId(jobId)
                .targetUrl(targetUrl)
                .redirectClickedAt(interaction.getRedirectClickedAt())
                .hasApplied(interaction.isHasApplied())
                .appliedAt(interaction.getAppliedAt())
                .interaction(response)
                .build();
    }

    private User requireAuthenticatedUser(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
    }

    private Job requireJob(Long jobId) {
        validatePositiveJobId(jobId);
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
    }

    private void requireJobExists(Long jobId) {
        validatePositiveJobId(jobId);
        if (!jobRepository.existsById(jobId)) {
            throw new ResourceNotFoundException("Job not found with id: " + jobId);
        }
    }

    private void validatePositiveJobId(Long jobId) {
        if (jobId == null || jobId <= 0) {
            throw new BusinessValidationException("Job ID must be a positive integer");
        }
    }

    private UserJobInteraction findOrCreateInteraction(User user, Job job) {
        return userJobInteractionRepository.findByUserIdAndJobId(user.getId(), job.getId())
                .orElseGet(() -> UserJobInteraction.builder()
                        .user(user)
                        .job(job)
                        .build());
    }

    private UserJobInteraction persistInteraction(UserJobInteraction interaction) {
        try {
            return userJobInteractionRepository.saveAndFlush(interaction);
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceConflictException("Concurrent update on job interaction. Please retry.");
        }
    }

    private String resolveSafeExternalUrl(Job job) {
        if (isValidHttpUrl(job.getApplicationUrl())) {
            return job.getApplicationUrl().trim();
        }
        if (isValidHttpUrl(job.getOriginalUrl())) {
            return job.getOriginalUrl().trim();
        }
        throw new BusinessValidationException("Job does not have a valid external application URL");
    }

    private boolean isValidHttpUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            return ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private JobInteractionResponse toResponse(Long jobId, UserJobInteraction interaction) {
        return JobInteractionResponse.builder()
                .jobId(jobId)
                .saved(interaction.isSaved())
                .savedAt(interaction.getSavedAt())
                .interested(interaction.isInterested())
                .interestedAt(interaction.getInterestedAt())
                .hidden(interaction.isHidden())
                .hiddenAt(interaction.getHiddenAt())
                .hasApplied(interaction.isHasApplied())
                .appliedAt(interaction.getAppliedAt())
                .redirectClickedAt(interaction.getRedirectClickedAt())
                .build();
    }

    private JobInteractionResponse emptyResponse(Long jobId) {
        return JobInteractionResponse.builder()
                .jobId(jobId)
                .saved(false)
                .interested(false)
                .hidden(false)
                .hasApplied(false)
                .build();
    }
}

