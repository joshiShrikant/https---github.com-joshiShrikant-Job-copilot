package com.eurojobcopilot.modules.sponsorship.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "sponsorship_registry_sync_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SponsorshipRegistrySyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "registry_source", nullable = false, length = 100)
    private String registrySource;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RegistrySyncStatus status;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "entries_parsed", nullable = false)
    @Builder.Default
    private int entriesParsed = 0;

    @Column(name = "entries_upserted", nullable = false)
    @Builder.Default
    private int entriesUpserted = 0;

    @Column(name = "entries_deactivated", nullable = false)
    @Builder.Default
    private int entriesDeactivated = 0;

    @Column(name = "companies_linked", nullable = false)
    @Builder.Default
    private int companiesLinked = 0;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}

