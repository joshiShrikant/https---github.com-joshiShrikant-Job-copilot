package com.eurojobcopilot.modules.sponsorship.catalog;

import java.util.List;

/**
 * Country-level regulatory and official government register profile for visa sponsorship intelligence.
 */
public record CountrySponsorshipProfile(
        String country,
        String isoCode,
        boolean officialRegisterAvailable,
        String registrySource,
        String registryName,
        String primaryAuthority,
        String officialSourceUrl,
        String publicRegisterUrl,
        String verificationMethod,
        List<String> visaPathways,
        String guidanceSummary,
        String vacancyRuleWarning
) {
}

