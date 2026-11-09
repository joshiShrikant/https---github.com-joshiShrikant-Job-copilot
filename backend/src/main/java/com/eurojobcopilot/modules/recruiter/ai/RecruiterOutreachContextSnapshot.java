package com.eurojobcopilot.modules.recruiter.ai;

import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RecruiterOutreachContextSnapshot(
        CommunicationChannel channel,
        RecruiterMessageType messageType,
        String personalContext,
        CandidateEvidence candidate,
        RecruiterEvidence recruiter,
        JobAndApplicationEvidence jobContext
) {

    public record CandidateEvidence(
            String fullName,
            String currentDesignation,
            BigDecimal yearsOfExperience,
            String currentCity,
            String currentCountry,
            String workAuthorizationStatus,
            String relocationPreference,
            Integer noticePeriodDays,
            String bio,
            List<String> topSkills,
            List<ExperienceItem> workExperiences,
            String masterResumeSummary,
            boolean requiresSponsorship,
            boolean requiresRelocation
    ) {
    }

    public record ExperienceItem(
            String designation,
            String companyName,
            String summary
    ) {
    }

    public record RecruiterEvidence(
            Long recruiterId,
            String fullName,
            String roleTitle,
            String companyName,
            String notes,
            boolean verifiedSponsorCompany,
            List<String> companyTechStack,
            String companyIndustry
    ) {
    }

    public record JobAndApplicationEvidence(
            Long jobId,
            String jobTitle,
            String companyName,
            String location,
            String country,
            String applicationUuid,
            ApplicationStatus applicationStatus,
            Instant appliedDate,
            Instant nextInterviewDate,
            List<String> jobSkills,
            String sponsorStatus,
            String jdSummary
    ) {
    }

    public static List<String> extractParsedSkills(ParsedResumeJsonDto parsed) {
        if (parsed == null || parsed.getSkills() == null) {
            return List.of();
        }
        return parsed.getSkills().stream()
                .map(s -> s != null ? s.getSkillName() : null)
                .filter(s -> s != null && !s.isBlank())
                .limit(15)
                .toList();
    }
}

