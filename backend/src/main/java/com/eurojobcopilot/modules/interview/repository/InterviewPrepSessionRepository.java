package com.eurojobcopilot.modules.interview.repository;

import com.eurojobcopilot.modules.interview.entity.InterviewPrepSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InterviewPrepSessionRepository extends JpaRepository<InterviewPrepSession, Long> {

    Optional<InterviewPrepSession> findByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT s FROM InterviewPrepSession s
            JOIN FETCH s.job j
            WHERE s.id = :id AND s.user.id = :userId
            """)
    Optional<InterviewPrepSession> findWithJobByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<InterviewPrepSession> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<InterviewPrepSession> findAllByUserIdAndJobIdOrderByCreatedAtDesc(Long userId, Long jobId);

    @Query("""
            SELECT s FROM InterviewPrepSession s
            JOIN FETCH s.job j
            WHERE s.user.id = :userId
            ORDER BY s.createdAt DESC, s.id DESC
            """)
    List<InterviewPrepSession> findAllWithJobByUserIdOrderByCreatedAtDescIdDesc(@Param("userId") Long userId);

    @Query("""
            SELECT s FROM InterviewPrepSession s
            JOIN FETCH s.job j
            WHERE s.user.id = :userId AND j.id = :jobId
            ORDER BY s.createdAt DESC, s.id DESC
            """)
    List<InterviewPrepSession> findAllWithJobByUserIdAndJobIdOrderByCreatedAtDescIdDesc(
            @Param("userId") Long userId,
            @Param("jobId") Long jobId
    );
}
