package com.eurojobcopilot.modules.user.repository;

import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserWorkExperienceRepository extends JpaRepository<UserWorkExperience, Long> {

    List<UserWorkExperience> findAllByUserIdOrderByStartDateDesc(Long userId);

    Optional<UserWorkExperience> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    @Query("SELECT MAX(COALESCE(e.updatedAt, e.createdAt)) FROM UserWorkExperience e WHERE e.user.id = :userId")
    Optional<Instant> findLatestTimestampByUserId(@Param("userId") Long userId);
}

