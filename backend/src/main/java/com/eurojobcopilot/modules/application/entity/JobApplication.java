package com.eurojobcopilot.modules.application.entity;

import com.eurojobcopilot.core.audit.BaseAuditableEntity;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetter;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.resume.entity.TailoredResume;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "job_applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_applications_user_job", columnNames = {"user_id", "job_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"user", "job", "tailoredResume", "coverLetter"})
public class JobApplication extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Include
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, unique = true, length = 36, updatable = false)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.INTERESTED;

    @Column(name = "applied_date")
    private Instant appliedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tailored_resume_id")
    private TailoredResume tailoredResume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_letter_id")
    private CoverLetter coverLetter;

    @Column(name = "current_salary_offered", precision = 12, scale = 2)
    private BigDecimal currentSalaryOffered;

    @Column(name = "offered_currency", length = 3)
    private String offeredCurrency;

    @Column(name = "next_follow_up_date")
    private LocalDate nextFollowUpDate;

    @Column(name = "follow_up_completed", nullable = false)
    @Builder.Default
    private boolean followUpCompleted = false;

    @Column(name = "next_interview_date")
    private Instant nextInterviewDate;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "notes")
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @PrePersist
    protected void ensureUuid() {
        if (this.uuid == null || this.uuid.isBlank()) {
            this.uuid = UUID.randomUUID().toString();
        }
    }
}

