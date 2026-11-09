package com.eurojobcopilot.modules.job.intelligence.analyzer;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;

import java.math.BigDecimal;
import java.util.List;

/**
 * Strictly validated and normalized structured JD extraction ready for persistence.
 */
public record ValidatedJobDescriptionAnalysis(
        String extractedTitle,
        String extractedCompany,
        String extractedCountry,
        String extractedLocation,
        BigDecimal requiredExperienceYears,
        ExperienceLevel requiredExperienceLevel,
        String requiredExperienceSummary,
        List<String> requiredSkills,
        List<String> niceToHaveSkills,
        List<String> responsibilities,
        List<String> benefits,
        List<String> languageRequirements,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        SalaryPeriod salaryPeriod,
        WorkArrangement workArrangement,
        EmploymentType employmentType,
        Boolean sponsorshipOffered,
        String visaSponsorshipAnalysis,
        Boolean relocationAssistanceOffered,
        String relocationAnalysis,
        String summary
) {
}

