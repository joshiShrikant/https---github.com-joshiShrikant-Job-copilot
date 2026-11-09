package com.eurojobcopilot.modules.job.dto;

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
@Schema(description = "User-scoped multi-factor job match breakdown combining deterministic and semantic AI scoring")
public class JobMatchBreakdownDto {

    @Schema(description = "Composite weighted final match score (0..100)", example = "88.50")
    private BigDecimal finalScore;

    @Schema(description = "Rounded percentage score for display gauges (0..100)", example = "89")
    private Integer matchPercentage;

    @Schema(description = "Deterministic rule-based compatibility score (0..100)", example = "86.00")
    private BigDecimal deterministicScore;

    @Schema(description = "Semantic AI compatibility score when evaluated (0..100)", example = "91.00")
    private BigDecimal aiScore;

    @Schema(description = "Technical skill overlap component score (0..100)", example = "90.00")
    private BigDecimal skillScore;

    @Schema(description = "Experience level & years component score (0..100)", example = "85.00")
    private BigDecimal experienceScore;

    @Schema(description = "Role title alignment component score (0..100)", example = "92.00")
    private BigDecimal titleScore;

    @Schema(description = "Country, city, and work arrangement preference score (0..100)", example = "100.00")
    private BigDecimal locationScore;

    @Schema(description = "Visa sponsorship preference fit score (0..100)", example = "100.00")
    private BigDecimal sponsorshipScore;

    @Schema(description = "Salary expectation compatibility score (0..100)", example = "80.00")
    private BigDecimal salaryScore;

    @Schema(description = "Candidate skills matched against job requirements")
    private List<String> matchingSkills;

    @Schema(description = "Required or desirable skills missing from candidate profile")
    private List<String> missingSkills;

    @Schema(description = "Human-readable compatibility verdict comparing candidate experience against job requirements",
            example = "Strong Fit · 6.0 yrs meets 5.0+ yrs (Senior)")
    private String experienceCompatibility;

    @Schema(description = "Deterministic rule-based recommendation reasons and missing-data notes")
    private List<String> recommendationReasons;

    @Schema(description = "AI semantic rationale explaining overall fit, evidence-backed strengths, and gaps when evaluated")
    private String aiRationale;

    @Schema(description = "Strong matching areas grounded in candidate profile, master resume, skills, experience, and projects")
    private List<String> aiStrongMatches;

    @Schema(description = "Missing or unverified skills highlighted by semantic comparison (kept separate from deterministic missingSkills)")
    private List<String> aiMissingSkills;

    @Schema(description = "Experience depth, seniority, scale, or domain gaps identified by AI comparison")
    private List<String> aiExperienceGaps;

    @Schema(description = "Potential red flags or practical concerns (e.g., visa sponsorship, missing resume text, relocation, stack mismatch)")
    private List<String> aiPotentialConcerns;

    @Schema(description = "Actionable, truthful improvement suggestions to strengthen fit before applying")
    private List<String> aiImprovementSuggestions;

    @Schema(description = "AI provider identifier used for semantic matching", example = "GEMINI")
    private String aiProvider;

    @Schema(description = "AI model identifier used for semantic matching", example = "gemini-2.5-flash")
    private String aiModelUsed;

    @Schema(description = "UTC timestamp when AI semantic comparison was last evaluated")
    private Instant aiEvaluatedAt;

    @Schema(description = "UTC timestamp when deterministic match scores were last computed")
    private Instant calculatedAt;
}

