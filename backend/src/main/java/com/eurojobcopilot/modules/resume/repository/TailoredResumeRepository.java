package com.eurojobcopilot.modules.resume.repository;

import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import com.eurojobcopilot.modules.resume.entity.TailoredResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TailoredResumeRepository extends JpaRepository<TailoredResume, Long> {

    Optional<TailoredResume> findByUuid(String uuid);

    Optional<TailoredResume> findByUuidAndUserId(String uuid, Long userId);

    @Query("""
            SELECT t FROM TailoredResume t
            JOIN FETCH t.masterResume
            JOIN FETCH t.job
            JOIN FETCH t.user
            WHERE t.uuid = :uuid AND t.user.id = :userId
            """)
    Optional<TailoredResume> findWithDetailsByUuidAndUserId(@Param("uuid") String uuid, @Param("userId") Long userId);

    @Query("""
            SELECT t FROM TailoredResume t
            JOIN FETCH t.masterResume
            JOIN FETCH t.job
            WHERE t.user.id = :userId
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    List<TailoredResume> findAllWithDetailsByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("""
            SELECT t FROM TailoredResume t
            JOIN FETCH t.masterResume
            JOIN FETCH t.job
            WHERE t.user.id = :userId AND t.job.id = :jobId
            ORDER BY t.versionNumber DESC, t.createdAt DESC
            """)
    List<TailoredResume> findAllWithDetailsByUserIdAndJobIdOrderByVersionDesc(
            @Param("userId") Long userId,
            @Param("jobId") Long jobId
    );

    List<TailoredResume> findAllByUserIdAndJobIdOrderByCreatedAtDesc(Long userId, Long jobId);

    Optional<TailoredResume> findFirstByUserIdAndJobIdAndMasterResumeIdAndTemplateNameOrderByVersionNumberDescCreatedAtDesc(
            Long userId,
            Long jobId,
            Long masterResumeId,
            ResumeTemplate templateName
    );

    @Query("""
            SELECT COALESCE(MAX(t.versionNumber), 0)
            FROM TailoredResume t
            WHERE t.user.id = :userId AND t.job.id = :jobId
            """)
    Integer findMaxVersionNumberByUserIdAndJobId(@Param("userId") Long userId, @Param("jobId") Long jobId);

    List<TailoredResume> findAllByMasterResumeId(Long masterResumeId);

    boolean existsByMasterResumeId(Long masterResumeId);

    @Modifying
    @Query("UPDATE JobApplication a SET a.tailoredResume = null WHERE a.tailoredResume.id = :tailoredResumeId")
    void clearApplicationReferences(@Param("tailoredResumeId") Long tailoredResumeId);

    @Modifying
    @Query("UPDATE CoverLetter c SET c.tailoredResume = null WHERE c.tailoredResume.id = :tailoredResumeId")
    void clearCoverLetterReferences(@Param("tailoredResumeId") Long tailoredResumeId);
}
