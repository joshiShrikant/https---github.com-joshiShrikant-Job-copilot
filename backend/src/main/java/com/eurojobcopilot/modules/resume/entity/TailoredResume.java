package com.eurojobcopilot.modules.resume.entity;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tailored_resumes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"masterResume", "job", "user", "resumeDataJson"})
public class TailoredResume extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Include
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, unique = true, length = 36, updatable = false)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "master_resume_id", nullable = false)
    private Resume masterResume;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_name", nullable = false, length = 50)
    @Builder.Default
    private ResumeTemplate templateName = ResumeTemplate.MODERN;

    @Column(name = "title", nullable = false, length = 200)
    @Builder.Default
    private String title = "Tailored Resume";

    @Column(name = "version_number", nullable = false)
    @Builder.Default
    private Integer versionNumber = 1;

    @Column(name = "tailoring_focus", length = 500)
    private String tailoringFocus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resume_data_json", nullable = false)
    @Builder.Default
    private Map<String, Object> resumeDataJson = new LinkedHashMap<>();

    @Column(name = "match_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal matchScore = BigDecimal.ZERO;

    @Column(name = "ats_keyword_coverage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal atsKeywordCoverage = BigDecimal.ZERO;

    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "ai_model_used", length = 100)
    private String aiModelUsed;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "edited_by_user", nullable = false)
    @Builder.Default
    private boolean editedByUser = false;

    @Column(name = "pdf_file_path", length = 500)
    private String pdfFilePath;

    @Column(name = "docx_file_path", length = 500)
    private String docxFilePath;

    @PrePersist
    protected void ensureDefaults() {
        if (this.uuid == null || this.uuid.isBlank()) {
            this.uuid = UUID.randomUUID().toString();
        }
        if (this.templateName == null) {
            this.templateName = ResumeTemplate.MODERN;
        }
        if (this.title == null || this.title.isBlank()) {
            this.title = "Tailored Resume";
        }
        if (this.versionNumber == null || this.versionNumber < 1) {
            this.versionNumber = 1;
        }
    }
}

