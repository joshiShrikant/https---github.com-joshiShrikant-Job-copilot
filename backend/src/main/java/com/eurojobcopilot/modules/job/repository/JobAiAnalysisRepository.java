package com.eurojobcopilot.modules.job.repository;

import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobAiAnalysisRepository extends JpaRepository<JobAiAnalysis, Long> {

    Optional<JobAiAnalysis> findByJobId(Long jobId);

    List<JobAiAnalysis> findAllByJobIdIn(Collection<Long> jobIds);

    boolean existsByJobId(Long jobId);
}

