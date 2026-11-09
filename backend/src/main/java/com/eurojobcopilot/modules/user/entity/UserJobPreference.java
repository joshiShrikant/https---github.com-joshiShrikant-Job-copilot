package com.eurojobcopilot.modules.user.entity;

import com.eurojobcopilot.core.audit.BaseAuditableEntity;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_job_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = "user")
public class UserJobPreference extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_job_titles", nullable = false)
    @Builder.Default
    private List<String> preferredJobTitles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_countries", nullable = false)
    @Builder.Default
    private List<String> preferredCountries = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_cities")
    @Builder.Default
    private List<String> preferredCities = new ArrayList<>();

    @Column(name = "min_salary", precision = 12, scale = 2)
    private BigDecimal minSalary;

    @Column(name = "salary_currency", nullable = false, length = 3)
    @Builder.Default
    private String salaryCurrency = "EUR";

    @Column(name = "requires_sponsorship", nullable = false)
    @Builder.Default
    private boolean requiresSponsorship = true;

    @Column(name = "requires_relocation", nullable = false)
    @Builder.Default
    private boolean requiresRelocation = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_arrangement", nullable = false, length = 30)
    @Builder.Default
    private WorkArrangement workArrangement = WorkArrangement.ANY;

    @Column(name = "english_speaking_only", nullable = false)
    @Builder.Default
    private boolean englishSpeakingOnly = true;

    @Column(name = "min_match_score", nullable = false)
    @Builder.Default
    private Integer minMatchScore = 60;

    @Column(name = "autopilot_enabled", nullable = false)
    @Builder.Default
    private boolean autopilotEnabled = false;

    @Column(name = "autopilot_paused", nullable = false)
    @Builder.Default
    private boolean autopilotPaused = false;

    @Column(name = "email_notifications_enabled", nullable = false)
    @Builder.Default
    private boolean emailNotificationsEnabled = false;

    @Column(name = "last_autopilot_run_at")
    private Instant lastAutopilotRunAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}

