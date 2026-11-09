package com.eurojobcopilot.modules.assistant.entity;

import com.eurojobcopilot.core.audit.BaseAuditableEntity;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.job.entity.Job;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "job_assistant_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"user", "job", "assistantContent", "editableContent"})
public class JobAssistantMessage extends BaseAuditableEntity {

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
    @Column(name = "action_type", nullable = false, length = 60)
    private AssistantActionType actionType;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "user_prompt")
    private String userPrompt;

    @Column(name = "assistant_title", nullable = false, length = 255)
    private String assistantTitle;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "assistant_content", nullable = false)
    private String assistantContent;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "editable_content", nullable = false)
    private String editableContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "highlights_json")
    @Builder.Default
    private List<String> highlights = new ArrayList<>();

    @Column(name = "edited_by_user", nullable = false)
    @Builder.Default
    private boolean editedByUser = false;

    @Column(name = "ai_generated", nullable = false)
    @Builder.Default
    private boolean aiGenerated = true;

    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "ai_model_used", length = 100)
    private String aiModelUsed;

    @PrePersist
    protected void ensureDefaults() {
        if (this.uuid == null || this.uuid.isBlank()) {
            this.uuid = UUID.randomUUID().toString();
        }
        if (this.editableContent == null) {
            this.editableContent = this.assistantContent;
        }
        if (this.highlights == null) {
            this.highlights = new ArrayList<>();
        }
    }
}

