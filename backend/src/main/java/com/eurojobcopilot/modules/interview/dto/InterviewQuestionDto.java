package com.eurojobcopilot.modules.interview.dto;

import com.eurojobcopilot.modules.interview.entity.QuestionCategory;
import com.eurojobcopilot.modules.interview.entity.QuestionDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Interview practice question with ideal benchmark answer, candidate submission, and AI grading feedback")
public class InterviewQuestionDto {

    private Long id;
    private Integer orderIndex;
    private QuestionCategory category;
    private QuestionDifficulty difficulty;
    private String questionText;
    private String idealAnswer;
    private List<String> focusTags;
    private String userAnswer;
    private String aiFeedback;
    private BigDecimal score;
    private List<String> strongPoints;
    private List<String> missingElements;
    private Instant answeredAt;
    private String evaluationProvider;
    private String evaluationModelUsed;
}

