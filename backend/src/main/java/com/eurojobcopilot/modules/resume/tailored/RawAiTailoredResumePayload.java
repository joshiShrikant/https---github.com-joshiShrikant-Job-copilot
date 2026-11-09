package com.eurojobcopilot.modules.resume.tailored;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Lenient intermediate POJO for deserializing raw structured JSON returned by AI providers
 * before strict anti-hallucination grounding and deterministic ATS coverage calculation.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawAiTailoredResumePayload {

    private String headline;
    private String tailoredSummary;
    private List<String> highlightedSkills;
    private List<String> additionalSkills;
    private List<String> missingKeywords;
    private String tailoringRationale;
    private List<RawOptimizedExperience> optimizedExperiences;
    private List<RawTailoredProject> projects;
    private List<String> warnings;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawOptimizedExperience {
        private String companyName;
        private String designation;
        private String location;
        private String startDate;
        private String endDate;
        private Boolean current;
        private Boolean isCurrent;
        private List<String> tailoredBullets;
        private List<String> matchedKeywords;
        private Integer relevanceScore;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawTailoredProject {
        private String name;
        private String description;
        private List<String> tailoredBullets;
        private List<String> technologies;
        private String url;
    }
}

