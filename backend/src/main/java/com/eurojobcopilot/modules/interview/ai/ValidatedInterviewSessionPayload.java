package com.eurojobcopilot.modules.interview.ai;

import com.eurojobcopilot.modules.interview.dto.CompanyPrepTopicDto;
import com.eurojobcopilot.modules.interview.entity.QuestionCategory;
import com.eurojobcopilot.modules.interview.entity.QuestionDifficulty;

import java.util.List;

public record ValidatedInterviewSessionPayload(
        String sessionTitle,
        List<CompanyPrepTopicDto> companyPrepTopics,
        List<ValidatedQuestionItem> questions,
        String overallPrepAdvice,
        List<String> improvementRecommendations
) {

    public record ValidatedQuestionItem(
            QuestionCategory category,
            QuestionDifficulty difficulty,
            String questionText,
            String idealAnswer,
            List<String> focusTags
    ) {
    }
}

