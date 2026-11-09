package com.eurojobcopilot.modules.job.repository;

import com.eurojobcopilot.modules.job.dto.JobSearchCriteria;
import com.eurojobcopilot.modules.job.dto.JobSortOption;
import com.eurojobcopilot.modules.job.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface JobSearchRepository {

    Page<Job> searchActiveJobs(
            Long userId,
            JobSearchCriteria criteria,
            String normalizedCountry,
            Pageable pageable,
            JobSortOption sortOption,
            Instant now
    );

    Page<Job> searchCompanyActiveJobs(
            Long userId,
            Long companyId,
            String companyName,
            boolean includeHidden,
            Pageable pageable,
            JobSortOption sortOption,
            Instant now
    );
}

