package com.eurojobcopilot.modules.coverletter.entity;

import com.eurojobcopilot.core.audit.BaseAuditableEntity;
import com.eurojobcopilot.modules.auth.entity.User;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "cover_letters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"user", "job", "tailoredResume", "content", "metadataJson"})
public class CoverLetter extends BaseAuditableEntity {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tailored_resume_id")
    private TailoredResume tailoredResume;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "content", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "tone", nullable = false, length = 50)
    @Builder.Default
    private CoverLetterTone tone = CoverLetterTone.PROFESSIONAL;

    @Column(name = "version_number", nullable = false)
    @Builder.Default
    private Integer versionNumber = 1;

    @Column(name = "personal_notes", length = 1000)
    private String personalNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json")
    @Builder.Default
    private Map<String, Object> metadataJson = new LinkedHashMap<>();

    @Column(name = "word_count", nullable = false)
    @Builder.Default
    private Integer wordCount = 0;

    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "ai_model_used", length = 100)
    private String aiModelUsed;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "edited_by_user", nullable = false)
    @Builder.Default
    private boolean editedByUser = false;

    @PrePersist
    protected void ensureDefaults() {
        if (this.uuid == null || this.uuid.isBlank()) {
            this.uuid = UUID.randomUUID().toString();
        }
        if (this.tone == null) {
            this.tone = CoverLetterTone.PROFESSIONAL;
        }
        if (this.versionNumber == null || this.versionNumber < 1) {
            this.versionNumber = 1;
        }
        if (this.wordCount == null || this.wordCount < 0) {
            this.wordCount = countWords(this.content);
        }
        if (this.metadataJson == null) {
            this.metadataJson = new LinkedHashMap<>();
        }
    }

    private static int countWords(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        return raw.trim().split("\\s+").length;
    }
}
