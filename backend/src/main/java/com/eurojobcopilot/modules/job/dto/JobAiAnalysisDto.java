package com.eurojobcopilot.modules.job.dto;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
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
@Schema(description = "Structured AI extraction of job metadata, requirements, compensation, sponsorship, relocation clues, and version history metadata")
public class JobAiAnalysisDto {

    @Schema(description = "Analysis record identifier", example = "18")
    private Long id;

    @Schema(description = "Target job identifier", example = "1042")
    private Long jobId;

    @Schema(description = "Sequential version number of this JD analysis for the job", example = "1")
    private Integer analysisVersion;

    @Schema(description = "Job title extracted by the AI analyzer", example = "Senior Java & Spring Boot Engineer")
    private String extractedTitle;

    @Schema(description = "Company name extracted by the AI analyzer", example = "Adyen N.V.")
    private String extractedCompany;

    @Schema(description = "Country extracted and normalized from the posting", example = "Netherlands")
    private String extractedCountry;

    @Schema(description = "City or full work location extracted from the posting", example = "Amsterdam, Netherlands")
    private String extractedLocation;

    @Schema(description = "Minimum years of required experience extracted from the JD", example = "5.0")
    private BigDecimal requiredExperienceYears;

    @Schema(description = "Required seniority classification extracted from the JD", example = "SENIOR")
    private ExperienceLevel requiredExperienceLevel;

    @Schema(description = "Human-readable summary of experience requirements extracted from the JD",
            example = "5+ years building distributed backend Java services")
    private String requiredExperienceSummary;

    @Schema(description = "Mandatory technical or domain skills extracted from the job description")
    private List<String> requiredSkills;

    @Schema(description = "Preferred or nice-to-have skills extracted from the job description")
    private List<String> niceToHaveSkills;

    @Schema(description = "Key day-to-day role responsibilities extracted from the job description")
    private List<String> responsibilities;

    @Schema(description = "Advertised benefits and perks extracted from the job description")
    private List<String> benefits;

    @Schema(description = "Spoken/written language requirements extracted from the job description")
    private List<String> languageRequirements;

    @Schema(description = "Lower salary bound extracted from the job description when stated", example = "85000.00")
    private BigDecimal salaryMin;

    @Schema(description = "Upper salary bound extracted from the job description when stated", example = "110000.00")
    private BigDecimal salaryMax;

    @Schema(description = "ISO 4217 currency code extracted for compensation", example = "EUR")
    private String salaryCurrency;

    @Schema(description = "Salary period extracted from the posting", example = "YEARLY")
    private SalaryPeriod salaryPeriod;

    @Schema(description = "Work arrangement extracted from the posting", example = "HYBRID")
    private WorkArrangement workArrangement;

    @Schema(description = "Employment contract type extracted from the posting", example = "FULL_TIME")
    private EmploymentType employmentType;

    @Schema(description = "Whether visa sponsorship appears explicitly offered (true), declined (false), or unmentioned (null)", example = "true")
    private Boolean sponsorshipOffered;

    @Schema(description = "AI assessment of visa sponsorship clues in the posting")
    private String visaSponsorshipAnalysis;

    @Schema(description = "Whether relocation assistance appears explicitly offered (true), declined (false), or unmentioned (null)", example = "true")
    private Boolean relocationAssistanceOffered;

    @Schema(description = "AI assessment of relocation support clues in the posting")
    private String relocationAnalysis;

    @Schema(description = "Concise AI executive summary of the role")
    private String summary;

    @Schema(description = "AI provider used for extraction (e.g. GEMINI, OPENAI, ANTHROPIC)", example = "GEMINI")
    private String aiProvider;

    @Schema(description = "Identifier of the AI model used for extraction", example = "gemini-2.5-flash")
    private String aiModelUsed;

    @Schema(description = "UTC timestamp when the structured extraction was performed")
    private Instant analyzedAt;
}

