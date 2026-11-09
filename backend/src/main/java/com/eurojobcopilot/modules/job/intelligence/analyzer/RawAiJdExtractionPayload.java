package com.eurojobcopilot.modules.job.intelligence.analyzer;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Unvalidated JSON projection returned by the configured LLM provider prior to domain validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawAiJdExtractionPayload {

    @JsonAlias({"title", "extractedTitle"})
    private String jobTitle;

    @JsonAlias({"companyName", "extractedCompany"})
    private String company;

    @JsonAlias("extractedCountry")
    private String country;

    @JsonAlias("extractedLocation")
    private String location;

    private BigDecimal requiredExperienceYears;

    private String requiredExperienceLevel;

    private String requiredExperienceSummary;

    private List<String> requiredSkills;

    @JsonAlias("niceToHaveSkills")
    private List<String> preferredSkills;

    private List<String> responsibilities;

    private List<String> benefits;

    private List<String> languageRequirements;

    private BigDecimal salaryMin;

    private BigDecimal salaryMax;

    private String salaryCurrency;

    private String salaryPeriod;

    private String workArrangement;

    private String employmentType;

    private Boolean sponsorshipOffered;

    private String visaSponsorshipAnalysis;

    private Boolean relocationAssistanceOffered;

    private String relocationAnalysis;

    private String summary;
}

