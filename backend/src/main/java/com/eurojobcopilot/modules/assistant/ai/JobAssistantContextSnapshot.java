package com.eurojobcopilot.modules.assistant.ai;

import com.eurojobcopilot.modules.assistant.entity.AssistantActionType;

import java.math.BigDecimal;
import java.util.List;

public record JobAssistantContextSnapshot(
        Long userId,
        Long jobId,
        AssistantActionType actionType,
        String userPrompt,
        String additionalContext,
        CandidateEvidence candidate,
        JobEvidence job,
        SponsorshipEvidence sponsorship,
        MatchEvidence match,
        ApplicationEvidence application,
        List<ConversationTurnSummary> recentConversation
) {

    public record CandidateEvidence(
            String fullName,
            String currentDesignation,
            BigDecimal yearsOfExperience,
            String currentLocation,
            String workAuthorizationStatus,
            String relocationPreference,
            Integer noticePeriodDays,
            BigDecimal expectedSalaryMin,
            String expectedSalaryCurrency,
            boolean requiresSponsorship,
            boolean requiresRelocation,
            List<String> topSkills,
            List<String> experiencesSummary,
            List<String> educationSummary,
            String approvedMasterResumeTitle,
            String approvedMasterResumeSummary
    ) {
    }

    public record JobEvidence(
            String title,
            String companyName,
            String country,
            String city,
            String workArrangement,
            String employmentType,
            String experienceLevel,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            String salaryPeriod,
            String sponsorStatus,
            List<String> skills,
            String descriptionExcerpt,
            String aiAnalysisSummary,
            List<String> aiRequiredSkills,
            List<String> aiNiceToHaveSkills,
            List<String> aiResponsibilities,
            List<String> aiLanguageRequirements
    ) {
    }

    public record SponsorshipEvidence(
            String detailedStatus,
            String confidenceLevel,
            String summary,
            String countryGuideSummary,
            String vacancyCaveat,
            List<String> evidenceSignals,
            List<String> registryRecordNames
    ) {
    }

    public record MatchEvidence(
            Integer matchPercentage,
            BigDecimal deterministicScore,
            BigDecimal aiScore,
            String experienceCompatibility,
            List<String> matchingSkills,
            List<String> missingSkills,
            List<String> aiMissingSkills,
            List<String> aiPotentialConcerns,
            List<String> recommendationReasons
    ) {
    }

    public record ApplicationEvidence(
            String status,
            String appliedDate,
            String nextInterviewDate,
            String nextFollowUpDate
    ) {
    }

    public record ConversationTurnSummary(
            String actionLabel,
            String userPrompt,
            String assistantTitle,
            String editedContentSnippet
    ) {
    }
}

