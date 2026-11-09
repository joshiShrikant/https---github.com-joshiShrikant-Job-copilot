package com.eurojobcopilot.modules.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lightweight metadata for a tailored resume or cover letter linked to a job application")
public class ApplicationDocumentSummaryDto {

    @Schema(description = "Canonical UUID of the linked document", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uuid;

    @Schema(description = "Document title", example = "Staff Java · Adyen (v2)")
    private String title;

    @Schema(description = "Version number", example = "2")
    private Integer versionNumber;

    @Schema(description = "Template or tone badge subtitle", example = "MODERN")
    private String subtitle;

    @Schema(description = "Last update UTC timestamp")
    private Instant updatedAt;
}

