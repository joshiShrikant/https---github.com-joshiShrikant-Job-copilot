package com.eurojobcopilot.modules.sponsorship.entity;

import com.eurojobcopilot.core.audit.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = "official_sponsor_registry_entries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_official_registry_source_norm",
                columnNames = {"registry_source", "country", "normalized_name"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class OfficialSponsorRegistryEntry extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "registry_source", nullable = false, length = 100)
    private String registrySource;

    @Column(name = "registry_identifier", length = 150)
    private String registryIdentifier;

    @Column(name = "official_name", nullable = false, length = 255)
    private String officialName;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private SponsorshipRecordStatus status = SponsorshipRecordStatus.RECOGNIZED_SPONSOR;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "evidence_notes")
    private String evidenceNotes;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}

