package com.eurojobcopilot.modules.coverletter.repository;

import com.eurojobcopilot.modules.coverletter.entity.CoverLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {

    Optional<CoverLetter> findByUuid(String uuid);

    Optional<CoverLetter> findByUuidAndUserId(String uuid, Long userId);

    List<CoverLetter> findAllByUserIdAndJobIdOrderByCreatedAtDesc(Long userId, Long jobId);

    @Query("""
            SELECT c FROM CoverLetter c
            JOIN FETCH c.job
            JOIN FETCH c.user
            LEFT JOIN FETCH c.tailoredResume
            WHERE c.uuid = :uuid AND c.user.id = :userId
            """)
    Optional<CoverLetter> findWithDetailsByUuidAndUserId(@Param("uuid") String uuid, @Param("userId") Long userId);

    @Query("""
            SELECT c FROM CoverLetter c
            JOIN FETCH c.job
            LEFT JOIN FETCH c.tailoredResume
            WHERE c.user.id = :userId
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<CoverLetter> findAllWithDetailsByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("""
            SELECT c FROM CoverLetter c
            JOIN FETCH c.job
            LEFT JOIN FETCH c.tailoredResume
            WHERE c.user.id = :userId AND c.job.id = :jobId
            ORDER BY c.versionNumber DESC, c.createdAt DESC
            """)
    List<CoverLetter> findAllWithDetailsByUserIdAndJobIdOrderByVersionDesc(
            @Param("userId") Long userId,
            @Param("jobId") Long jobId
    );

    @Query("""
            SELECT COALESCE(MAX(c.versionNumber), 0)
            FROM CoverLetter c
            WHERE c.user.id = :userId AND c.job.id = :jobId
            """)
    Integer findMaxVersionNumberByUserIdAndJobId(@Param("userId") Long userId, @Param("jobId") Long jobId);

    @Modifying
    @Query("UPDATE JobApplication a SET a.coverLetter = null WHERE a.coverLetter.id = :coverLetterId")
    void clearApplicationReferences(@Param("coverLetterId") Long coverLetterId);
}
