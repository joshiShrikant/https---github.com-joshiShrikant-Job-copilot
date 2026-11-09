package com.eurojobcopilot.modules.job.entity;

import com.eurojobcopilot.core.audit.BaseCreatedEntity;
import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(
        name = "job_ai_analysis_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_ai_history_version",
                columnNames = {"job_id", "version_number"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"job", "triggeredByUser", "rawPayloadJson"})
public class JobAiAnalysisHistory extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by_user_id")
    private User triggeredByUser;

    @Column(name = "extracted_title", length = 255)
    private String extractedTitle;

    @Column(name = "extracted_company", length = 255)
    private String extractedCompany;

    @Column(name = "extracted_country", length = 100)
    private String extractedCountry;

    @Column(name = "extracted_location", length = 255)
    private String extractedLocation;

    @Column(name = "required_experience_years", precision = 4, scale = 1)
    private BigDecimal requiredExperienceYears;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_experience_level", length = 50)
    private ExperienceLevel requiredExperienceLevel;

    @Column(name = "required_experience_summary", length = 255)
    private String requiredExperienceSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_skills", nullable = false)
    @Builder.Default
    private List<String> requiredSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nice_to_have_skills")
    @Builder.Default
    private List<String> niceToHaveSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "responsibilities", nullable = false)
    @Builder.Default
    private List<String> responsibilities = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefits")
    @Builder.Default
    private List<String> benefits = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "language_requirements")
    @Builder.Default
    private List<String> languageRequirements = new ArrayList<>();

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "salary_currency", length = 3)
    private String salaryCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", length = 20)
    private SalaryPeriod salaryPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_arrangement", length = 30)
    private WorkArrangement workArrangement;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 30)
    private EmploymentType employmentType;

    @Column(name = "sponsorship_offered")
    private Boolean sponsorshipOffered;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "visa_sponsorship_analysis")
    private String visaSponsorshipAnalysis;

    @Column(name = "relocation_assistance_offered")
    private Boolean relocationAssistanceOffered;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "relocation_analysis")
    private String relocationAnalysis;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "summary")
    private String summary;

    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "ai_model_used", nullable = false, length = 100)
    private String aiModelUsed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload_json")
    @Builder.Default
    private Map<String, Object> rawPayloadJson = new LinkedHashMap<>();

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @PrePersist
    protected void ensureAnalyzedAt() {
        if (analyzedAt == null) {
            analyzedAt = Instant.now();
        }
    }
}

