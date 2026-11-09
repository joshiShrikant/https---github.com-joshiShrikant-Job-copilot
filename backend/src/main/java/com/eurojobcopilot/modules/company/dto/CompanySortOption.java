package com.eurojobcopilot.modules.company.dto;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum CompanySortOption {
    ACTIVE_JOBS_DESC("activeJobs,desc"),
    VERIFIED_DESC("verified,desc"),
    NAME_ASC("name,asc"),
    SAVED_AT_DESC("savedAt,desc");

    private final String paramValue;

    public static CompanySortOption fromParam(String rawSort) {
        if (rawSort == null || rawSort.isBlank()) {
            return ACTIVE_JOBS_DESC;
        }
        String normalized = rawSort.trim().toLowerCase(Locale.ROOT);
        for (CompanySortOption option : values()) {
            if (option.paramValue.toLowerCase(Locale.ROOT).equals(normalized)) {
                return option;
            }
        }
        String supported = Arrays.stream(values())
                .map(CompanySortOption::getParamValue)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        throw new BusinessValidationException(
                "Unsupported company sort parameter '" + rawSort + "'. Supported values: " + supported
        );
    }
}

