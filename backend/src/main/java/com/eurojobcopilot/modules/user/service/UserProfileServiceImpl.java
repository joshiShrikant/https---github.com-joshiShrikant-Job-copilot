package com.eurojobcopilot.modules.user.service;

import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.matching.service.JobMatchingService;
import com.eurojobcopilot.modules.user.dto.EducationDto;
import com.eurojobcopilot.modules.user.dto.UpsertEducationRequest;
import com.eurojobcopilot.modules.user.dto.UpsertProfileRequest;
import com.eurojobcopilot.modules.user.dto.UpsertSkillRequest;
import com.eurojobcopilot.modules.user.dto.UpsertWorkExperienceRequest;
import com.eurojobcopilot.modules.user.dto.UserProfileResponse;
import com.eurojobcopilot.modules.user.dto.UserSkillDto;
import com.eurojobcopilot.modules.user.dto.WorkExperienceDto;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.UserEducation;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import com.eurojobcopilot.modules.user.exception.InvalidProfileDataException;
import com.eurojobcopilot.modules.user.exception.ProfileConflictException;
import com.eurojobcopilot.modules.user.exception.ProfileResourceNotFoundException;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserEducationRepository userEducationRepository;
    private final UserWorkExperienceRepository userWorkExperienceRepository;
    private final UserJobPreferenceRepository userJobPreferenceRepository;
    private final JobMatchingService jobMatchingService;

    @Override
    @Transactional
    public UserProfileResponse createProfile(AuthenticatedUserPrincipal currentUser, UpsertProfileRequest request) {
        User user = requireAuthenticatedUser(currentUser);
        if (userProfileRepository.existsByUserId(user.getId())) {
            throw new ProfileConflictException("User profile already exists");
        }

        UserProfile profile = UserProfile.builder()
                .user(user)
                .build();

        applyProfileUpdates(user, profile, request, true);
        UserProfile savedProfile;
        try {
            savedProfile = userProfileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            throw new ProfileConflictException("User profile already exists");
        }

        UserJobPreference preference;
        try {
            preference = syncJobPreferences(user, request);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            throw new ProfileConflictException("Concurrent profile modification conflict");
        }
        jobMatchingService.recalculateActiveJobsForUser(user.getId());
        log.info("Created profile for userUuid={}", user.getUuid());
        return assembleProfileResponse(user, savedProfile, preference);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("User profile has not been created yet"));
        UserJobPreference preference = userJobPreferenceRepository.findByUserId(user.getId()).orElse(null);
        return assembleProfileResponse(user, profile, preference);
    }

    @Override
    @Transactional
    public UserProfileResponse upsertProfile(AuthenticatedUserPrincipal currentUser, UpsertProfileRequest request) {
        User user = requireAuthenticatedUser(currentUser);
        boolean isNew = false;
        UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);
        if (profile == null) {
            isNew = true;
            profile = UserProfile.builder().user(user).build();
        }

        applyProfileUpdates(user, profile, request, isNew);
        UserProfile savedProfile;
        UserJobPreference preference;
        try {
            savedProfile = userProfileRepository.saveAndFlush(profile);
            preference = syncJobPreferences(user, request);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            throw new ProfileConflictException("Concurrent profile modification conflict");
        }

        jobMatchingService.recalculateActiveJobsForUser(user.getId());
        log.info("Saved profile for userUuid={} created={}", user.getUuid(), isNew);
        return assembleProfileResponse(user, savedProfile, preference);
    }

    @Override
    @Transactional
    public void deleteProfile(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("User profile does not exist"));
        userProfileRepository.delete(profile);
        userProfileRepository.flush();
        jobMatchingService.recalculateActiveJobsForUser(user.getId());
        log.info("Deleted profile for userUuid={}", user.getUuid());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSkillDto> getSkills(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        return userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(user.getId())
                .stream()
                .map(this::toSkillDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserSkillDto getSkill(AuthenticatedUserPrincipal currentUser, Long skillId) {
        User user = requireAuthenticatedUser(currentUser);
        UserSkill existing = userSkillRepository.findByIdAndUserId(skillId, user.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("Skill not found"));
        return toSkillDto(existing);
    }

    @Override
    @Transactional
    public UserSkillDto addSkill(AuthenticatedUserPrincipal currentUser, UpsertSkillRequest request) {
        User user = requireAuthenticatedUser(currentUser);
        String trimmedName = request.getSkillName().trim();
        String normalized = trimmedName.toLowerCase(Locale.ROOT);

        if (userSkillRepository.existsByUserIdAndNormalizedSkill(user.getId(), normalized)) {
            throw new ProfileConflictException("Skill '" + trimmedName + "' already exists in your profile");
        }

        UserSkill skill = UserSkill.builder()
                .user(user)
                .skillName(trimmedName)
                .normalizedSkill(normalized)
                .yearsExperience(request.getYearsExperience())
                .proficiencyLevel(request.getProficiencyLevel())
                .primary(request.isPrimary())
                .build();

        UserSkill saved;
        try {
            saved = userSkillRepository.saveAndFlush(skill);
        } catch (DataIntegrityViolationException ex) {
            throw new ProfileConflictException("Skill '" + trimmedName + "' already exists in your profile");
        }
        jobMatchingService.recalculateActiveJobsForUser(user.getId());
        return toSkillDto(saved);
    }

    @Override
    @Transactional
    public UserSkillDto updateSkill(AuthenticatedUserPrincipal currentUser, Long skillId, UpsertSkillRequest request) {
        User user = requireAuthenticatedUser(currentUser);
        UserSkill existing = userSkillRepository.findByIdAndUserId(skillId, user.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("Skill not found"));

        String trimmedName = request.getSkillName().trim();
        String newNormalized = trimmedName.toLowerCase(Locale.ROOT);

        if (!newNormalized.equals(existing.getNormalizedSkill())
                && userSkillRepository.existsByUserIdAndNormalizedSkill(user.getId(), newNormalized)) {
            throw new ProfileConflictException("Skill '" + trimmedName + "' already exists in your profile");
        }

        existing.setSkillName(trimmedName);
        existing.setNormalizedSkill(newNormalized);
        existing.setYearsExperience(request.getYearsExperience());
        existing.setProficiencyLevel(request.getProficiencyLevel());
        existing.setPrimary(request.isPrimary());

        UserSkill saved;
        try {
            saved = userSkillRepository.saveAndFlush(existing);
        } catch (DataIntegrityViolationException ex) {
            throw new ProfileConflictException("Skill '" + trimmedName + "' already exists in your profile");
        }
        jobMatchingService.recalculateActiveJobsForUser(user.getId());
        return toSkillDto(saved);
    }

    @Override
    @Transactional
    public void deleteSkill(AuthenticatedUserPrincipal currentUser, Long skillId) {
        User user = requireAuthenticatedUser(currentUser);
        UserSkill existing = userSkillRepository.findByIdAndUserId(skillId, user.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("Skill not found"));
        userSkillRepository.delete(existing);
        userSkillRepository.flush();
        jobMatchingService.recalculateActiveJobsForUser(user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EducationDto> getEducations(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        return userEducationRepository.findAllByUserIdOrderByStartDateDesc(user.getId())
                .stream()
                .map(this::toEducationDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EducationDto getEducation(AuthenticatedUserPrincipal currentUser, Long educationId) {
        User user = requireAuthenticatedUser(currentUser);
        UserEducation existing = userEducationRepository.findByIdAndUserId(educationId, user.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("Education entry not found"));
        return toEducationDto(existing);
    }

    @Override
    @Transactional
    public EducationDto addEducation(AuthenticatedUserPrincipal currentUser, UpsertEducationRequest request) {
        User user = requireAuthenticatedUser(currentUser);
        validateDateRange(request.getStartDate(), request.getEndDate(), "Education end date cannot precede start date");

        UserEducation education = UserEducation.builder()
                .user(user)
                .institution(request.getInstitution().trim())
                .degree(request.getDegree().trim())
                .fieldOfStudy(request.getFieldOfStudy().trim())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .gradeOrGpa(trimToNull(request.getGradeOrGpa()))
                .country(request.getCountry().trim())
                .build();

        return toEducationDto(userEducationRepository.saveAndFlush(education));
    }

    @Override
    @Transactional
    public EducationDto updateEducation(
            AuthenticatedUserPrincipal currentUser,
            Long educationId,
            UpsertEducationRequest request
    ) {
        User user = requireAuthenticatedUser(currentUser);
        UserEducation existing = userEducationRepository.findByIdAndUserId(educationId, user.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("Education entry not found"));

        validateDateRange(request.getStartDate(), request.getEndDate(), "Education end date cannot precede start date");

        existing.setInstitution(request.getInstitution().trim());
        existing.setDegree(request.getDegree().trim());
        existing.setFieldOfStudy(request.getFieldOfStudy().trim());
        existing.setStartDate(request.getStartDate());
        existing.setEndDate(request.getEndDate());
        existing.setGradeOrGpa(trimToNull(request.getGradeOrGpa()));
        existing.setCountry(request.getCountry().trim());

        return toEducationDto(userEducationRepository.saveAndFlush(existing));
    }

    @Override
    @Transactional
    public void deleteEducation(AuthenticatedUserPrincipal currentUser, Long educationId) {
        User user = requireAuthenticatedUser(currentUser);
        UserEducation existing = userEducationRepository.findByIdAndUserId(educationId, user.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("Education entry not found"));
        userEducationRepository.delete(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkExperienceDto> getWorkExperiences(AuthenticatedUserPrincipal currentUser) {
        User user = requireAuthenticatedUser(currentUser);
        return userWorkExperienceRepository.findAllByUserIdOrderByStartDateDesc(user.getId())
                .stream()
                .map(this::toWorkExperienceDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkExperienceDto getWorkExperience(AuthenticatedUserPrincipal currentUser, Long experienceId) {
        User user = requireAuthenticatedUser(currentUser);
        UserWorkExperience existing = userWorkExperienceRepository.findByIdAndUserId(experienceId, user.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("Work experience entry not found"));
        return toWorkExperienceDto(existing);
    }

    @Override
    @Transactional
    public WorkExperienceDto addWorkExperience(
            AuthenticatedUserPrincipal currentUser,
            UpsertWorkExperienceRequest request
    ) {
        User user = requireAuthenticatedUser(currentUser);
        LocalDate effectiveEndDate = request.isCurrent() ? null : request.getEndDate();
        validateDateRange(request.getStartDate(), effectiveEndDate, "Work experience end date cannot precede start date");

        UserWorkExperience experience = UserWorkExperience.builder()
                .user(user)
                .companyName(request.getCompanyName().trim())
                .designation(request.getDesignation().trim())
                .location(trimToNull(request.getLocation()))
                .startDate(request.getStartDate())
                .endDate(effectiveEndDate)
                .current(request.isCurrent())
                .description(trimToNull(request.getDescription()))
                .skillsUsed(normalizeStringList(request.getSkillsUsed()))
                .build();

        UserWorkExperience saved = userWorkExperienceRepository.saveAndFlush(experience);
        jobMatchingService.recalculateActiveJobsForUser(user.getId());
        return toWorkExperienceDto(saved);
    }

    @Override
    @Transactional
    public WorkExperienceDto updateWorkExperience(
            AuthenticatedUserPrincipal currentUser,
            Long experienceId,
            UpsertWorkExperienceRequest request
    ) {
        User user = requireAuthenticatedUser(currentUser);
        UserWorkExperience existing = userWorkExperienceRepository.findByIdAndUserId(experienceId, user.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("Work experience entry not found"));

        LocalDate effectiveEndDate = request.isCurrent() ? null : request.getEndDate();
        validateDateRange(request.getStartDate(), effectiveEndDate, "Work experience end date cannot precede start date");

        existing.setCompanyName(request.getCompanyName().trim());
        existing.setDesignation(request.getDesignation().trim());
        existing.setLocation(trimToNull(request.getLocation()));
        existing.setStartDate(request.getStartDate());
        existing.setEndDate(effectiveEndDate);
        existing.setCurrent(request.isCurrent());
        existing.setDescription(trimToNull(request.getDescription()));
        existing.setSkillsUsed(normalizeStringList(request.getSkillsUsed()));

        UserWorkExperience saved = userWorkExperienceRepository.saveAndFlush(existing);
        jobMatchingService.recalculateActiveJobsForUser(user.getId());
        return toWorkExperienceDto(saved);
    }

    @Override
    @Transactional
    public void deleteWorkExperience(AuthenticatedUserPrincipal currentUser, Long experienceId) {
        User user = requireAuthenticatedUser(currentUser);
        UserWorkExperience existing = userWorkExperienceRepository.findByIdAndUserId(experienceId, user.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("Work experience entry not found"));
        userWorkExperienceRepository.delete(existing);
        userWorkExperienceRepository.flush();
        jobMatchingService.recalculateActiveJobsForUser(user.getId());
    }

    private User requireAuthenticatedUser(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ProfileResourceNotFoundException("Authenticated user not found");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ProfileResourceNotFoundException("Authenticated user no longer exists"));
    }

    private void applyProfileUpdates(
            User user,
            UserProfile profile,
            UpsertProfileRequest request,
            boolean isNew
    ) {
        boolean userUpdated = false;
        if (request.getFirstName() != null) {
            String trimmedFirst = request.getFirstName().trim();
            if (trimmedFirst.length() < 2 || trimmedFirst.length() > 100) {
                throw new InvalidProfileDataException("First name must be between 2 and 100 characters");
            }
            user.setFirstName(trimmedFirst);
            userUpdated = true;
        }
        if (request.getLastName() != null) {
            String trimmedLast = request.getLastName().trim();
            if (trimmedLast.length() < 2 || trimmedLast.length() > 100) {
                throw new InvalidProfileDataException("Last name must be between 2 and 100 characters");
            }
            user.setLastName(trimmedLast);
            userUpdated = true;
        }
        if (userUpdated) {
            userRepository.save(user);
        }

        profile.setCurrentDesignation(trimToNull(request.getCurrentDesignation()));
        profile.setCurrentCountry(trimToNull(request.getCurrentCountry()));
        profile.setCurrentCity(trimToNull(request.getCurrentCity()));
        profile.setExpectedSalaryMin(request.getExpectedSalaryMin());
        profile.setLinkedinUrl(trimToNull(request.getLinkedinUrl()));
        profile.setGithubUrl(trimToNull(request.getGithubUrl()));
        profile.setPortfolioUrl(trimToNull(request.getPortfolioUrl()));
        profile.setBio(trimToNull(request.getBio()));

        if (request.getYearsOfExperience() != null) {
            profile.setYearsOfExperience(request.getYearsOfExperience());
        } else if (isNew) {
            profile.setYearsOfExperience(BigDecimal.ZERO);
        }

        String normalizedCurrency = trimToNull(request.getExpectedSalaryCurrency());
        if (normalizedCurrency != null) {
            profile.setExpectedSalaryCurrency(normalizedCurrency.toUpperCase(Locale.ROOT));
        } else if (isNew || profile.getExpectedSalaryCurrency() == null) {
            profile.setExpectedSalaryCurrency("EUR");
        }

        if (request.getNoticePeriodDays() != null) {
            profile.setNoticePeriodDays(request.getNoticePeriodDays());
        } else if (isNew) {
            profile.setNoticePeriodDays(30);
        }

        if (request.getWorkAuthorizationStatus() != null) {
            profile.setWorkAuthorizationStatus(request.getWorkAuthorizationStatus());
        } else if (isNew) {
            profile.setWorkAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP);
        }

        if (request.getRelocationPreference() != null) {
            profile.setRelocationPreference(request.getRelocationPreference());
        } else if (isNew) {
            profile.setRelocationPreference(RelocationPreference.WILLING_TO_RELOCATE);
        }
    }

    private UserJobPreference syncJobPreferences(User user, UpsertProfileRequest request) {
        if (request.getPreferredJobTitles() == null && request.getPreferredCountries() == null) {
            return userJobPreferenceRepository.findByUserId(user.getId()).orElse(null);
        }

        UserJobPreference preference = userJobPreferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> UserJobPreference.builder().user(user).build());

        if (request.getPreferredJobTitles() != null) {
            preference.setPreferredJobTitles(normalizeStringList(request.getPreferredJobTitles()));
        }
        if (request.getPreferredCountries() != null) {
            preference.setPreferredCountries(normalizeStringList(request.getPreferredCountries()));
        }

        return userJobPreferenceRepository.saveAndFlush(preference);
    }

    private UserProfileResponse assembleProfileResponse(
            User user,
            UserProfile profile,
            UserJobPreference preference
    ) {
        List<UserSkillDto> skills = userSkillRepository
                .findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(user.getId())
                .stream()
                .map(this::toSkillDto)
                .toList();

        List<EducationDto> educations = userEducationRepository
                .findAllByUserIdOrderByStartDateDesc(user.getId())
                .stream()
                .map(this::toEducationDto)
                .toList();

        List<WorkExperienceDto> experiences = userWorkExperienceRepository
                .findAllByUserIdOrderByStartDateDesc(user.getId())
                .stream()
                .map(this::toWorkExperienceDto)
                .toList();

        List<String> preferredTitles = preference != null && preference.getPreferredJobTitles() != null
                ? List.copyOf(preference.getPreferredJobTitles())
                : List.of();
        List<String> preferredCountries = preference != null && preference.getPreferredCountries() != null
                ? List.copyOf(preference.getPreferredCountries())
                : List.of();

        return UserProfileResponse.builder()
                .uuid(user.getUuid())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName((user.getFirstName() + " " + user.getLastName()).trim())
                .email(user.getEmail())
                .currentDesignation(profile.getCurrentDesignation())
                .yearsOfExperience(profile.getYearsOfExperience())
                .currentCountry(profile.getCurrentCountry())
                .currentCity(profile.getCurrentCity())
                .expectedSalaryMin(profile.getExpectedSalaryMin())
                .expectedSalaryCurrency(profile.getExpectedSalaryCurrency())
                .noticePeriodDays(profile.getNoticePeriodDays())
                .workAuthorizationStatus(profile.getWorkAuthorizationStatus())
                .relocationPreference(profile.getRelocationPreference())
                .linkedinUrl(profile.getLinkedinUrl())
                .githubUrl(profile.getGithubUrl())
                .portfolioUrl(profile.getPortfolioUrl())
                .bio(profile.getBio())
                .preferredJobTitles(preferredTitles)
                .preferredCountries(preferredCountries)
                .skills(skills)
                .educations(educations)
                .experiences(experiences)
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private UserSkillDto toSkillDto(UserSkill entity) {
        return UserSkillDto.builder()
                .id(entity.getId())
                .skillName(entity.getSkillName())
                .normalizedSkill(entity.getNormalizedSkill())
                .yearsExperience(entity.getYearsExperience())
                .proficiencyLevel(entity.getProficiencyLevel())
                .primary(entity.isPrimary())
                .build();
    }

    private EducationDto toEducationDto(UserEducation entity) {
        return EducationDto.builder()
                .id(entity.getId())
                .institution(entity.getInstitution())
                .degree(entity.getDegree())
                .fieldOfStudy(entity.getFieldOfStudy())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .gradeOrGpa(entity.getGradeOrGpa())
                .country(entity.getCountry())
                .build();
    }

    private WorkExperienceDto toWorkExperienceDto(UserWorkExperience entity) {
        return WorkExperienceDto.builder()
                .id(entity.getId())
                .companyName(entity.getCompanyName())
                .designation(entity.getDesignation())
                .location(entity.getLocation())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .current(entity.isCurrent())
                .description(entity.getDescription())
                .skillsUsed(entity.getSkillsUsed() != null ? List.copyOf(entity.getSkillsUsed()) : List.of())
                .build();
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate, String errorMessage) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new InvalidProfileDataException(errorMessage);
        }
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
