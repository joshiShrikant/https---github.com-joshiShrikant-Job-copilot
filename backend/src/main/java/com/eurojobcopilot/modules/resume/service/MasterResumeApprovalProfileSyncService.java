package com.eurojobcopilot.modules.resume.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.resume.dto.ApproveMasterResumeRequest;
import com.eurojobcopilot.modules.resume.dto.MasterResumeSyncSummaryDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeEducationDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProfileDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProjectDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeSkillDto;
import com.eurojobcopilot.modules.resume.dto.ProfileSyncMode;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.UserEducation;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates human-confirmed master resume data and synchronizes approved qualifications into
 * {@code user_profiles}, {@code user_skills}, {@code user_work_experiences}, and {@code user_educations}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterResumeApprovalProfileSyncService {

    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserWorkExperienceRepository userWorkExperienceRepository;
    private final UserEducationRepository userEducationRepository;

    public SyncResult validateAndSync(User user, ApproveMasterResumeRequest request) {
        if (request == null || request.getApprovedParsedData() == null) {
            throw new BusinessValidationException("Approved parsed resume data is required");
        }

        ProfileSyncMode syncMode = request.getSyncMode() != null ? request.getSyncMode() : ProfileSyncMode.MERGE;
        ParsedResumeJsonDto normalized = normalizeAndValidateForApproval(request.getApprovedParsedData());

        ProfileUpsertOutcome profileOutcome = syncUserProfile(user, normalized);
        CollectionOutcome skillsOutcome = syncUserSkills(user, normalized.getSkills(), syncMode);
        CollectionOutcome expOutcome = syncWorkExperiences(user, normalized.getExperiences(), syncMode);
        CollectionOutcome eduOutcome = syncEducations(user, normalized.getEducations(), syncMode);

        MasterResumeSyncSummaryDto summary = MasterResumeSyncSummaryDto.builder()
                .syncMode(syncMode)
                .profileCreated(profileOutcome.created())
                .profileUpdated(profileOutcome.updated())
                .skillsAdded(skillsOutcome.added())
                .skillsUpdated(skillsOutcome.updated())
                .experiencesAdded(expOutcome.added())
                .experiencesUpdated(expOutcome.updated())
                .educationsAdded(eduOutcome.added())
                .educationsUpdated(eduOutcome.updated())
                .syncedAt(Instant.now())
                .build();

        return new SyncResult(normalized, summary);
    }

    private ParsedResumeJsonDto normalizeAndValidateForApproval(ParsedResumeJsonDto raw) {
        ParsedResumeProfileDto rawProfile = raw.getProfile();
        ParsedResumeProfileDto cleanProfile = rawProfile != null
                ? ParsedResumeProfileDto.builder()
                .fullName(trimToNull(rawProfile.getFullName()))
                .currentDesignation(trimToNull(rawProfile.getCurrentDesignation()))
                .yearsOfExperience(rawProfile.getYearsOfExperience())
                .currentCountry(trimToNull(rawProfile.getCurrentCountry()))
                .currentCity(trimToNull(rawProfile.getCurrentCity()))
                .linkedinUrl(trimToNull(rawProfile.getLinkedinUrl()))
                .githubUrl(trimToNull(rawProfile.getGithubUrl()))
                .portfolioUrl(trimToNull(rawProfile.getPortfolioUrl()))
                .bio(trimToNull(rawProfile.getBio()))
                .workAuthorizationStatus(rawProfile.getWorkAuthorizationStatus())
                .relocationPreference(rawProfile.getRelocationPreference())
                .build()
                : ParsedResumeProfileDto.builder().build();

        String cleanSummary = trimToNull(raw.getSummary());

        Map<String, ParsedResumeSkillDto> dedupSkills = new LinkedHashMap<>();
        if (raw.getSkills() != null) {
            for (ParsedResumeSkillDto s : raw.getSkills()) {
                if (s == null || s.getSkillName() == null || s.getSkillName().isBlank()) {
                    throw new BusinessValidationException("Skill name is required for all approved skills");
                }
                String cleanName = s.getSkillName().trim();
                String key = cleanName.toLowerCase(Locale.ROOT);
                ParsedResumeSkillDto existing = dedupSkills.get(key);
                if (existing == null) {
                    dedupSkills.put(key, ParsedResumeSkillDto.builder()
                            .skillName(cleanName)
                            .yearsExperience(s.getYearsExperience())
                            .proficiencyLevel(s.getProficiencyLevel())
                            .primary(s.isPrimary())
                            .build());
                } else {
                    if (s.isPrimary()) {
                        existing.setPrimary(true);
                    }
                    if (existing.getYearsExperience() == null && s.getYearsExperience() != null) {
                        existing.setYearsExperience(s.getYearsExperience());
                    }
                    if (existing.getProficiencyLevel() == null && s.getProficiencyLevel() != null) {
                        existing.setProficiencyLevel(s.getProficiencyLevel());
                    }
                }
            }
        }

        List<ParsedResumeExperienceDto> cleanExps = new ArrayList<>();
        if (raw.getExperiences() != null) {
            for (ParsedResumeExperienceDto exp : raw.getExperiences()) {
                if (exp == null) {
                    continue;
                }
                String company = trimToNull(exp.getCompanyName());
                String designation = trimToNull(exp.getDesignation());
                if (company == null || designation == null) {
                    throw new BusinessValidationException("Company name and designation are required for every approved work experience");
                }
                if (exp.getStartDate() == null) {
                    throw new BusinessValidationException(
                            "Start date is required for work experience '" + designation + " at " + company + "' before approving."
                    );
                }
                LocalDate effectiveEnd = exp.isCurrent() ? null : exp.getEndDate();
                if (effectiveEnd != null && effectiveEnd.isBefore(exp.getStartDate())) {
                    throw new BusinessValidationException(
                            "Work experience end date cannot precede start date for '" + designation + " at " + company + "'."
                    );
                }
                cleanExps.add(ParsedResumeExperienceDto.builder()
                        .companyName(company)
                        .designation(designation)
                        .location(trimToNull(exp.getLocation()))
                        .startDate(exp.getStartDate())
                        .endDate(effectiveEnd)
                        .current(exp.isCurrent())
                        .description(trimToNull(exp.getDescription()))
                        .skillsUsed(normalizeDistinctList(exp.getSkillsUsed()))
                        .build());
            }
        }

        List<ParsedResumeEducationDto> cleanEdus = new ArrayList<>();
        if (raw.getEducations() != null) {
            for (ParsedResumeEducationDto edu : raw.getEducations()) {
                if (edu == null) {
                    continue;
                }
                String institution = trimToNull(edu.getInstitution());
                String degree = trimToNull(edu.getDegree());
                String field = trimToNull(edu.getFieldOfStudy());
                String country = trimToNull(edu.getCountry());
                if (institution == null || degree == null || field == null) {
                    throw new BusinessValidationException("Institution, degree, and field of study are required for every approved education");
                }
                if (edu.getStartDate() == null) {
                    throw new BusinessValidationException(
                            "Start date is required for education '" + degree + " at " + institution + "' before approving."
                    );
                }
                if (country == null) {
                    throw new BusinessValidationException(
                            "Country is required for education '" + degree + " at " + institution + "' before approving."
                    );
                }
                if (edu.getEndDate() != null && edu.getEndDate().isBefore(edu.getStartDate())) {
                    throw new BusinessValidationException(
                            "Education end date cannot precede start date for '" + degree + " at " + institution + "'."
                    );
                }
                cleanEdus.add(ParsedResumeEducationDto.builder()
                        .institution(institution)
                        .degree(degree)
                        .fieldOfStudy(field)
                        .startDate(edu.getStartDate())
                        .endDate(edu.getEndDate())
                        .gradeOrGpa(trimToNull(edu.getGradeOrGpa()))
                        .country(country)
                        .build());
            }
        }

        List<ParsedResumeProjectDto> cleanProjects = new ArrayList<>();
        if (raw.getProjects() != null) {
            for (ParsedResumeProjectDto proj : raw.getProjects()) {
                if (proj == null) {
                    continue;
                }
                String name = trimToNull(proj.getName());
                if (name == null) {
                    throw new BusinessValidationException("Project name is required for every approved project");
                }
                cleanProjects.add(ParsedResumeProjectDto.builder()
                        .name(name)
                        .description(trimToNull(proj.getDescription()))
                        .technologies(normalizeDistinctList(proj.getTechnologies()))
                        .url(trimToNull(proj.getUrl()))
                        .build());
            }
        }

        return ParsedResumeJsonDto.builder()
                .profile(cleanProfile)
                .summary(cleanSummary)
                .skills(List.copyOf(dedupSkills.values()))
                .experiences(List.copyOf(cleanExps))
                .educations(List.copyOf(cleanEdus))
                .projects(List.copyOf(cleanProjects))
                .languages(normalizeDistinctList(raw.getLanguages()))
                .certifications(normalizeDistinctList(raw.getCertifications()))
                .warnings(List.of())
                .build();
    }

    private ProfileUpsertOutcome syncUserProfile(User user, ParsedResumeJsonDto approved) {
        UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);
        boolean isNew = false;
        if (profile == null) {
            isNew = true;
            profile = UserProfile.builder()
                    .user(user)
                    .yearsOfExperience(BigDecimal.ZERO)
                    .expectedSalaryCurrency("EUR")
                    .noticePeriodDays(30)
                    .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                    .relocationPreference(RelocationPreference.WILLING_TO_RELOCATE)
                    .build();
        }

        ParsedResumeProfileDto incoming = approved.getProfile();
        boolean changed = isNew;

        if (incoming != null) {
            if (incoming.getCurrentDesignation() != null
                    && !incoming.getCurrentDesignation().equals(profile.getCurrentDesignation())) {
                profile.setCurrentDesignation(incoming.getCurrentDesignation());
                changed = true;
            }
            if (incoming.getYearsOfExperience() != null
                    && (profile.getYearsOfExperience() == null
                    || incoming.getYearsOfExperience().compareTo(profile.getYearsOfExperience()) != 0)) {
                profile.setYearsOfExperience(incoming.getYearsOfExperience());
                changed = true;
            }
            if (incoming.getCurrentCountry() != null
                    && !incoming.getCurrentCountry().equals(profile.getCurrentCountry())) {
                profile.setCurrentCountry(incoming.getCurrentCountry());
                changed = true;
            }
            if (incoming.getCurrentCity() != null
                    && !incoming.getCurrentCity().equals(profile.getCurrentCity())) {
                profile.setCurrentCity(incoming.getCurrentCity());
                changed = true;
            }
            if (incoming.getLinkedinUrl() != null
                    && !incoming.getLinkedinUrl().equals(profile.getLinkedinUrl())) {
                profile.setLinkedinUrl(incoming.getLinkedinUrl());
                changed = true;
            }
            if (incoming.getGithubUrl() != null
                    && !incoming.getGithubUrl().equals(profile.getGithubUrl())) {
                profile.setGithubUrl(incoming.getGithubUrl());
                changed = true;
            }
            if (incoming.getPortfolioUrl() != null
                    && !incoming.getPortfolioUrl().equals(profile.getPortfolioUrl())) {
                profile.setPortfolioUrl(incoming.getPortfolioUrl());
                changed = true;
            }
            if (incoming.getWorkAuthorizationStatus() != null
                    && incoming.getWorkAuthorizationStatus() != profile.getWorkAuthorizationStatus()) {
                profile.setWorkAuthorizationStatus(incoming.getWorkAuthorizationStatus());
                changed = true;
            }
            if (incoming.getRelocationPreference() != null
                    && incoming.getRelocationPreference() != profile.getRelocationPreference()) {
                profile.setRelocationPreference(incoming.getRelocationPreference());
                changed = true;
            }
        }

        String effectiveBio = incoming != null && incoming.getBio() != null ? incoming.getBio() : approved.getSummary();
        if (effectiveBio != null && !effectiveBio.equals(profile.getBio())) {
            profile.setBio(effectiveBio);
            changed = true;
        }

        if (changed) {
            userProfileRepository.saveAndFlush(profile);
        }

        return new ProfileUpsertOutcome(isNew, !isNew && changed);
    }

    private CollectionOutcome syncUserSkills(
            User user,
            List<ParsedResumeSkillDto> approvedSkills,
            ProfileSyncMode syncMode
    ) {
        List<UserSkill> existingList = userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(user.getId());
        Map<String, UserSkill> existingByKey = new LinkedHashMap<>();
        for (UserSkill skill : existingList) {
            String key = normalizeSkillKey(skill.getSkillName());
            existingByKey.putIfAbsent(key, skill);
        }

        Set<String> incomingKeys = new LinkedHashSet<>();
        for (ParsedResumeSkillDto skill : approvedSkills) {
            incomingKeys.add(normalizeSkillKey(skill.getSkillName()));
        }

        if (syncMode == ProfileSyncMode.REPLACE) {
            List<UserSkill> toDelete = existingList.stream()
                    .filter(existing -> !incomingKeys.contains(normalizeSkillKey(existing.getSkillName())))
                    .toList();
            if (!toDelete.isEmpty()) {
                userSkillRepository.deleteAllInBatch(toDelete);
                userSkillRepository.flush();
            }
        }

        int added = 0;
        int updated = 0;
        List<UserSkill> toSave = new ArrayList<>();

        for (ParsedResumeSkillDto incoming : approvedSkills) {
            String normalizedKey = normalizeSkillKey(incoming.getSkillName());
            UserSkill existing = existingByKey.get(normalizedKey);
            if (existing != null) {
                existing.setSkillName(incoming.getSkillName());
                existing.setNormalizedSkill(normalizedKey);
                if (incoming.getYearsExperience() != null) {
                    existing.setYearsExperience(incoming.getYearsExperience());
                } else if (existing.getYearsExperience() == null) {
                    existing.setYearsExperience(BigDecimal.ONE);
                }
                if (incoming.getProficiencyLevel() != null) {
                    existing.setProficiencyLevel(incoming.getProficiencyLevel());
                } else if (existing.getProficiencyLevel() == null) {
                    existing.setProficiencyLevel(ProficiencyLevel.INTERMEDIATE);
                }
                if (syncMode == ProfileSyncMode.REPLACE || incoming.isPrimary()) {
                    existing.setPrimary(incoming.isPrimary());
                }
                toSave.add(existing);
                updated++;
            } else {
                toSave.add(UserSkill.builder()
                        .user(user)
                        .skillName(incoming.getSkillName())
                        .normalizedSkill(normalizedKey)
                        .yearsExperience(incoming.getYearsExperience() != null ? incoming.getYearsExperience() : BigDecimal.ONE)
                        .proficiencyLevel(incoming.getProficiencyLevel() != null ? incoming.getProficiencyLevel() : ProficiencyLevel.INTERMEDIATE)
                        .primary(incoming.isPrimary())
                        .build());
                added++;
            }
        }

        if (!toSave.isEmpty()) {
            userSkillRepository.saveAllAndFlush(toSave);
        }
        return new CollectionOutcome(added, updated);
    }

    private CollectionOutcome syncWorkExperiences(
            User user,
            List<ParsedResumeExperienceDto> approvedExps,
            ProfileSyncMode syncMode
    ) {
        List<UserWorkExperience> existingList = userWorkExperienceRepository.findAllByUserIdOrderByStartDateDesc(user.getId());
        Map<String, UserWorkExperience> existingByKey = new LinkedHashMap<>();
        for (UserWorkExperience exp : existingList) {
            existingByKey.putIfAbsent(experienceKey(exp.getCompanyName(), exp.getDesignation(), exp.getStartDate()), exp);
        }

        Set<Long> matchedIds = new LinkedHashSet<>();
        int added = 0;
        int updated = 0;
        List<UserWorkExperience> toSave = new ArrayList<>();

        for (ParsedResumeExperienceDto incoming : approvedExps) {
            String key = experienceKey(incoming.getCompanyName(), incoming.getDesignation(), incoming.getStartDate());
            UserWorkExperience existing = existingByKey.get(key);
            if (existing != null) {
                matchedIds.add(existing.getId());
                existing.setCompanyName(incoming.getCompanyName());
                existing.setDesignation(incoming.getDesignation());
                existing.setLocation(incoming.getLocation());
                existing.setStartDate(incoming.getStartDate());
                existing.setEndDate(incoming.isCurrent() ? null : incoming.getEndDate());
                existing.setCurrent(incoming.isCurrent());
                existing.setDescription(incoming.getDescription());
                existing.setSkillsUsed(new ArrayList<>(incoming.getSkillsUsed()));
                toSave.add(existing);
                updated++;
            } else {
                toSave.add(UserWorkExperience.builder()
                        .user(user)
                        .companyName(incoming.getCompanyName())
                        .designation(incoming.getDesignation())
                        .location(incoming.getLocation())
                        .startDate(incoming.getStartDate())
                        .endDate(incoming.isCurrent() ? null : incoming.getEndDate())
                        .current(incoming.isCurrent())
                        .description(incoming.getDescription())
                        .skillsUsed(new ArrayList<>(incoming.getSkillsUsed()))
                        .build());
                added++;
            }
        }

        if (syncMode == ProfileSyncMode.REPLACE) {
            List<UserWorkExperience> toDelete = existingList.stream()
                    .filter(existing -> !matchedIds.contains(existing.getId()))
                    .toList();
            if (!toDelete.isEmpty()) {
                userWorkExperienceRepository.deleteAllInBatch(toDelete);
                userWorkExperienceRepository.flush();
            }
        }

        if (!toSave.isEmpty()) {
            userWorkExperienceRepository.saveAllAndFlush(toSave);
        }
        return new CollectionOutcome(added, updated);
    }

    private CollectionOutcome syncEducations(
            User user,
            List<ParsedResumeEducationDto> approvedEdus,
            ProfileSyncMode syncMode
    ) {
        List<UserEducation> existingList = userEducationRepository.findAllByUserIdOrderByStartDateDesc(user.getId());
        Map<String, UserEducation> existingByKey = new LinkedHashMap<>();
        for (UserEducation edu : existingList) {
            existingByKey.putIfAbsent(educationKey(edu.getInstitution(), edu.getDegree(), edu.getStartDate()), edu);
        }

        Set<Long> matchedIds = new LinkedHashSet<>();
        int added = 0;
        int updated = 0;
        List<UserEducation> toSave = new ArrayList<>();

        for (ParsedResumeEducationDto incoming : approvedEdus) {
            String key = educationKey(incoming.getInstitution(), incoming.getDegree(), incoming.getStartDate());
            UserEducation existing = existingByKey.get(key);
            if (existing != null) {
                matchedIds.add(existing.getId());
                existing.setInstitution(incoming.getInstitution());
                existing.setDegree(incoming.getDegree());
                existing.setFieldOfStudy(incoming.getFieldOfStudy());
                existing.setStartDate(incoming.getStartDate());
                existing.setEndDate(incoming.getEndDate());
                existing.setGradeOrGpa(incoming.getGradeOrGpa());
                existing.setCountry(incoming.getCountry());
                toSave.add(existing);
                updated++;
            } else {
                toSave.add(UserEducation.builder()
                        .user(user)
                        .institution(incoming.getInstitution())
                        .degree(incoming.getDegree())
                        .fieldOfStudy(incoming.getFieldOfStudy())
                        .startDate(incoming.getStartDate())
                        .endDate(incoming.getEndDate())
                        .gradeOrGpa(incoming.getGradeOrGpa())
                        .country(incoming.getCountry())
                        .build());
                added++;
            }
        }

        if (syncMode == ProfileSyncMode.REPLACE) {
            List<UserEducation> toDelete = existingList.stream()
                    .filter(existing -> !matchedIds.contains(existing.getId()))
                    .toList();
            if (!toDelete.isEmpty()) {
                userEducationRepository.deleteAllInBatch(toDelete);
                userEducationRepository.flush();
            }
        }

        if (!toSave.isEmpty()) {
            userEducationRepository.saveAllAndFlush(toSave);
        }
        return new CollectionOutcome(added, updated);
    }

    private String normalizeSkillKey(String skillName) {
        return skillName != null ? skillName.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String experienceKey(String company, String designation, LocalDate startDate) {
        return normalizeToken(company) + "|" + normalizeToken(designation) + "|" + startDate;
    }

    private String educationKey(String institution, String degree, LocalDate startDate) {
        return normalizeToken(institution) + "|" + normalizeToken(degree) + "|" + startDate;
    }

    private String normalizeToken(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeDistinctList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (String item : raw) {
            if (item != null && !item.isBlank()) {
                dedup.add(item.trim());
            }
        }
        return List.copyOf(dedup);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record SyncResult(ParsedResumeJsonDto normalizedApprovedData, MasterResumeSyncSummaryDto summary) {
    }

    private record ProfileUpsertOutcome(boolean created, boolean updated) {
    }

    private record CollectionOutcome(int added, int updated) {
    }
}

