package com.eurojobcopilot.modules.company.dto;

import lombok.Builder;

@Builder
public record CompanySearchCriteria(
        String query,
        String country,
        String industry,
        String tech,
        boolean verifiedSponsorOnly,
        boolean savedOnly,
        boolean withOpenJobsOnly,
        int page,
        int size,
        String sort
) {
}

