package com.eurojobcopilot.modules.autopilot.repository;

import com.eurojobcopilot.modules.autopilot.entity.AutopilotRunLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AutopilotRunLogRepository extends JpaRepository<AutopilotRunLog, Long> {

    Page<AutopilotRunLog> findAllByUserIdOrderByStartedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<AutopilotRunLog> findFirstByUserIdOrderByStartedAtDescIdDesc(Long userId);
}

