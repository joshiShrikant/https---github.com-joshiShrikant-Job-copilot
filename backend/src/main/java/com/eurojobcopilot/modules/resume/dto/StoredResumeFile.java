package com.eurojobcopilot.modules.resume.dto;

/**
 * Internal metadata returned by FileStorageService after persisting a validated upload.
 */
public record StoredResumeFile(
        String storageKey,
        String sanitizedFileName,
        String mimeType,
        long fileSizeBytes
) {
}

