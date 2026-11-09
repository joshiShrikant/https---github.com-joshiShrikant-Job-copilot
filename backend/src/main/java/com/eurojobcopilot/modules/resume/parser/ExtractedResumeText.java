package com.eurojobcopilot.modules.resume.parser;

import java.util.List;

/**
 * Result of raw text extraction from a stored PDF or DOCX resume file.
 */
public record ExtractedResumeText(
        String text,
        int charCount,
        int pageCount,
        String format,
        List<String> warnings
) {
}

