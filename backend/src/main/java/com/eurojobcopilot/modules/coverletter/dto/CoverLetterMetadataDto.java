package com.eurojobcopilot.modules.coverletter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Structured grounding highlights, sender contact header, relocation framing, and verification warnings stored alongside a cover letter")
public class CoverLetterMetadataDto {

    @Schema(description = "Salutation line used to address the hiring team", example = "Dear Hiring Team at Adyen N.V.,")
    private String salutation;

    @Schema(description = "Closing sign-off block", example = "Sincerely,\nElena Weber")
    private String signOff;

    @Schema(description = "Verified candidate full name", example = "Elena Weber")
    private String candidateFullName;

    @Schema(description = "Verified candidate email", example = "elena.weber@eurojob.eu")
    private String candidateEmail;

    @Schema(description = "Verified candidate current city/country", example = "Berlin, Germany")
    private String candidateLocation;

    @Schema(description = "Verified candidate professional headline", example = "Senior Java Backend Engineer")
    private String candidateHeadline;

    @Schema(description = "Grounded European work authorization and visa context", example = "EU/EEA Citizen · Based in Europe · 30-day notice")
    private String workAuthorizationSummary;

    @Schema(description = "Relocation and notice-period alignment note for the target role country")
    private String relocationReadinessNote;

    @Schema(description = "How candidate accomplishments connect directly to the target company's mission or technical domain")
    private String companyConnectionSummary;

    @Schema(description = "Key factual achievement alignment points incorporated into the letter")
    private List<String> keyAlignmentPoints;

    @Schema(description = "Verified candidate skills referenced in the cover letter")
    private List<String> highlightedSkills;

    @Schema(description = "Anti-hallucination notices if unverified claims or authorization mismatches were flagged/filtered")
    private List<String> groundingWarnings;

    @Schema(description = "Computed word count of the letter body", example = "312")
    private Integer wordCount;
}

