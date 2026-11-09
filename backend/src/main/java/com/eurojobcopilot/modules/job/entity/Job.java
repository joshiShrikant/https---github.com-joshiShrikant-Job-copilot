package com.eurojobcopilot.modules.job.entity;

import com.eurojobcopilot.core.audit.BaseAuditableEntity;
import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
import java.util.Locale;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"company", "description"})
public class Job extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Include
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "canonical_hash", nullable = false, unique = true, length = 64)
    private String canonicalHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "normalized_title", nullable = false, length = 255)
    private String normalizedTitle;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_arrangement", nullable = false, length = 30)
    @Builder.Default
    private WorkArrangement workArrangement = WorkArrangement.ONSITE;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    @Builder.Default
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 50)
    @Builder.Default
    private ExperienceLevel experienceLevel = ExperienceLevel.MID;

    @Column(name = "min_experience_years", precision = 4, scale = 1)
    private BigDecimal minExperienceYears;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "salary_currency", length = 3)
    private String salaryCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", nullable = false, length = 20)
    @Builder.Default
    private SalaryPeriod salaryPeriod = SalaryPeriod.YEARLY;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "description_snippet", length = 500)
    private String descriptionSnippet;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills")
    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @Column(name = "source_provider", nullable = false, length = 50)
    private String sourceProvider;

    @Column(name = "source_job_id", length = 255)
    private String sourceJobId;

    @Column(name = "original_url", nullable = false, length = 1000)
    private String originalUrl;

    @Column(name = "application_url", length = 1000)
    private String applicationUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "sponsor_status", nullable = false, length = 50)
    @Builder.Default
    private JobSponsorStatus sponsorStatus = JobSponsorStatus.UNCONFIRMED;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @PrePersist
    @PreUpdate
    protected void normalize() {
        if (this.title != null && (this.normalizedTitle == null || this.normalizedTitle.isBlank())) {
            this.normalizedTitle = this.title.trim().toLowerCase(Locale.ROOT);
        }
    }
}

