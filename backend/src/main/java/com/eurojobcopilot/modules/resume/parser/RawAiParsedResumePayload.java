package com.eurojobcopilot.modules.resume.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tolerant deserialization target for raw LLM JSON responses prior to strict domain validation.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawAiParsedResumePayload {

    private RawProfile profile;
    private String summary;
    private List<RawSkill> skills;
    private List<RawExperience> experiences;
    private List<RawEducation> educations;
    private List<RawProject> projects;
    private List<String> languages;
    private List<String> certifications;
    private List<String> warnings;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawProfile {
        private String fullName;
        private String currentDesignation;
        private BigDecimal yearsOfExperience;
        private String currentCountry;
        private String currentCity;
        private String linkedinUrl;
        private String githubUrl;
        private String portfolioUrl;
        private String bio;
        private String workAuthorizationStatus;
        private String relocationPreference;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawSkill {
        private String skillName;
        private BigDecimal yearsExperience;
        private String proficiencyLevel;
        private Boolean primary;
        private Boolean isPrimary;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawExperience {
        private String companyName;
        private String designation;
        private String location;
        private String startDate;
        private String endDate;
        private Boolean current;
        private Boolean isCurrent;
        private String description;
        private List<String> skillsUsed;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawEducation {
        private String institution;
        private String degree;
        private String fieldOfStudy;
        private String startDate;
        private String endDate;
        private String gradeOrGpa;
        private String country;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawProject {
        private String name;
        private String description;
        private List<String> technologies;
        private String url;
    }
}

