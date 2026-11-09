package com.eurojobcopilot.modules.application.repository;

import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    Optional<JobApplication> findByUuid(String uuid);

    Optional<JobApplication> findByUuidAndUserId(String uuid, Long userId);

    Optional<JobApplication> findByUserIdAndJobId(Long userId, Long jobId);

    boolean existsByUserIdAndJobId(Long userId, Long jobId);

    List<JobApplication> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    Page<JobApplication> findAllByUserIdAndStatusOrderByUpdatedAtDesc(
            Long userId, ApplicationStatus status, Pageable pageable
    );

    long countByUserIdAndStatus(Long userId, ApplicationStatus status);

    long countByUserIdAndStatusNotIn(Long userId, Collection<ApplicationStatus> excludedStatuses);

    @Query("SELECT a.status, COUNT(a) FROM JobApplication a WHERE a.user.id = :userId GROUP BY a.status")
    List<Object[]> countGroupedByStatusForUser(@Param("userId") Long userId);

    @Query("SELECT a FROM JobApplication a JOIN FETCH a.job WHERE a.user.id = :userId ORDER BY a.updatedAt DESC")
    List<JobApplication> findRecentWithJob(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT a FROM JobApplication a
            JOIN FETCH a.job j
            LEFT JOIN FETCH j.company
            LEFT JOIN FETCH a.tailoredResume
            LEFT JOIN FETCH a.coverLetter
            WHERE a.user.id = :userId
            ORDER BY a.updatedAt DESC, a.id DESC
            """)
    List<JobApplication> findAllWithDetailsByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT a FROM JobApplication a
            JOIN FETCH a.job j
            LEFT JOIN FETCH j.company
            LEFT JOIN FETCH a.tailoredResume
            LEFT JOIN FETCH a.coverLetter
            WHERE a.uuid = :uuid AND a.user.id = :userId
            """)
    Optional<JobApplication> findWithDetailsByUuidAndUserId(@Param("uuid") String uuid, @Param("userId") Long userId);

    @Query("""
            SELECT a FROM JobApplication a
            JOIN FETCH a.job j
            LEFT JOIN FETCH j.company
            LEFT JOIN FETCH a.tailoredResume
            LEFT JOIN FETCH a.coverLetter
            WHERE a.user.id = :userId AND a.job.id = :jobId
            """)
    Optional<JobApplication> findWithDetailsByUserIdAndJobId(@Param("userId") Long userId, @Param("jobId") Long jobId);

    @Query("""
            SELECT a FROM JobApplication a
            JOIN FETCH a.job j
            WHERE a.user.id = :userId
              AND a.nextFollowUpDate IS NOT NULL
            ORDER BY a.nextFollowUpDate ASC, a.id ASC
            """)
    List<JobApplication> findAllWithFollowUpReminderByUserId(@Param("userId") Long userId);
}

