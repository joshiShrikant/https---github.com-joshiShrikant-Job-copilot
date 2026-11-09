package com.eurojobcopilot.modules.resume.dto;

import org.springframework.core.io.Resource;

/**
 * Wrapper carrying a readable resume Resource along with download HTTP headers metadata.
 */
public record ResumeDownloadResource(
        Resource resource,
        String fileName,
        String mimeType,
        long fileSizeBytes
) {
}

