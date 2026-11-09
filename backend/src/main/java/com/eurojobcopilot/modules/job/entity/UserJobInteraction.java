package com.eurojobcopilot.modules.job.entity;

import com.eurojobcopilot.core.audit.BaseAuditableEntity;
import com.eurojobcopilot.modules.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@Entity
@Table(
        name = "user_job_interactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_interactions_user_job", columnNames = {"user_id", "job_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"user", "job"})
public class UserJobInteraction extends BaseAuditableEntity {

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

    @Column(name = "is_saved", nullable = false)
    @Builder.Default
    private boolean saved = false;

    @Column(name = "saved_at")
    private Instant savedAt;

    @Column(name = "is_interested", nullable = false)
    @Builder.Default
    private boolean interested = false;

    @Column(name = "interested_at")
    private Instant interestedAt;

    @Column(name = "is_hidden", nullable = false)
    @Builder.Default
    private boolean hidden = false;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "has_applied", nullable = false)
    @Builder.Default
    private boolean hasApplied = false;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "redirect_clicked_at")
    private Instant redirectClickedAt;
}

