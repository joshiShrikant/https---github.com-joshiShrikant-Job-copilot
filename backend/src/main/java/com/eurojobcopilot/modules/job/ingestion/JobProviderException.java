package com.eurojobcopilot.modules.job.ingestion;

/**
 * Unchecked exception signaling upstream HTTP, timeout, or schema failures from a {@link JobProvider}.
 */
public class JobProviderException extends RuntimeException {

    private final String providerName;

    public JobProviderException(String providerName, String message, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }
}

