package com.eurojobcopilot.modules.job.ingestion;

import java.util.List;
import java.util.Optional;

/**
 * Coordinates provider polling, normalization, SHA-256 deduplication, concurrency gating, and audit logging.
 */
public interface JobIngestionService {

    IngestionExecutionSummary ingestAll(IngestionQuery query);

    ProviderIngestionResult ingestFromProvider(String providerName, IngestionQuery query);

    Optional<IngestionExecutionSummary> tryScheduledIngestAll(IngestionQuery query);

    boolean isRunning();

    IngestionRuntimeSnapshot getRuntimeSnapshot();

    List<ProviderRegistration> getRegisteredProviders();

    record ProviderRegistration(String providerName, boolean enabled) {
    }
}
