package com.eurojobcopilot.modules.matching.ai.model;

import com.eurojobcopilot.modules.job.dto.JobMatchBreakdownDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Detached read-only snapshot of verified candidate qualifications, master resume, and target role data
 * assembled before invoking external AI providers.
 */
public record SemanticMatchContextSnapshot(
        Long userId,
        Long jobId,
        ProfileEvidence profile,
        PreferenceEvidence preference,
        List<SkillEvidence> skills,
        List<WorkExperienceEvidence> workExperiences,
        List<EducationEvidence> educations,
        ResumeEvidence masterResume,
        JobEvidence job,
        JobAiAnalysisEvidence jobAiAnalysis,
        JobMatchBreakdownDto cachedBreakdown
) {

    public SemanticMatchContextSnapshot {
        skills = skills == null ? List.of() : List.copyOf(skills);
        workExperiences = workExperiences == null ? List.of() : List.copyOf(workExperiences);
        educations = educations == null ? List.of() : List.copyOf(educations);
    }

    public boolean hasCandidateEvidence() {
        boolean hasProfile = profile != null && (
                hasText(profile.currentDesignation())
                        || hasText(profile.bio())
                        || (profile.yearsOfExperience() != null && profile.yearsOfExperience().compareTo(BigDecimal.ZERO) > 0)
                        || hasText(profile.currentCountry())
                        || hasText(profile.githubUrl())
                        || hasText(profile.portfolioUrl())
        );
        return hasProfile
                || !skills.isEmpty()
                || !workExperiences.isEmpty()
                || !educations.isEmpty()
                || masterResume != null;
    }

    public boolean hasJobContent() {
        return job != null && (
                hasText(job.sanitizedDescription())
                        || !job.skills().isEmpty()
                        || jobAiAnalysis != null
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ProfileEvidence(
            String displayName,
            String currentDesignation,
            BigDecimal yearsOfExperience,
            String currentCountry,
            String currentCity,
            BigDecimal expectedSalaryMin,
            String expectedSalaryCurrency,
            Integer noticePeriodDays,
            String workAuthorizationStatus,
            String relocationPreference,
            String linkedinUrl,
            String githubUrl,
            String portfolioUrl,
            String bio
    ) {
    }

    public record PreferenceEvidence(
            List<String> preferredJobTitles,
            List<String> preferredCountries,
            List<String> preferredCities,
            BigDecimal minSalary,
            String salaryCurrency,
            boolean requiresSponsorship,
            boolean requiresRelocation,
            String workArrangement,
            boolean englishSpeakingOnly
    ) {
        public PreferenceEvidence {
            preferredJobTitles = preferredJobTitles == null ? List.of() : List.copyOf(preferredJobTitles);
            preferredCountries = preferredCountries == null ? List.of() : List.copyOf(preferredCountries);
            preferredCities = preferredCities == null ? List.of() : List.copyOf(preferredCities);
        }
    }

    public record SkillEvidence(
            String skillName,
            BigDecimal yearsExperience,
            String proficiencyLevel,
            boolean primary
    ) {
    }

    public record WorkExperienceEvidence(
            String companyName,
            String designation,
            String location,
            LocalDate startDate,
            LocalDate endDate,
            boolean current,
            String description,
            List<String> skillsUsed
    ) {
        public WorkExperienceEvidence {
            skillsUsed = skillsUsed == null ? List.of() : List.copyOf(skillsUsed);
        }
    }

    public record EducationEvidence(
            String institution,
            String degree,
            String fieldOfStudy,
            LocalDate startDate,
            LocalDate endDate,
            String gradeOrGpa,
            String country
    ) {
    }

    public record ResumeEvidence(
            String uuid,
            String title,
            String fileName,
            Integer versionNumber,
            boolean masterApproved,
            Map<String, Object> parsedContentJson
    ) {
    }

    public record JobEvidence(
            String title,
            String companyName,
            String country,
            String city,
            String location,
            String workArrangement,
            String employmentType,
            String experienceLevel,
            BigDecimal minExperienceYears,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            String salaryPeriod,
            String sponsorStatus,
            boolean companyVerifiedSponsor,
            String companySponsorConfidence,
            String companyIndustry,
            List<String> skills,
            String sanitizedDescription
    ) {
        public JobEvidence {
            skills = skills == null ? List.of() : List.copyOf(skills);
        }
    }

    public record JobAiAnalysisEvidence(
            String requiredExperienceSummary,
            List<String> requiredSkills,
            List<String> niceToHaveSkills,
            List<String> responsibilities,
            List<String> languageRequirements,
            String visaSponsorshipAnalysis,
            String relocationAnalysis,
            String summary
    ) {
        public JobAiAnalysisEvidence {
            requiredSkills = requiredSkills == null ? List.of() : List.copyOf(requiredSkills);
            niceToHaveSkills = niceToHaveSkills == null ? List.of() : List.copyOf(niceToHaveSkills);
            responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
            languageRequirements = languageRequirements == null ? List.of() : List.copyOf(languageRequirements);
        }
    }
}

