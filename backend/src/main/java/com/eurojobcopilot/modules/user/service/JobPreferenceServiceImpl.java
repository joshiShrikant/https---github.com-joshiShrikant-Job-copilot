package com.eurojobcopilot.modules.user.service;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.matching.service.JobMatchingService;
import com.eurojobcopilot.modules.user.dto.JobPreferencesResponse;
import com.eurojobcopilot.modules.user.dto.UpdateJobPreferencesRequest;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.exception.ProfileConflictException;
import com.eurojobcopilot.modules.user.exception.ProfileResourceNotFoundException;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobPreferenceServiceImpl implements JobPreferenceService {

    private static final String DEFAULT_CURRENCY = "EUR";
    private static final int DEFAULT_MIN_MATCH_SCORE = 60;

    private final UserRepository userRepository;
    private final UserJobPreferenceRepository userJobPreferenceRepository;
    private final JobMatchingService jobMatchingService;

    @Override
    @Transactional(readOnly = true)
    public JobPreferencesResponse getPreferences(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        return userJobPreferenceRepository.findByUserId(user.getId())
                .map(this::toResponse)
                .orElseGet(this::defaultResponse);
    }

    @Override
    @Transactional
    public JobPreferencesResponse updatePreferences(
            AuthenticatedUserPrincipal currentUser,
            UpdateJobPreferencesRequest request
    ) {
        User user = requireAuthenticatedUser(currentUser);
        boolean isNew = false;

        UserJobPreference preference = userJobPreferenceRepository.findByUserId(user.getId()).orElse(null);
        if (preference == null) {
            isNew = true;
            preference = UserJobPreference.builder()
                    .user(user)
                    .build();
        }

        if (request.getPreferredJobTitles() != null) {
            preference.setPreferredJobTitles(normalizeStringList(request.getPreferredJobTitles()));
        } else if (preference.getPreferredJobTitles() == null) {
            preference.setPreferredJobTitles(new ArrayList<>());
        }

        if (request.getPreferredCountries() != null) {
            preference.setPreferredCountries(normalizeStringList(request.getPreferredCountries()));
        } else if (preference.getPreferredCountries() == null) {
            preference.setPreferredCountries(new ArrayList<>());
        }

        if (request.getPreferredCities() != null) {
            preference.setPreferredCities(normalizeStringList(request.getPreferredCities()));
        } else if (preference.getPreferredCities() == null) {
            preference.setPreferredCities(new ArrayList<>());
        }

        preference.setMinSalary(request.getMinSalary());

        String normalizedCurrency = trimToNull(request.getSalaryCurrency());
        if (normalizedCurrency != null) {
            preference.setSalaryCurrency(normalizedCurrency.toUpperCase(Locale.ROOT));
        } else if (isNew || preference.getSalaryCurrency() == null) {
            preference.setSalaryCurrency(DEFAULT_CURRENCY);
        }

        if (request.getRequiresSponsorship() != null) {
            preference.setRequiresSponsorship(request.getRequiresSponsorship());
        } else if (isNew) {
            preference.setRequiresSponsorship(true);
        }

        if (request.getRequiresRelocation() != null) {
            preference.setRequiresRelocation(request.getRequiresRelocation());
        } else if (isNew) {
            preference.setRequiresRelocation(true);
        }

        if (request.getWorkArrangement() != null) {
            preference.setWorkArrangement(request.getWorkArrangement());
        } else if (isNew || preference.getWorkArrangement() == null) {
            preference.setWorkArrangement(WorkArrangement.ANY);
        }

        if (request.getEnglishSpeakingOnly() != null) {
            preference.setEnglishSpeakingOnly(request.getEnglishSpeakingOnly());
        } else if (isNew) {
            preference.setEnglishSpeakingOnly(true);
        }

        if (request.getMinMatchScore() != null) {
            preference.setMinMatchScore(request.getMinMatchScore());
        } else if (isNew || preference.getMinMatchScore() == null) {
            preference.setMinMatchScore(DEFAULT_MIN_MATCH_SCORE);
        }

        if (request.getAutopilotEnabled() != null) {
            preference.setAutopilotEnabled(request.getAutopilotEnabled());
        } else if (isNew) {
            preference.setAutopilotEnabled(false);
        }

        if (request.getAutopilotPaused() != null) {
            preference.setAutopilotPaused(request.getAutopilotPaused());
        } else if (isNew) {
            preference.setAutopilotPaused(false);
        }

        if (request.getEmailNotificationsEnabled() != null) {
            preference.setEmailNotificationsEnabled(request.getEmailNotificationsEnabled());
        } else if (isNew) {
            preference.setEmailNotificationsEnabled(false);
        }

        UserJobPreference saved;
        try {
            saved = userJobPreferenceRepository.saveAndFlush(preference);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            throw new ProfileConflictException("Concurrent job preference modification conflict");
        }
        jobMatchingService.recalculateActiveJobsForUser(user.getId());
        log.info("Updated job preferences for userUuid={} created={}", user.getUuid(), isNew);
        return toResponse(saved);
    }

    private User requireAuthenticatedUser(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ProfileResourceNotFoundException("Authenticated user not found");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("Authenticated user no longer exists"));
    }

    private JobPreferencesResponse toResponse(UserJobPreference entity) {
        return JobPreferencesResponse.builder()
                .preferredJobTitles(safeImmutableCopy(entity.getPreferredJobTitles()))
                .preferredCountries(safeImmutableCopy(entity.getPreferredCountries()))
                .preferredCities(safeImmutableCopy(entity.getPreferredCities()))
                .minSalary(entity.getMinSalary())
                .salaryCurrency(entity.getSalaryCurrency() != null ? entity.getSalaryCurrency() : DEFAULT_CURRENCY)
                .requiresSponsorship(entity.isRequiresSponsorship())
                .requiresRelocation(entity.isRequiresRelocation())
                .workArrangement(entity.getWorkArrangement() != null ? entity.getWorkArrangement() : WorkArrangement.ANY)
                .englishSpeakingOnly(entity.isEnglishSpeakingOnly())
                .minMatchScore(entity.getMinMatchScore() != null ? entity.getMinMatchScore() : DEFAULT_MIN_MATCH_SCORE)
                .autopilotEnabled(entity.isAutopilotEnabled())
                .autopilotPaused(entity.isAutopilotPaused())
                .emailNotificationsEnabled(entity.isEmailNotificationsEnabled())
                .lastAutopilotRunAt(entity.getLastAutopilotRunAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private JobPreferencesResponse defaultResponse() {
        return JobPreferencesResponse.builder()
                .preferredJobTitles(List.of())
                .preferredCountries(List.of())
                .preferredCities(List.of())
                .minSalary(null)
                .salaryCurrency(DEFAULT_CURRENCY)
                .requiresSponsorship(true)
                .requiresRelocation(true)
                .workArrangement(WorkArrangement.ANY)
                .englishSpeakingOnly(true)
                .minMatchScore(DEFAULT_MIN_MATCH_SCORE)
                .autopilotEnabled(false)
                .autopilotPaused(false)
                .emailNotificationsEnabled(false)
                .lastAutopilotRunAt(null)
                .updatedAt(null)
                .build();
    }

    private List<String> safeImmutableCopy(List<String> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    private List<String> normalizeStringList(List<String> input) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String item : input) {
            if (item != null && !item.isBlank()) {
                deduplicated.add(item.trim());
            }
        }
        return new ArrayList<>(deduplicated);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

