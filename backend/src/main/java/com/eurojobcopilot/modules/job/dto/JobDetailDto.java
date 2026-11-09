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
@Schema(description = "Full job detail payload enriched with sponsorship intelligence, personalized match breakdown, AI analysis, and user interaction state")
public class JobDetailDto {

    @Schema(description = "Unique job identifier", example = "1042")
    private Long id;

    @Schema(description = "Job title", example = "Senior Java & Spring Boot Backend Engineer")
    private String title;

    @Schema(description = "Employer name", example = "Adyen Engineering")
    private String companyName;

    @Schema(description = "Normalized country name", example = "Netherlands")
    private String country;

    @Schema(description = "Normalized city name", example = "Amsterdam")
    private String city;

    @Schema(description = "Formatted location string", example = "Amsterdam, Netherlands")
    private String location;

    @Schema(description = "Work arrangement classification", example = "HYBRID")
    private WorkArrangement workArrangement;

    @Schema(description = "Employment contract type", example = "FULL_TIME")
    private EmploymentType employmentType;

    @Schema(description = "Required seniority classification", example = "SENIOR")
    private ExperienceLevel experienceLevel;

    @Schema(description = "Minimum years of experience mentioned or inferred", example = "5.0")
    private BigDecimal minExperienceYears;

    @Schema(description = "Lower salary bound when advertised", example = "85000.00")
    private BigDecimal salaryMin;

    @Schema(description = "Upper salary bound when advertised", example = "105000.00")
    private BigDecimal salaryMax;

    @Schema(description = "ISO 4217 3-letter currency code when salary is advertised", example = "EUR")
    private String salaryCurrency;

    @Schema(description = "Salary compensation period", example = "YEARLY")
    private SalaryPeriod salaryPeriod;

    @Schema(description = "Full sanitized job description text")
    private String description;

    @Schema(description = "Short plain-text excerpt of the job description")
    private String descriptionSnippet;

    @Schema(description = "Extracted normalized technical skills")
    private List<String> skills;

    @Schema(description = "Visa sponsorship verification status", example = "VERIFIED_SPONSOR")
    private JobSponsorStatus sponsorStatus;

    @Schema(description = "Rounded 0..100 match score for the current user, or null if unscored", example = "88")
    private Integer matchPercentage;

    @Schema(description = "Source job board provider identifier", example = "ARBEITNOW")
    private String sourceProvider;

    @Schema(description = "Provider-specific external job identifier", example = "adyen-java-1042")
    private String sourceJobId;

    @Schema(description = "Canonical external job posting URL", example = "https://careers.example.eu/jobs/1042")
    private String originalUrl;

    @Schema(description = "Direct employer application URL when distinct from originalUrl")
    private String applicationUrl;

    @Schema(description = "UTC publication timestamp")
    private Instant postedAt;

    @Schema(description = "Optional UTC expiration timestamp")
    private Instant expiresAt;

    @JsonProperty("isActive")
    @Schema(name = "isActive", description = "Whether the posting is currently active in discovery feeds", example = "true")
    private boolean active;

    @JsonProperty("isSaved")
    @Schema(name = "isSaved", description = "Whether the current user saved this job", example = "true")
    private boolean saved;

    @Schema(description = "UTC timestamp when the current user saved this job")
    private Instant savedAt;

    @JsonProperty("isInterested")
    @Schema(name = "isInterested", description = "Whether the current user marked this job as interested", example = "false")
    private boolean interested;

    @Schema(description = "UTC timestamp when the current user marked this job as interested")
    private Instant interestedAt;

    @JsonProperty("isHidden")
    @Schema(name = "isHidden", description = "Whether the current user hid this job from default discovery", example = "false")
    private boolean hidden;

    @Schema(description = "UTC timestamp when the current user hid this job")
    private Instant hiddenAt;

    @JsonProperty("hasApplied")
    @Schema(name = "hasApplied", description = "Whether an application action or tracker record exists for this job", example = "false")
    private boolean hasApplied;

    @Schema(description = "UTC timestamp of application confirmation when recorded")
    private Instant appliedAt;

    @Schema(description = "UTC timestamp when external application redirect was clicked")
    private Instant redirectClickedAt;

    @Schema(description = "Matched company profile and verified sponsor status when available")
    private CompanySponsorshipSummaryDto company;

    @Schema(description = "Comprehensive visa sponsorship evidence and official registry records")
    private SponsorshipBreakdownDto sponsorshipBreakdown;

    @Schema(description = "Detailed user-scoped compatibility sub-scores and skill gap breakdown when calculated")
    private JobMatchBreakdownDto matchBreakdown;

    @Schema(description = "Cached AI structured job-description extraction when available")
    private JobAiAnalysisDto aiAnalysis;

    @Schema(description = "Existing application pipeline summary for the current user when tracked")
    private JobApplicationSummaryDto application;
}

