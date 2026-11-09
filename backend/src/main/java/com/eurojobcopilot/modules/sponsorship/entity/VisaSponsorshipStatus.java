package com.eurojobcopilot.modules.sponsorship.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Canonical 6-state classification for country- and employer/vacancy-aware visa sponsorship intelligence.
 */
@Getter
@RequiredArgsConstructor
public enum VisaSponsorshipStatus {

    VERIFIED_SPONSOR(
            "Verified sponsor",
            "Employer matches an active official government sponsor register; confirm whether each specific vacancy is eligible."
    ),
    SPONSORSHIP_MENTIONED(
            "Sponsorship explicitly mentioned",
            "Posting or source material explicitly mentions visa sponsorship or skilled work permit support."
    ),
    RELOCATION_ASSISTANCE_MENTIONED(
            "Relocation assistance mentioned",
            "Posting mentions relocation support or allowance, which does not automatically guarantee visa sponsorship."
    ),
    POSSIBLE_UNCONFIRMED(
            "Possible but unconfirmed",
            "Country permit pathways or international role signals indicate sponsorship may be possible, without official confirmation."
    ),
    NOT_MENTIONED(
            "Not mentioned",
            "Neither official sponsor registers nor available job/company evidence mention visa sponsorship."
    ),
    UNAVAILABLE(
            "Unavailable",
            "Vacancy or source explicitly states sponsorship is unavailable, requires existing local authorization, or accreditation is revoked."
    );

    private final String label;
    private final String description;
}

