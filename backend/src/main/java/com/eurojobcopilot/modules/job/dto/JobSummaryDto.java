package com.eurojobcopilot.modules.job.dto;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Active job listing summary enriched with personalized match score and user interaction state")
public class JobSummaryDto {

    @Schema(description = "Unique job identifier", example = "1042")
    private Long id;

    @Schema(description = "Job title", example = "Senior Java & Spring Boot Backend Engineer")
    private String title;

    @Schema(description = "Employer name", example = "Adyen Engineering")
    private String companyName;

    @Schema(description = "Formatted location string", example = "Amsterdam, Netherlands")
    private String location;

    @Schema(description = "Normalized city name", example = "Amsterdam")
    private String city;

    @Schema(description = "Normalized country name", example = "Netherlands")
    private String country;

    @Schema(description = "Work arrangement classification", example = "HYBRID")
    private WorkArrangement workArrangement;

    @Schema(description = "Employment contract type", example = "FULL_TIME")
    private EmploymentType employmentType;

    @Schema(description = "Required seniority classification", example = "SENIOR")
    private ExperienceLevel experienceLevel;

    @Schema(description = "Lower salary bound when advertised", example = "85000.00")
    private BigDecimal salaryMin;

    @Schema(description = "Upper salary bound when advertised", example = "105000.00")
    private BigDecimal salaryMax;

    @Schema(description = "ISO 4217 3-letter currency code when salary is advertised", example = "EUR")
    private String salaryCurrency;

    @Schema(description = "Salary compensation period", example = "YEARLY")
    private SalaryPeriod salaryPeriod;

    @Schema(description = "Visa sponsorship verification status", example = "VERIFIED_SPONSOR")
    private JobSponsorStatus sponsorStatus;

    @Schema(description = "Rounded 0..100 match score for the current user, or null if not yet scored", example = "88")
    private Integer matchPercentage;

    @Schema(description = "Source job board provider identifier", example = "ARBEITNOW")
    private String sourceProvider;

    @Schema(description = "UTC publication timestamp")
    private Instant postedAt;

    @JsonProperty("isSaved")
    @Schema(name = "isSaved", description = "Whether the current user saved this job", example = "true")
    private boolean saved;

    @JsonProperty("isInterested")
    @Schema(name = "isInterested", description = "Whether the current user marked this job as interested", example = "false")
    private boolean interested;

    @JsonProperty("isHidden")
    @Schema(name = "isHidden", description = "Whether the current user hid this job from default discovery", example = "false")
    private boolean hidden;

    @Schema(description = "Extracted normalized technical skills")
    private List<String> skills;

    @Schema(description = "Short plain-text excerpt of the job description")
    private String descriptionSnippet;

    @Schema(description = "Canonical external job posting URL", example = "https://careers.example.eu/jobs/1042")
    private String originalUrl;
}

