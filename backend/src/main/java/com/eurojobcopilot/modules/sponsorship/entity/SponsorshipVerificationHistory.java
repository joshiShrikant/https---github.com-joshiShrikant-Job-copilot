package com.eurojobcopilot.modules.sponsorship.entity;

import com.eurojobcopilot.core.audit.BaseCreatedEntity;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sponsorship_verification_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"company", "job", "triggeredByUser"})
public class SponsorshipVerificationHistory extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by_user_id")
    private User triggeredByUser;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "normalized_company_name", nullable = false, length = 255)
    private String normalizedCompanyName;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "country_iso_code", nullable = false, length = 2)
    private String countryIsoCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private VisaSponsorshipStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "vacancy_status", nullable = false, length = 50)
    private VisaSponsorshipStatus vacancyStatus;

    @Column(name = "vacancy_sponsorship_confirmed", nullable = false)
    @Builder.Default
    private boolean vacancySponsorshipConfirmed = false;

    @Column(name = "vacancy_sponsorship_assumed", nullable = false)
    @Builder.Default
    private boolean vacancySponsorshipAssumed = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 30)
    private SponsorConfidenceLevel confidenceLevel;

    @Column(name = "registry_source", length = 100)
    private String registrySource;

    @Column(name = "registry_identifier", length = 150)
    private String registryIdentifier;

    @Column(name = "official_registered_name", length = 255)
    private String officialRegisteredName;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "evidence_summary")
    private String evidenceSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_signals", nullable = false)
    @Builder.Default
    private List<String> evidenceSignals = new ArrayList<>();

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "vacancy_caveat")
    private String vacancyCaveat;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;
}

