package com.eurojobcopilot.modules.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Daily job discovery trend bucket for collected postings")
public class DiscoveryTrendPointDto {

    @Schema(description = "ISO-8601 UTC date", example = "2026-11-08")
    private String date;

    private long totalDiscovered;
    private long angularDiscovered;
    private long frontendDiscovered;
    private long javaDiscovered;
    private long fullStackDiscovered;
    private long sponsorshipDiscovered;
    private long remoteDiscovered;
}

