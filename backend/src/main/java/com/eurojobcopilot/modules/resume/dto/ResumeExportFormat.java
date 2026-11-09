package com.eurojobcopilot.modules.resume.dto;

import com.eurojobcopilot.core.exception.BusinessValidationException;

import java.util.Locale;

/**
 * Supported binary document export formats for tailored CVs.
 */
public enum ResumeExportFormat {

    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final String extension;
    private final String mimeType;

    ResumeExportFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String extension() {
        return extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public static ResumeExportFormat fromQueryParam(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessValidationException("Export format query parameter is required (supported: pdf, docx)");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ResumeExportFormat format : values()) {
            if (format.extension.equals(normalized)) {
                return format;
            }
        }
        throw new BusinessValidationException(
                "Unsupported export format '" + raw.trim() + "'. Supported formats: pdf, docx"
        );
    }
}

