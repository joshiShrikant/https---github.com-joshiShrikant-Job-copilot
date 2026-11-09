package com.eurojobcopilot.modules.job.normalization;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Validated, normalized job payload ready for canonical deduplication and persistence.
 */
@Builder
public record NormalizedJobData(
        String canonicalHash,
        String companyName,
        String normalizedCompanyName,
        String title,
        String normalizedTitle,
        String location,
        String city,
        String country,
        WorkArrangement workArrangement,
        EmploymentType employmentType,
        ExperienceLevel experienceLevel,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        SalaryPeriod salaryPeriod,
        String description,
        String descriptionSnippet,
        List<String> skills,
        String sourceProvider,
        String sourceJobId,
        String originalUrl,
        String applicationUrl,
        JobSponsorStatus sponsorStatus,
        Instant postedAt,
        Instant expiresAt
) {
    public NormalizedJobData {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }
}

