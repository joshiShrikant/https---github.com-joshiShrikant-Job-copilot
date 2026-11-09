package com.eurojobcopilot.modules.job.repository;

import com.eurojobcopilot.modules.job.entity.FetchStatus;
import com.eurojobcopilot.modules.job.entity.JobFetchLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobFetchLogRepository extends JpaRepository<JobFetchLog, Long> {

    Page<JobFetchLog> findAllByOrderByFetchedAtDesc(Pageable pageable);

    Page<JobFetchLog> findAllByProviderNameOrderByFetchedAtDesc(String providerName, Pageable pageable);

    Page<JobFetchLog> findAllByProviderNameIgnoreCaseOrderByFetchedAtDesc(String providerName, Pageable pageable);

    Page<JobFetchLog> findAllByStatusOrderByFetchedAtDesc(FetchStatus status, Pageable pageable);

    Page<JobFetchLog> findAllByProviderNameIgnoreCaseAndStatusOrderByFetchedAtDesc(
            String providerName, FetchStatus status, Pageable pageable
    );

    Optional<JobFetchLog> findTopByProviderNameOrderByFetchedAtDesc(String providerName);

    boolean existsByStatus(FetchStatus status);
}

