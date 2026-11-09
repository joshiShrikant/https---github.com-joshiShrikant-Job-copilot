package com.eurojobcopilot.modules.user.repository;

import com.eurojobcopilot.modules.user.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    List<UserSkill> findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(Long userId);

    Optional<UserSkill> findByIdAndUserId(Long id, Long userId);

    Optional<UserSkill> findByUserIdAndNormalizedSkill(Long userId, String normalizedSkill);

    boolean existsByUserIdAndNormalizedSkill(Long userId, String normalizedSkill);

    long countByUserId(Long userId);

    @Query("SELECT MAX(COALESCE(s.updatedAt, s.createdAt)) FROM UserSkill s WHERE s.user.id = :userId")
    Optional<Instant> findLatestTimestampByUserId(@Param("userId") Long userId);
}

