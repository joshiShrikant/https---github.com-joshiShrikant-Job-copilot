package com.eurojobcopilot.modules.matching.entity;

import com.eurojobcopilot.core.audit.BaseAuditableEntity;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.job.entity.Job;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "job_match_scores",
        uniqueConstraints = @UniqueConstraint(name = "uk_match_scores_user_job", columnNames = {"user_id", "job_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"user", "job"})
public class JobMatchScore extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "deterministic_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal deterministicScore = BigDecimal.ZERO;

    @Column(name = "ai_score", precision = 5, scale = 2)
    private BigDecimal aiScore;

    @Column(name = "final_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal finalScore = BigDecimal.ZERO;

    @Column(name = "skill_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal skillScore = BigDecimal.ZERO;

    @Column(name = "experience_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal experienceScore = BigDecimal.ZERO;

    @Column(name = "title_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal titleScore = BigDecimal.ZERO;

    @Column(name = "location_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal locationScore = BigDecimal.ZERO;

    @Column(name = "sponsorship_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal sponsorshipScore = BigDecimal.ZERO;

    @Column(name = "salary_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal salaryScore = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matching_skills")
    @Builder.Default
    private List<String> matchingSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_skills")
    @Builder.Default
    private List<String> missingSkills = new ArrayList<>();

    @Column(name = "experience_compatibility", length = 255)
    private String experienceCompatibility;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendation_reasons")
    @Builder.Default
    private List<String> recommendationReasons = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "ai_rationale")
    private String aiRationale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_strong_matches")
    @Builder.Default
    private List<String> aiStrongMatches = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_missing_skills")
    @Builder.Default
    private List<String> aiMissingSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_experience_gaps")
    @Builder.Default
    private List<String> aiExperienceGaps = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_potential_concerns")
    @Builder.Default
    private List<String> aiPotentialConcerns = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_improvement_suggestions")
    @Builder.Default
    private List<String> aiImprovementSuggestions = new ArrayList<>();

    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "ai_model_used", length = 100)
    private String aiModelUsed;

    @Column(name = "ai_evaluated_at")
    private Instant aiEvaluatedAt;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @PrePersist
    protected void ensureCalculatedAt() {
        if (this.calculatedAt == null) {
            this.calculatedAt = Instant.now();
        }
    }
}

