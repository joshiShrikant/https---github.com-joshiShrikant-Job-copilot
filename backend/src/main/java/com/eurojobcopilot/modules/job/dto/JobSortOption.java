package com.eurojobcopilot.modules.job.dto;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum JobSortOption {
    POSTED_AT_DESC("postedAt,desc"),
    POSTED_AT_ASC("postedAt,asc"),
    SALARY_MAX_DESC("salaryMax,desc"),
    MATCH_SCORE_DESC("matchScore,desc");

    private final String paramValue;

    public static JobSortOption fromParam(String rawSort) {
        if (rawSort == null || rawSort.isBlank()) {
            return POSTED_AT_DESC;
        }
        String normalized = rawSort.trim().toLowerCase(Locale.ROOT);
        for (JobSortOption option : values()) {
            if (option.paramValue.toLowerCase(Locale.ROOT).equals(normalized)) {
                return option;
            }
        }
        String supported = Arrays.stream(values())
                .map(JobSortOption::getParamValue)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        throw new BusinessValidationException(
                "Unsupported sort parameter '" + rawSort + "'. Supported values: " + supported
        );
    }
}

