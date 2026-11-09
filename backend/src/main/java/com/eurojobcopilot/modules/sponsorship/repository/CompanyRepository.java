package com.eurojobcopilot.modules.sponsorship.repository;

import com.eurojobcopilot.modules.sponsorship.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByNormalizedName(String normalizedName);

    Page<Company> findAllByVerifiedSponsorTrue(Pageable pageable);

    Page<Company> findAllByHeadquartersCountryIgnoreCase(String country, Pageable pageable);

    long countByVerifiedSponsorTrue();
}

