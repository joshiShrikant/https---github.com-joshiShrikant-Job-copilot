package com.eurojobcopilot.modules.sponsorship.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.sponsorship.dto.CountrySponsorshipProfileDto;
import com.eurojobcopilot.modules.sponsorship.dto.PagedIndRegistryResponse;
import com.eurojobcopilot.modules.sponsorship.dto.PagedSponsorshipHistoryResponse;
import com.eurojobcopilot.modules.sponsorship.dto.SponsorshipVerificationResponseDto;

import java.util.List;

public interface SponsorshipIntelligenceService {

    List<CountrySponsorshipProfileDto> getCountryProfiles();

    SponsorshipVerificationResponseDto verifyCompanySponsorship(
            AuthenticatedUserPrincipal currentUser,
            String companyName,
            String country,
            Long jobId,
            String postingText
    );

    PagedSponsorshipHistoryResponse getVerificationHistory(
            AuthenticatedUserPrincipal currentUser,
            String companyName,
            String country,
            int page,
            int size
    );

    PagedIndRegistryResponse searchIndRegistry(
            AuthenticatedUserPrincipal currentUser,
            String query,
            int page,
            int size
    );
}

