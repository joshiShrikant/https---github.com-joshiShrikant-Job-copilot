package com.eurojobcopilot.modules.sponsorship.repository;

import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipVerificationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SponsorshipVerificationHistoryRepository extends JpaRepository<SponsorshipVerificationHistory, Long> {

    @Query("""
            SELECT h FROM SponsorshipVerificationHistory h
            WHERE h.triggeredByUser.id = :userId
              AND (:companyFilter IS NULL OR :companyFilter = ''
                   OR LOWER(h.companyName) LIKE LOWER(CONCAT('%', :companyFilter, '%'))
                   OR LOWER(h.normalizedCompanyName) LIKE LOWER(CONCAT('%', :companyFilter, '%')))
              AND (:country IS NULL OR :country = ''
                   OR LOWER(h.country) = LOWER(:country))
            ORDER BY h.verifiedAt DESC, h.id DESC
            """)
    Page<SponsorshipVerificationHistory> findUserHistoryFiltered(
            @Param("userId") Long userId,
            @Param("companyFilter") String companyFilter,
            @Param("country") String country,
            Pageable pageable
    );

    List<SponsorshipVerificationHistory> findTop5ByTriggeredByUserIdAndNormalizedCompanyNameAndCountryIgnoreCaseOrderByVerifiedAtDescIdDesc(
            Long userId,
            String normalizedCompanyName,
            String country
    );
}

