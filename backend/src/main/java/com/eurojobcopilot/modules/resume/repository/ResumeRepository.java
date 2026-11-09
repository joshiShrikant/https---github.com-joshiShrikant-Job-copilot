package com.eurojobcopilot.modules.resume.repository;

import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    Optional<Resume> findByUuid(String uuid);

    Optional<Resume> findByUuidAndUserId(String uuid, Long userId);

    List<Resume> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<Resume> findAllByUserIdOrderByVersionNumberDescCreatedAtDesc(Long userId);

    Optional<Resume> findFirstByUserIdAndMasterApprovedTrueOrderByVersionNumberDesc(Long userId);

    @Query("SELECT COALESCE(MAX(r.versionNumber), 0) FROM Resume r WHERE r.user.id = :userId")
    Integer findMaxVersionNumberByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndMasterApprovedTrue(Long userId);

    List<Resume> findAllByUserIdAndResumeTypeAndMasterApprovedTrue(Long userId, ResumeType resumeType);

    List<Resume> findTop3ByUserIdOrderByUpdatedAtDesc(Long userId);
}

