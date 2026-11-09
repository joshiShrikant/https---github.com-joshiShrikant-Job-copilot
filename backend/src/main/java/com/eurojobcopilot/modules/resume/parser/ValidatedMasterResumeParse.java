package com.eurojobcopilot.modules.resume.parser;

import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;

import java.util.List;

/**
 * Validated structured resume extraction output paired with human-review alerts.
 */
public record ValidatedMasterResumeParse(
        ParsedResumeJsonDto parsedData,
        List<String> warnings
) {
}

