package com.eurojobcopilot.modules.job.ingestion;

import java.util.List;

/**
 * Service Provider Interface (SPI) implemented by every external job source connector.
 */
public interface JobProvider {

    /**
     * Canonical uppercase identifier recorded in {@code jobs.source_provider} and {@code job_fetch_logs.provider_name}.
     */
    String getProviderName();

    /**
     * Whether this connector is currently active and configured for polling.
     */
    boolean isEnabled();

    /**
     * Fetches raw job postings from the upstream source according to the requested query parameters.
     */
    List<RawJobPosting> fetchJobs(IngestionQuery query);

    /**
     * Optional capability hint indicating whether a provider services the given European country.
     */
    default boolean supportsCountry(String country) {
        return true;
    }
}

