package com.eurojobcopilot.modules.recruiter.entity;

import com.eurojobcopilot.core.audit.BaseAuditableEntity;
import com.eurojobcopilot.modules.application.entity.JobApplication;
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
import java.time.LocalDate;

@Entity
@Table(name = "recruiter_interactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"recruiter", "application", "messageBody"})
public class RecruiterInteraction extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private Recruiter recruiter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 40)
    @Builder.Default
    private CommunicationChannel channel = CommunicationChannel.LINKEDIN;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 50)
    @Builder.Default
    private RecruiterMessageType messageType = RecruiterMessageType.OUTREACH;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private RecruiterMessageStatus status = RecruiterMessageStatus.DRAFT;

    @Column(name = "subject_line", length = 255)
    private String subjectLine;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "message_body", nullable = false)
    private String messageBody;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "follow_up_reminder_date")
    private LocalDate followUpReminderDate;

    @Column(name = "reminder_completed", nullable = false)
    @Builder.Default
    private boolean reminderCompleted = false;

    @Column(name = "ai_generated", nullable = false)
    @Builder.Default
    private boolean aiGenerated = false;

    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "ai_model_used", length = 100)
    private String aiModelUsed;

    @Column(name = "edited_by_user", nullable = false)
    @Builder.Default
    private boolean editedByUser = false;
}

