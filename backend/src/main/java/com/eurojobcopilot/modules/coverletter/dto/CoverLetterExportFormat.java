package com.eurojobcopilot.modules.coverletter.dto;

import com.eurojobcopilot.core.exception.BusinessValidationException;

import java.util.Locale;

/**
 * Supported export formats for finalized AI cover letters.
 */
public enum CoverLetterExportFormat {

    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    TXT("txt", "text/plain; charset=UTF-8");

    private final String extension;
    private final String mimeType;

    CoverLetterExportFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String extension() {
        return extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public static CoverLetterExportFormat fromQueryParam(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessValidationException("Export format query parameter is required (supported: pdf, docx, txt)");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (CoverLetterExportFormat format : values()) {
            if (format.extension.equals(normalized)) {
                return format;
            }
        }
        throw new BusinessValidationException(
                "Unsupported export format '" + raw.trim() + "'. Supported formats: pdf, docx, txt"
        );
    }
}

