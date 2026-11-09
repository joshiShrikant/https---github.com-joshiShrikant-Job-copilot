package com.eurojobcopilot.modules.assistant.repository;

import com.eurojobcopilot.modules.assistant.entity.JobAssistantMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobAssistantMessageRepository extends JpaRepository<JobAssistantMessage, Long> {

    List<JobAssistantMessage> findAllByUserIdAndJobIdOrderByCreatedAtAscIdAsc(Long userId, Long jobId);

    Optional<JobAssistantMessage> findByIdAndUserIdAndJobId(Long id, Long userId, Long jobId);
}

