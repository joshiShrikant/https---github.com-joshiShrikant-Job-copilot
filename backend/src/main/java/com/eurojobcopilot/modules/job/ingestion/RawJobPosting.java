package com.eurojobcopilot.modules.job.ingestion;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Unnormalized job payload emitted by an upstream {@link JobProvider} connector.
 */
@Builder
public record RawJobPosting(
        String sourceProvider,
        String sourceJobId,
        String title,
        String companyName,
        String location,
        String city,
        String country,
        String workArrangementHint,
        Boolean remoteFlag,
        String employmentTypeHint,
        String experienceLevelHint,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        String salaryPeriodHint,
        String rawDescription,
        List<String> tags,
        String originalUrl,
        String applicationUrl,
        Boolean visaSponsoredHint,
        Instant postedAt,
        Instant expiresAt
) {
    public RawJobPosting {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}

