package com.eurojobcopilot.modules.job.repository;

import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job>, JobSearchRepository {

    interface ActiveJobVersionView {
        Long getId();

        Instant getChangedAt();
    }

    @Query("""
            SELECT j.id AS id, COALESCE(j.updatedAt, j.createdAt) AS changedAt
            FROM Job j
            WHERE j.active = true
              AND (j.expiresAt IS NULL OR j.expiresAt >= :now)
            ORDER BY j.postedAt DESC, j.id DESC
            """)
    List<ActiveJobVersionView> findActiveNonExpiredVersions(@Param("now") Instant now, Pageable pageable);

    Optional<Job> findByCanonicalHash(String canonicalHash);

    boolean existsByCanonicalHash(String canonicalHash);

    Optional<Job> findBySourceProviderAndSourceJobId(String sourceProvider, String sourceJobId);

    @Query("SELECT j FROM Job j LEFT JOIN FETCH j.company WHERE j.id = :id")
    Optional<Job> findByIdWithCompany(@Param("id") Long id);

    @Query("""
            SELECT j FROM Job j
            LEFT JOIN FETCH j.company
            WHERE j.active = true
              AND (j.expiresAt IS NULL OR j.expiresAt >= :now)
            ORDER BY j.postedAt DESC, j.id DESC
            """)
    List<Job> findActiveNonExpiredWithCompany(@Param("now") Instant now, Pageable pageable);

    @Query("""
            SELECT j FROM Job j
            WHERE j.active = true
              AND (j.expiresAt IS NULL OR j.expiresAt >= :now)
              AND j.company.id IN :companyIds
            """)
    List<Job> findActiveNonExpiredByCompanyIdIn(@Param("companyIds") Collection<Long> companyIds, @Param("now") Instant now);

    @Query("""
            SELECT j FROM Job j
            WHERE j.active = true
              AND (j.expiresAt IS NULL OR j.expiresAt >= :now)
              AND j.company IS NULL
              AND LOWER(j.companyName) IN :companyNamesLower
            """)
    List<Job> findActiveNonExpiredUnlinkedByCompanyNameIn(
            @Param("companyNamesLower") Collection<String> companyNamesLower,
            @Param("now") Instant now
    );

    Page<Job> findAllByActiveTrueOrderByPostedAtDesc(Pageable pageable);

    List<Job> findAllByActiveTrue();

    Page<Job> findAllByActiveTrueAndCountryIgnoreCaseAndSponsorStatus(
            String country, JobSponsorStatus sponsorStatus, Pageable pageable
    );

    long countByActiveTrue();

    long countByActiveTrueAndPostedAtGreaterThanEqual(Instant postedSince);

    long countByActiveTrueAndSponsorStatusIn(Collection<JobSponsorStatus> sponsorStatuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Job j
            SET j.active = false, j.updatedAt = :now
            WHERE j.active = true
              AND ((j.expiresAt IS NOT NULL AND j.expiresAt < :now)
                   OR (j.expiresAt IS NULL AND j.postedAt < :staleCutoff))
            """)
    int deactivateExpiredOrStaleJobs(@Param("now") Instant now, @Param("staleCutoff") Instant staleCutoff);
}

