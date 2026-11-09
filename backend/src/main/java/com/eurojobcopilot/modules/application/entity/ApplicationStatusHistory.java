package com.eurojobcopilot.modules.application.entity;

import com.eurojobcopilot.core.audit.BaseCreatedEntity;
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
@Table(name = "application_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = "application")
public class ApplicationStatusHistory extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 40)
    private ApplicationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 40)
    private ApplicationStatus newStatus;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "transition_notes")
    private String transitionNotes;

    @Column(name = "transitioned_at", nullable = false)
    private Instant transitionedAt;

    @PrePersist
    protected void ensureTransitionedAt() {
        if (this.transitionedAt == null) {
            this.transitionedAt = Instant.now();
        }
    }
}

