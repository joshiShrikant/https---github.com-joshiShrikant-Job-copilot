package com.eurojobcopilot.modules.company.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CompanyPriorityTier {
    TOP_PRIORITY("Top Priority"),
    TARGET("Target Employer"),
    WATCHLIST("Watchlist");

    private final String label;
}

