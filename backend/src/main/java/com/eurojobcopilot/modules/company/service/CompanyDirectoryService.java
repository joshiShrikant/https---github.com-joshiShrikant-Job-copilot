package com.eurojobcopilot.modules.company.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.company.dto.CompanyDetailDto;
import com.eurojobcopilot.modules.company.dto.CompanySaveResponse;
import com.eurojobcopilot.modules.company.dto.CompanySearchCriteria;
import com.eurojobcopilot.modules.company.dto.PagedCompaniesResponse;
import com.eurojobcopilot.modules.company.dto.SaveCompanyRequest;
import com.eurojobcopilot.modules.job.dto.PagedJobsResponse;

public interface CompanyDirectoryService {

    PagedCompaniesResponse searchCompanies(AuthenticatedUserPrincipal currentUser, CompanySearchCriteria criteria);

    CompanyDetailDto getCompanyDetail(AuthenticatedUserPrincipal currentUser, Long companyId);

    PagedJobsResponse getCompanyJobs(
            AuthenticatedUserPrincipal currentUser,
            Long companyId,
            boolean includeHidden,
            int page,
            int size,
            String sort
    );

    CompanySaveResponse saveCompany(
            AuthenticatedUserPrincipal currentUser,
            Long companyId,
            SaveCompanyRequest request
    );

    CompanySaveResponse unsaveCompany(AuthenticatedUserPrincipal currentUser, Long companyId);
}

