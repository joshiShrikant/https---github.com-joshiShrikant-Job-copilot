package com.eurojobcopilot.modules.coverletter.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Lenient intermediate POJO for deserializing structured JSON returned by AI providers
 * prior to strict anti-hallucination grounding verification and letter formatting.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawAiCoverLetterPayload {

    private String salutation;
    private String openingParagraph;
    private List<String> bodyParagraphs;
    private String relocationAndAuthorizationParagraph;
    private String closingParagraph;
    private String signOff;
    private String content;
    private String companyConnectionSummary;
    private List<String> keyAlignmentPoints;
    private List<String> highlightedSkills;
    private List<String> warnings;
}

