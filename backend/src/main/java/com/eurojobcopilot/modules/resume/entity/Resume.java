package com.eurojobcopilot.modules.resume.entity;

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
@Table(name = "resumes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"user", "parsedContentJson"})
public class Resume extends BaseAuditableEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "resume_type", nullable = false, length = 30)
    @Builder.Default
    private ResumeType resumeType = ResumeType.MASTER;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 50)
    @Builder.Default
    private StorageProvider storageProvider = StorageProvider.LOCAL;

    @Column(name = "version_number", nullable = false)
    @Builder.Default
    private Integer versionNumber = 1;

    @Column(name = "is_master_approved", nullable = false)
    @Builder.Default
    private boolean masterApproved = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_content_json")
    @Builder.Default
    private Map<String, Object> parsedContentJson = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 30)
    @Builder.Default
    private ResumeParseStatus parseStatus = ResumeParseStatus.NOT_PARSED;

    @Column(name = "parsed_at")
    private Instant parsedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "ai_model_used", length = 100)
    private String aiModelUsed;

    @Column(name = "extracted_char_count")
    private Integer extractedCharCount;

    @PrePersist
    protected void ensureUuid() {
        if (this.uuid == null || this.uuid.isBlank()) {
            this.uuid = UUID.randomUUID().toString();
        }
    }
}

