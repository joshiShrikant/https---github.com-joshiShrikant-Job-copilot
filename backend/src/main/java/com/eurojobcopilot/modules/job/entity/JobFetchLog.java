package com.eurojobcopilot.modules.job.entity;

import com.eurojobcopilot.core.audit.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "job_fetch_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class JobFetchLog extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FetchStatus status;

    @Column(name = "jobs_fetched", nullable = false)
    @Builder.Default
    private Integer jobsFetched = 0;

    @Column(name = "jobs_inserted", nullable = false)
    @Builder.Default
    private Integer jobsInserted = 0;

    @Column(name = "jobs_updated", nullable = false)
    @Builder.Default
    private Integer jobsUpdated = 0;

    @Column(name = "jobs_skipped_duplicates", nullable = false)
    @Builder.Default
    private Integer jobsSkippedDuplicates = 0;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "execution_duration_ms", nullable = false)
    @Builder.Default
    private Long executionDurationMs = 0L;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @PrePersist
    protected void ensureFetchedAt() {
        if (this.fetchedAt == null) {
            this.fetchedAt = Instant.now();
        }
    }
}

