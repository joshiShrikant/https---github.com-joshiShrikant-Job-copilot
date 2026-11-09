package com.eurojobcopilot.modules.application.dto;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete tracked job application record including job metadata, linked documents, dates, offer, and chronological status timeline")
public class JobApplicationDetailResponse {

    @Schema(description = "Canonical application UUID", example = "7f21a1e8-b4d2-4d30-9f1a-45c7d9b8e110")
    private String uuid;

    @Schema(description = "Target job ID", example = "1042")
    private Long jobId;

    @Schema(description = "Job title", example = "Staff Java Clearing Architect")
    private String jobTitle;

    @Schema(description = "Resolved employer company ID if linked")
    private Long companyId;

    @Schema(description = "Employer company name", example = "Adyen N.V.")
    private String companyName;

    @Schema(description = "Target job country", example = "Netherlands")
    private String jobCountry;

    @Schema(description = "Target job city", example = "Amsterdam")
    private String jobCity;

    @Schema(description = "Target job display location", example = "Amsterdam, Netherlands")
    private String jobLocation;

    @Schema(description = "Work arrangement", example = "HYBRID")
    private WorkArrangement workArrangement;

    @Schema(description = "Employment contract type", example = "FULL_TIME")
    private EmploymentType employmentType;

    @Schema(description = "Visa sponsorship badge on job", example = "VERIFIED_SPONSOR")
    private JobSponsorStatus sponsorStatus;

    @Schema(description = "Advertised minimum salary from job listing")
    private BigDecimal jobSalaryMin;

    @Schema(description = "Advertised maximum salary from job listing")
    private BigDecimal jobSalaryMax;

    @Schema(description = "Advertised currency from job listing", example = "EUR")
    private String jobSalaryCurrency;

    @Schema(description = "Original external job listing URL")
    private String jobOriginalUrl;

    @Schema(description = "Direct application URL")
    private String jobApplicationUrl;

    @Schema(description = "Personalized match fit percentage (0-100) if scored", example = "89")
    private Integer matchPercentage;

    @Schema(description = "Current application pipeline stage", example = "TECH_INTERVIEW")
    private ApplicationStatus status;

    @Schema(description = "UTC timestamp when application was submitted")
    private Instant appliedDate;

    @Schema(description = "Linked tailored resume version used for this application")
    private ApplicationDocumentSummaryDto tailoredResume;

    @Schema(description = "Linked cover letter version used for this application")
    private ApplicationDocumentSummaryDto coverLetter;

    @Schema(description = "Salary offer received or proposed", example = "105000.00")
    private BigDecimal currentSalaryOffered;

    @Schema(description = "3-letter ISO currency code for offered salary", example = "EUR")
    private String offeredCurrency;

    @Schema(description = "Scheduled follow-up reminder date", example = "2026-11-18")
    private LocalDate nextFollowUpDate;

    @Schema(description = "Whether the current follow-up reminder has been completed")
    private boolean followUpCompleted;

    @Schema(description = "Computed indicator: true if nextFollowUpDate is in the past and followUpCompleted is false")
    private boolean overdueFollowUp;

    @Schema(description = "Scheduled next interview UTC timestamp")
    private Instant nextInterviewDate;

    @Schema(description = "Personal notes, compensation breakdown, and interview preparation")
    private String notes;

    @Schema(description = "Optimistic concurrency version counter", example = "2")
    private Integer version;

    @Schema(description = "Count of logged or drafted recruiter communications linked to this application", example = "2")
    private long recruiterInteractionsCount;

    @Schema(description = "Chronological history of status transitions (newest first)")
    private List<ApplicationStatusHistoryDto> statusHistory;

    @Schema(description = "Record creation UTC timestamp")
    private Instant createdAt;

    @Schema(description = "Last modification UTC timestamp")
    private Instant updatedAt;
}

