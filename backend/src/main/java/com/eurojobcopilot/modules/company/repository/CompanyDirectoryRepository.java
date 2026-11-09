package com.eurojobcopilot.modules.company.repository;

import com.eurojobcopilot.modules.company.dto.CompanySearchCriteria;
import com.eurojobcopilot.modules.company.dto.CompanySortOption;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface CompanyDirectoryRepository {

    Page<Company> searchCompanies(
            Long userId,
            CompanySearchCriteria criteria,
            String normalizedCountry,
            Pageable pageable,
            CompanySortOption sortOption,
            Instant now
    );

    long countVerifiedSponsorCompanies();
}

