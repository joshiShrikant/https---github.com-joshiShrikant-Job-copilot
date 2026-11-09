package com.eurojobcopilot.modules.recruiter.repository;

import com.eurojobcopilot.modules.recruiter.entity.RecruiterInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecruiterInteractionRepository extends JpaRepository<RecruiterInteraction, Long> {

    List<RecruiterInteraction> findAllByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);

    List<RecruiterInteraction> findAllByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    @Query("""
            SELECT i FROM RecruiterInteraction i
            JOIN FETCH i.recruiter r
            LEFT JOIN FETCH i.application a
            LEFT JOIN FETCH a.job j
            WHERE i.recruiter.id = :recruiterId
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    List<RecruiterInteraction> findAllWithDetailsByRecruiterId(@Param("recruiterId") Long recruiterId);

    @Query("""
            SELECT i FROM RecruiterInteraction i
            JOIN FETCH i.recruiter r
            LEFT JOIN FETCH i.application a
            LEFT JOIN FETCH a.job j
            WHERE i.id = :interactionId AND r.id = :recruiterId AND r.user.id = :userId
            """)
    Optional<RecruiterInteraction> findWithDetailsByIdAndRecruiterIdAndUserId(
            @Param("interactionId") Long interactionId,
            @Param("recruiterId") Long recruiterId,
            @Param("userId") Long userId
    );

    @Query("""
            SELECT i FROM RecruiterInteraction i
            JOIN FETCH i.recruiter r
            LEFT JOIN FETCH i.application a
            LEFT JOIN FETCH a.job j
            WHERE r.user.id = :userId
              AND i.followUpReminderDate IS NOT NULL
            ORDER BY i.followUpReminderDate ASC, i.id ASC
            """)
    List<RecruiterInteraction> findAllWithFollowUpReminderByUserId(@Param("userId") Long userId);

    @Query("SELECT i.application.id, COUNT(i) FROM RecruiterInteraction i WHERE i.application.id IN :applicationIds GROUP BY i.application.id")
    List<Object[]> countGroupedByApplicationIdIn(@Param("applicationIds") Collection<Long> applicationIds);

    @Query("SELECT i.recruiter.id, COUNT(i) FROM RecruiterInteraction i WHERE i.recruiter.id IN :recruiterIds GROUP BY i.recruiter.id")
    List<Object[]> countGroupedByRecruiterIdIn(@Param("recruiterIds") Collection<Long> recruiterIds);

    @Modifying
    @Query("UPDATE RecruiterInteraction i SET i.application = null WHERE i.application.id = :applicationId")
    void clearApplicationReferences(@Param("applicationId") Long applicationId);

    @Modifying
    @Query("DELETE FROM RecruiterInteraction i WHERE i.recruiter.id = :recruiterId")
    void deleteAllByRecruiterId(@Param("recruiterId") Long recruiterId);
}
