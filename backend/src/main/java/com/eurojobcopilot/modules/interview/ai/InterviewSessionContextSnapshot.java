package com.eurojobcopilot.modules.interview.ai;

import com.eurojobcopilot.modules.interview.entity.QuestionCategory;
import com.eurojobcopilot.modules.interview.entity.QuestionDifficulty;

import java.math.BigDecimal;
import java.util.List;

public record InterviewSessionContextSnapshot(
        Long userId,
        Long jobId,
        String customSessionTitle,
        List<QuestionCategory> requestedCategories,
        QuestionDifficulty targetDifficulty,
        int questionCount,
        String focusNotes,
        String jobTitle,
        String companyName,
        String companyIndustry,
        String companyWebsite,
        boolean companyVerifiedSponsor,
        String country,
        String city,
        String experienceLevel,
        List<String> jobSkills,
        String jobDescription,
        String aiJobSummary,
        List<String> aiRequiredSkills,
        List<String> aiResponsibilities,
        String candidateDesignation,
        BigDecimal candidateYearsOfExperience,
        List<String> candidateTopSkills,
        List<String> candidateExperienceHighlights,
        String masterResumeSummary
) {
}

