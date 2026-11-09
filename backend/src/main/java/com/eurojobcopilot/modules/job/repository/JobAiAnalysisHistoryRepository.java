package com.eurojobcopilot.modules.job.repository;

import com.eurojobcopilot.modules.job.entity.JobAiAnalysisHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobAiAnalysisHistoryRepository extends JpaRepository<JobAiAnalysisHistory, Long> {

    List<JobAiAnalysisHistory> findAllByJobIdOrderByVersionNumberDesc(Long jobId);

    Optional<JobAiAnalysisHistory> findTopByJobIdOrderByVersionNumberDesc(Long jobId);

    long countByJobId(Long jobId);
}

