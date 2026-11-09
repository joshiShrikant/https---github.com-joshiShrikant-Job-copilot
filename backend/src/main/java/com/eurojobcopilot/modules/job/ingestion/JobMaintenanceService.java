package com.eurojobcopilot.modules.job.ingestion;

import com.eurojobcopilot.modules.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Performs transactional lifecycle maintenance for discovered job records (expiration & stale deactivation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobMaintenanceService {

    private final JobRepository jobRepository;

    @Transactional
    public int deactivateExpiredOrStaleJobs(int staleExpirationDays) {
        int effectiveDays = Math.max(1, staleExpirationDays);
        Instant now = Instant.now();
        Instant staleCutoff = now.minus(effectiveDays, ChronoUnit.DAYS);

        int deactivated = jobRepository.deactivateExpiredOrStaleJobs(now, staleCutoff);
        if (deactivated > 0) {
            log.info("Deactivated {} expired or stale job(s) (staleThresholdDays={})", deactivated, effectiveDays);
        }
        return deactivated;
    }
}

