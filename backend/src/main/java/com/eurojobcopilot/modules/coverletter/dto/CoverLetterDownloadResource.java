package com.eurojobcopilot.modules.coverletter.dto;

import org.springframework.core.io.Resource;

public record CoverLetterDownloadResource(
        Resource resource,
        String fileName,
        String mimeType,
        long fileSizeBytes
) {
}

