package com.eurojobcopilot.modules.user.entity;

import com.eurojobcopilot.core.audit.BaseAuditableEntity;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = "user")
public class UserProfile extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "current_designation", length = 150)
    private String currentDesignation;

    @Column(name = "years_of_experience", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal yearsOfExperience = BigDecimal.ZERO;

    @Column(name = "current_country", length = 100)
    private String currentCountry;

    @Column(name = "current_city", length = 100)
    private String currentCity;

    @Column(name = "expected_salary_min", precision = 12, scale = 2)
    private BigDecimal expectedSalaryMin;

    @Column(name = "expected_salary_currency", nullable = false, length = 3)
    @Builder.Default
    private String expectedSalaryCurrency = "EUR";

    @Column(name = "notice_period_days", nullable = false)
    @Builder.Default
    private Integer noticePeriodDays = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_authorization_status", nullable = false, length = 50)
    @Builder.Default
    private WorkAuthorizationStatus workAuthorizationStatus = WorkAuthorizationStatus.NEEDS_SPONSORSHIP;

    @Enumerated(EnumType.STRING)
    @Column(name = "relocation_preference", nullable = false, length = 50)
    @Builder.Default
    private RelocationPreference relocationPreference = RelocationPreference.WILLING_TO_RELOCATE;

    @Column(name = "linkedin_url", length = 255)
    private String linkedinUrl;

    @Column(name = "github_url", length = 255)
    private String githubUrl;

    @Column(name = "portfolio_url", length = 255)
    private String portfolioUrl;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "bio")
    private String bio;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}

