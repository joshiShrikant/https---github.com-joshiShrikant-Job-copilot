package com.eurojobcopilot.modules.interview.entity;

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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "interview_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"session", "idealAnswer", "userAnswer", "aiFeedback"})
public class InterviewQuestion extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewPrepSession session;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private QuestionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 30)
    @Builder.Default
    private QuestionDifficulty difficulty = QuestionDifficulty.MID;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "question_text", nullable = false)
    private String questionText;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "ideal_answer")
    private String idealAnswer;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "user_answer")
    private String userAnswer;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "ai_feedback")
    private String aiFeedback;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "focus_tags_json")
    @Builder.Default
    private List<String> focusTags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strong_points_json")
    @Builder.Default
    private List<String> strongPoints = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_elements_json")
    @Builder.Default
    private List<String> missingElements = new ArrayList<>();

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "evaluation_provider", length = 50)
    private String evaluationProvider;

    @Column(name = "evaluation_model_used", length = 100)
    private String evaluationModelUsed;
}

