package com.eurojobcopilot.modules.user.entity;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Locale;

@Entity
@Table(
        name = "user_skills",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_skills_user_skill", columnNames = {"user_id", "normalized_skill"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = "user")
public class UserSkill extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Column(name = "normalized_skill", nullable = false, length = 100)
    private String normalizedSkill;

    @Column(name = "years_experience", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal yearsExperience = BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency_level", nullable = false, length = 30)
    @Builder.Default
    private ProficiencyLevel proficiencyLevel = ProficiencyLevel.INTERMEDIATE;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;

    @PrePersist
    @PreUpdate
    protected void normalize() {
        if (this.skillName != null) {
            this.skillName = this.skillName.trim();
            this.normalizedSkill = this.skillName.toLowerCase(Locale.ROOT);
        }
    }
}

