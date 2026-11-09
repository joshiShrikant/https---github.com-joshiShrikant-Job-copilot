package com.eurojobcopilot.modules.sponsorship.entity;

import com.eurojobcopilot.core.audit.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Company extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @EqualsAndHashCode.Include
    @Column(name = "normalized_name", nullable = false, unique = true, length = 255)
    private String normalizedName;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "careers_url", length = 500)
    private String careersUrl;

    @Column(name = "headquarters_country", length = 100)
    private String headquartersCountry;

    @Column(name = "headquarters_city", length = 100)
    private String headquartersCity;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "company_size", length = 50)
    private String companySize;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tech_stack")
    @Builder.Default
    private List<String> techStack = new ArrayList<>();

    @Column(name = "is_verified_sponsor", nullable = false)
    @Builder.Default
    private boolean verifiedSponsor = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "sponsor_confidence_level", nullable = false, length = 30)
    @Builder.Default
    private SponsorConfidenceLevel sponsorConfidenceLevel = SponsorConfidenceLevel.UNVERIFIED;

    @PrePersist
    @PreUpdate
    protected void normalize() {
        if (this.name != null && (this.normalizedName == null || this.normalizedName.isBlank())) {
            this.normalizedName = this.name.trim().toLowerCase(Locale.ROOT);
        }
    }
}

