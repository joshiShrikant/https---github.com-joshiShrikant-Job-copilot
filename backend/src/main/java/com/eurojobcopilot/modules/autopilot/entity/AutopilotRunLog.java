package com.eurojobcopilot.modules.autopilot.entity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "autopilot_run_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"user", "errorMessage"})
public class AutopilotRunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private AutopilotTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AutopilotRunStatus status;

    @Column(name = "ingestion_triggered", nullable = false)
    @Builder.Default
    private boolean ingestionTriggered = false;

    @Column(name = "jobs_evaluated", nullable = false)
    @Builder.Default
    private int jobsEvaluated = 0;

    @Column(name = "new_matches_found", nullable = false)
    @Builder.Default
    private int newMatchesFound = 0;

    @Column(name = "recommended_jobs_count", nullable = false)
    @Builder.Default
    private int recommendedJobsCount = 0;

    @Column(name = "notifications_created", nullable = false)
    @Builder.Default
    private int notificationsCreated = 0;

    @Column(name = "email_dispatched", nullable = false)
    @Builder.Default
    private boolean emailDispatched = false;

    @Column(name = "summary_message", length = 500)
    private String summaryMessage;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void ensureStartedAt() {
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
    }
}

