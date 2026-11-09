package com.eurojobcopilot.modules.sponsorship.repository;

import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanySponsorshipRecordRepository extends JpaRepository<CompanySponsorshipRecord, Long> {

    List<CompanySponsorshipRecord> findAllByCompanyId(Long companyId);

    List<CompanySponsorshipRecord> findAllByCompanyIdOrderByVerifiedAtDesc(Long companyId);

    List<CompanySponsorshipRecord> findAllByCompanyIdInOrderByVerifiedAtDesc(Collection<Long> companyIds);

    Optional<CompanySponsorshipRecord> findByCompanyIdAndCountryIgnoreCaseAndRegistrySourceIgnoreCase(
            Long companyId, String country, String registrySource
    );

    List<CompanySponsorshipRecord> findAllByCountryIgnoreCaseAndStatus(String country, SponsorshipRecordStatus status);
}

