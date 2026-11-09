package com.eurojobcopilot.modules.job.normalization;

/**
 * Handles canonical SHA-256 deduplication, employer resolution, and upsert persistence for normalized jobs.
 */
public interface DeduplicationService {

    DeduplicationOutcome upsertNormalizedJob(NormalizedJobData normalizedJob);
}

