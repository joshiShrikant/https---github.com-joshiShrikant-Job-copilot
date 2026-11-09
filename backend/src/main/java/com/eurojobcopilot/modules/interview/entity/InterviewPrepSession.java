package com.eurojobcopilot.modules.interview.entity;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "interview_prep_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"user", "job"})
public class InterviewPrepSession extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "session_title", nullable = false, length = 200)
    private String sessionTitle;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "overall_feedback")
    private String overallFeedback;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_categories_json")
    @Builder.Default
    private List<String> targetCategories = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "target_difficulty", nullable = false, length = 30)
    @Builder.Default
    private QuestionDifficulty targetDifficulty = QuestionDifficulty.MID;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "company_prep_topics_json")
    @Builder.Default
    private List<Map<String, String>> companyPrepTopics = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "improvement_recommendations_json")
    @Builder.Default
    private List<String> improvementRecommendations = new ArrayList<>();

    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "ai_model_used", length = 100)
    private String aiModelUsed;
}

