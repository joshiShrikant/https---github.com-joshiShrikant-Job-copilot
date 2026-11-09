package com.eurojobcopilot.modules.resume.tailored;

import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeResponse;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Detached read-only snapshot of verified candidate qualifications, master resume, target job,
 * and structured JD intelligence assembled before invoking external AI tailoring providers.
 */
public record TailoredResumeContextSnapshot(
        Long userId,
        Long jobId,
        ResumeTemplate templateName,
        String tailoringFocus,
        HeaderEvidence header,
        List<SkillEvidence> skills,
        List<WorkExperienceEvidence> workExperiences,
        List<EducationEvidence> educations,
        MasterResumeEvidence masterResume,
        JobEvidence job,
        JobAiAnalysisEvidence jobAiAnalysis,
        BigDecimal baselineMatchScore,
        TailoredResumeResponse cachedDraft
) {

    public TailoredResumeContextSnapshot {
        skills = skills == null ? List.of() : List.copyOf(skills);
        workExperiences = workExperiences == null ? List.of() : List.copyOf(workExperiences);
        educations = educations == null ? List.of() : List.copyOf(educations);
    }

    public TailoredResumeContextSnapshot withRawMasterResumeText(String extractedText) {
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
        return new TailoredResumeContextSnapshot(
                userId,
                jobId,
                templateName,
                tailoringFocus,
                header,
                skills,
                workExperiences,
                educations,
                enriched,
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

        return hasHeader
                || !skills.isEmpty()
                || !workExperiences.isEmpty()
                || !educations.isEmpty()
                || hasParsedMaster
                || hasRawMasterText;
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

    public record HeaderEvidence(
            String fullName,
            String email,
            String currentDesignation,
            BigDecimal yearsOfExperience,
            String currentCountry,
            String currentCity,
            String workAuthorizationStatus,
            String relocationPreference,
            String linkedinUrl,
            String githubUrl,
            String portfolioUrl,
            String bio
    ) {
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

