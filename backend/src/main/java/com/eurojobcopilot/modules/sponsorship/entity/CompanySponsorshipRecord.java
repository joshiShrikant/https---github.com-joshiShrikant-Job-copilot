package com.eurojobcopilot.modules.sponsorship.entity;

import com.eurojobcopilot.core.audit.BaseAuditableEntity;
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
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = "company_sponsorship_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sponsorship_company_source",
                columnNames = {"company_id", "country", "registry_source"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = "company")
public class CompanySponsorshipRecord extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "registry_source", nullable = false, length = 100)
    private String registrySource;

    @Column(name = "registry_identifier", length = 150)
    private String registryIdentifier;

    @Column(name = "official_registered_name", nullable = false, length = 255)
    private String officialRegisteredName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private SponsorshipRecordStatus status = SponsorshipRecordStatus.RECOGNIZED_SPONSOR;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 30)
    @Builder.Default
    private SponsorConfidenceLevel confidenceLevel = SponsorConfidenceLevel.VERIFIED;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "evidence_notes")
    private String evidenceNotes;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;
}

