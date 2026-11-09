package com.eurojobcopilot.modules.coverletter.generator;

import com.eurojobcopilot.modules.coverletter.dto.CoverLetterMetadataDto;

public record ValidatedCoverLetter(
        String content,
        Integer wordCount,
        CoverLetterMetadataDto metadata
) {
}

