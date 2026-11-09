package com.eurojobcopilot.modules.sponsorship.repository;

import com.eurojobcopilot.modules.sponsorship.entity.OfficialSponsorRegistryEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OfficialSponsorRegistryEntryRepository extends JpaRepository<OfficialSponsorRegistryEntry, Long> {

    Optional<OfficialSponsorRegistryEntry> findFirstByCountryIgnoreCaseAndNormalizedNameIgnoreCaseAndActiveTrueOrderBySyncedAtDesc(
            String country,
            String normalizedName
    );

    List<OfficialSponsorRegistryEntry> findAllByNormalizedNameIgnoreCaseAndActiveTrueOrderBySyncedAtDesc(
            String normalizedName
    );

    List<OfficialSponsorRegistryEntry> findAllByNormalizedNameInAndActiveTrueOrderBySyncedAtDesc(
            Collection<String> normalizedNames
    );

    Optional<OfficialSponsorRegistryEntry> findByRegistrySourceAndCountryAndNormalizedName(
            String registrySource,
            String country,
            String normalizedName
    );

    List<OfficialSponsorRegistryEntry> findAllByRegistrySourceAndCountryIgnoreCase(
            String registrySource,
            String country
    );

    long countByRegistrySourceAndActiveTrue(String registrySource);

    long countByCountryIgnoreCaseAndActiveTrue(String country);

    @Query("""
            SELECT e FROM OfficialSponsorRegistryEntry e
            WHERE e.registrySource = :registrySource
              AND e.active = true
              AND (:query IS NULL OR :query = ''
                   OR LOWER(e.officialName) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(e.normalizedName) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(COALESCE(e.registryIdentifier, '')) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY e.officialName ASC
            """)
    Page<OfficialSponsorRegistryEntry> searchActiveByRegistrySource(
            @Param("registrySource") String registrySource,
            @Param("query") String query,
            Pageable pageable
    );
}

