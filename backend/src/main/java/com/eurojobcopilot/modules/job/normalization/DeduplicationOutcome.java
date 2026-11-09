package com.eurojobcopilot.modules.job.normalization;

/**
 * Result classification for a single job upsert during deduplication.
 */
public enum DeduplicationOutcome {
    INSERTED,
    UPDATED,
    SKIPPED_DUPLICATE
}

