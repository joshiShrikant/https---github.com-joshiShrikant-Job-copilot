package com.eurojobcopilot.modules.user.repository;

import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserJobPreferenceRepository extends JpaRepository<UserJobPreference, Long> {

    Optional<UserJobPreference> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Query("""
            SELECT p FROM UserJobPreference p
            JOIN FETCH p.user u
            WHERE p.autopilotEnabled = true
              AND p.autopilotPaused = false
              AND u.accountStatus = com.eurojobcopilot.modules.auth.entity.AccountStatus.ACTIVE
            """)
    List<UserJobPreference> findAllActiveAutopilotSubscribers();
}
