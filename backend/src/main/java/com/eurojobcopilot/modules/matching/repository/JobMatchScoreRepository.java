package com.eurojobcopilot.modules.matching.repository;

import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobMatchScoreRepository extends JpaRepository<JobMatchScore, Long> {

    Optional<JobMatchScore> findByUserIdAndJobId(Long userId, Long jobId);

    List<JobMatchScore> findAllByUserIdAndJobIdIn(Long userId, Collection<Long> jobIds);

    long countByUserIdAndJobIdIn(Long userId, Collection<Long> jobIds);

    @Query("SELECT MIN(m.calculatedAt) FROM JobMatchScore m WHERE m.user.id = :userId AND m.job.id IN :jobIds")
    Optional<Instant> findMinCalculatedAtByUserIdAndJobIdIn(
            @Param("userId") Long userId,
            @Param("jobIds") Collection<Long> jobIds
    );

    Page<JobMatchScore> findAllByUserIdOrderByFinalScoreDesc(Long userId, Pageable pageable);

    Page<JobMatchScore> findAllByUserIdAndFinalScoreGreaterThanEqualOrderByFinalScoreDesc(
            Long userId, BigDecimal minScore, Pageable pageable
    );

    @Query("SELECT COUNT(m) FROM JobMatchScore m WHERE m.user.id = :userId AND m.finalScore >= :minScore AND m.job.active = true")
    long countActiveHighMatchJobs(@Param("userId") Long userId, @Param("minScore") BigDecimal minScore);
}

