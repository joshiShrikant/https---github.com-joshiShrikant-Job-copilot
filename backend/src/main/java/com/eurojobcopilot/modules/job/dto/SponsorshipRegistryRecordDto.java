package com.eurojobcopilot.modules.job.dto;

import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
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
@Schema(description = "Official government or authoritative sponsorship registry evidence record")
public class SponsorshipRegistryRecordDto {

    @Schema(description = "Sponsorship record identifier", example = "42")
    private Long id;

    @Schema(description = "Jurisdiction country", example = "Netherlands")
    private String country;

    @Schema(description = "Authoritative registry source name", example = "NL_IND_PUBLIC_REGISTER")
    private String registrySource;

    @Schema(description = "Official registry license or Chamber identifier", example = "IND-34259528")
    private String registryIdentifier;

    @Schema(description = "Registered legal entity name in the official register", example = "Adyen N.V.")
    private String officialRegisteredName;

    @Schema(description = "Registry license status", example = "RECOGNIZED_SPONSOR")
    private SponsorshipRecordStatus status;

    @Schema(description = "Verification notes or license remarks")
    private String evidenceNotes;

    @Schema(description = "Reference URL to the public sponsor register")
    private String sourceUrl;

    @Schema(description = "UTC timestamp when this record was last verified")
    private Instant verifiedAt;
}

