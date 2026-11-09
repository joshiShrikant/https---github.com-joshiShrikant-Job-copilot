package com.eurojobcopilot.modules.job.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobDetailDto;
import com.eurojobcopilot.modules.job.dto.JobSearchCriteria;
import com.eurojobcopilot.modules.job.dto.PagedJobsResponse;

public interface JobQueryService {

    PagedJobsResponse searchJobs(AuthenticatedUserPrincipal currentUser, JobSearchCriteria criteria);

    PagedJobsResponse getCompanyActiveJobs(
            AuthenticatedUserPrincipal currentUser,
            Long companyId,
            String companyName,
            boolean includeHidden,
            int page,
            int size,
            String sort
    );

    JobDetailDto getJobDetail(AuthenticatedUserPrincipal currentUser, Long jobId);
}

