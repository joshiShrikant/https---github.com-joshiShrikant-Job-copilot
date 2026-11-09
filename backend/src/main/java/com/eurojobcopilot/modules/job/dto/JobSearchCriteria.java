package com.eurojobcopilot.modules.job.dto;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record JobSearchCriteria(
        String query,
        String country,
        String city,
        WorkArrangement workArrangement,
        EmploymentType employmentType,
        ExperienceLevel experienceLevel,
        JobSponsorStatus sponsorStatus,
        Integer minMatchScore,
        BigDecimal minSalary,
        boolean savedOnly,
        boolean interestedOnly,
        boolean includeHidden,
        int page,
        int size,
        String sort
) {
}

