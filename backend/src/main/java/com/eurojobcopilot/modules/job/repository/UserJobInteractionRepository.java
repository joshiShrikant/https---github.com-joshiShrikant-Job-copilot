package com.eurojobcopilot.modules.job.repository;

import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserJobInteractionRepository extends JpaRepository<UserJobInteraction, Long> {

    Optional<UserJobInteraction> findByUserIdAndJobId(Long userId, Long jobId);

    List<UserJobInteraction> findAllByUserIdAndJobIdIn(Long userId, Collection<Long> jobIds);

    Page<UserJobInteraction> findAllByUserIdAndSavedTrueOrderBySavedAtDesc(Long userId, Pageable pageable);

    Page<UserJobInteraction> findAllByUserIdAndInterestedTrueOrderByInterestedAtDesc(Long userId, Pageable pageable);

    Page<UserJobInteraction> findAllByUserIdAndHiddenTrue(Long userId, Pageable pageable);

    long countByUserIdAndSavedTrue(Long userId);

    long countByUserIdAndInterestedTrue(Long userId);

    @Query("SELECT u FROM UserJobInteraction u JOIN FETCH u.job WHERE u.user.id = :userId AND u.saved = true ORDER BY u.savedAt DESC")
    List<UserJobInteraction> findRecentSavedWithJob(@Param("userId") Long userId, Pageable pageable);
}

