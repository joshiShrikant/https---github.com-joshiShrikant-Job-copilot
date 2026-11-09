package com.eurojobcopilot.modules.coverletter.generator;

import com.eurojobcopilot.modules.coverletter.dto.CoverLetterResponse;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeDataDto;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Detached read-only snapshot of verified candidate credentials, optional linked tailored CV
 * or master resume evidence, employer sponsorship context, and JD analysis gathered before
 * calling AI generation providers.
 */
public record CoverLetterContextSnapshot(
        Long userId,
        Long jobId,
        CoverLetterTone tone,
        String personalNotes,
        CandidateHeaderEvidence header,
        PreferenceEvidence preference,
        List<SkillEvidence> skills,
        List<WorkExperienceEvidence> workExperiences,
        List<EducationEvidence> educations,
        MasterResumeEvidence masterResume,
        TailoredResumeEvidence tailoredResume,
        JobEvidence job,
        JobAiAnalysisEvidence jobAiAnalysis,
        BigDecimal baselineMatchScore,
        CoverLetterResponse cachedDraft
) {

    public CoverLetterContextSnapshot {
        skills = skills == null ? List.of() : List.copyOf(skills);
        workExperiences = workExperiences == null ? List.of() : List.copyOf(workExperiences);
        educations = educations == null ? List.of() : List.copyOf(educations);
    }

    public CoverLetterContextSnapshot withRawMasterResumeText(String extractedText) {
        if (masterResume == null) {
            return this;
        }
        MasterResumeEvidence enriched = new MasterResumeEvidence(
                masterResume.id(),
                masterResume.uuid(),
                masterResume.title(),
                masterResume.fileName(),
                masterResume.mimeType(),
                masterResume.filePath(),
                masterResume.versionNumber(),
                masterResume.masterApproved(),
                masterResume.parsedResume(),
                extractedText
        );
        return new CoverLetterContextSnapshot(
                userId,
                jobId,
                tone,
                personalNotes,
                header,
                preference,
                skills,
                workExperiences,
                educations,
                enriched,
                tailoredResume,
                job,
                jobAiAnalysis,
                baselineMatchScore,
                cachedDraft
        );
    }

    public boolean hasCandidateEvidence() {
        boolean hasHeader = header != null && (
                hasText(header.currentDesignation())
                        || hasText(header.bio())
                        || (header.yearsOfExperience() != null
                        && header.yearsOfExperience().compareTo(BigDecimal.ZERO) > 0)
                        || hasText(header.currentCountry())
        );
        boolean hasParsedMaster = masterResume != null && masterResume.parsedResume() != null;
        boolean hasRawMasterText = masterResume != null && hasText(masterResume.rawExtractedText());
        boolean hasTailoredData = tailoredResume != null && tailoredResume.resumeData() != null
                && (hasText(tailoredResume.resumeData().getTailoredSummary())
                || (tailoredResume.resumeData().getHighlightedSkills() != null
                && !tailoredResume.resumeData().getHighlightedSkills().isEmpty())
                || (tailoredResume.resumeData().getOptimizedExperiences() != null
                && !tailoredResume.resumeData().getOptimizedExperiences().isEmpty()));

        return hasHeader
                || !skills.isEmpty()
                || !workExperiences.isEmpty()
                || !educations.isEmpty()
                || hasParsedMaster
                || hasRawMasterText
                || hasTailoredData;
    }

    public boolean hasJobContent() {
        return job != null && (
                hasText(job.title())
                        || hasText(job.sanitizedDescription())
                        || !job.skills().isEmpty()
                        || jobAiAnalysis != null
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record CandidateHeaderEvidence(
            String fullName,
            String email,
            String currentDesignation,
            BigDecimal yearsOfExperience,
            String currentCountry,
            String currentCity,
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
            List<String> preferredCountries,
            List<String> preferredCities,
            boolean requiresSponsorship,
            boolean requiresRelocation,
            String workArrangement
    ) {
        public PreferenceEvidence {
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

    public record MasterResumeEvidence(
            Long id,
            String uuid,
            String title,
            String fileName,
            String mimeType,
            String filePath,
            Integer versionNumber,
            boolean masterApproved,
            ParsedResumeJsonDto parsedResume,
            String rawExtractedText
    ) {
    }

    public record TailoredResumeEvidence(
            Long id,
            String uuid,
            String title,
            ResumeTemplate templateName,
            String tailoringFocus,
            BigDecimal atsKeywordCoverage,
            TailoredResumeDataDto resumeData
    ) {
    }

    public record JobEvidence(
            Long id,
            String title,
            String companyName,
            String country,
            String city,
            String location,
            String workArrangement,
            String employmentType,
            String experienceLevel,
            BigDecimal minExperienceYears,
            String sponsorStatus,
            String companyIndustry,
            String companyDescription,
            String companySize,
            List<String> companyTechStack,
            boolean verifiedSponsor,
            String sponsorConfidenceLevel,
            List<String> skills,
            String sanitizedDescription
    ) {
        public JobEvidence {
            companyTechStack = companyTechStack == null ? List.of() : List.copyOf(companyTechStack);
            skills = skills == null ? List.of() : List.copyOf(skills);
        }
    }

    public record JobAiAnalysisEvidence(
            String requiredExperienceSummary,
            List<String> requiredSkills,
            List<String> niceToHaveSkills,
            List<String> responsibilities,
            List<String> languageRequirements,
            Boolean sponsorshipOffered,
            String visaSponsorshipAnalysis,
            Boolean relocationAssistanceOffered,
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

