package com.eurojobcopilot.modules.resume.tailored;

import com.eurojobcopilot.modules.resume.dto.TailoredResumeDataDto;

import java.math.BigDecimal;

public record ValidatedTailoredResume(
        TailoredResumeDataDto resumeData,
        BigDecimal atsKeywordCoverage
) {
}

