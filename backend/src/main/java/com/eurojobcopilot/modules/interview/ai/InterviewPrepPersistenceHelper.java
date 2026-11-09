package com.eurojobcopilot.modules.interview.ai;

import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.interview.ai.ValidatedInterviewSessionPayload.ValidatedQuestionItem;
import com.eurojobcopilot.modules.interview.dto.CompanyPrepTopicDto;
import com.eurojobcopilot.modules.interview.entity.InterviewPrepSession;
import com.eurojobcopilot.modules.interview.entity.InterviewQuestion;
import com.eurojobcopilot.modules.interview.entity.QuestionCategory;
import com.eurojobcopilot.modules.interview.entity.QuestionDifficulty;
import com.eurojobcopilot.modules.interview.repository.InterviewPrepSessionRepository;
import com.eurojobcopilot.modules.interview.repository.InterviewQuestionRepository;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class InterviewPrepPersistenceHelper {

    public record LoadedSessionData(InterviewPrepSession session, List<InterviewQuestion> questions) {
    }

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final InterviewPrepSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public AiJsonPromptRequest prepareEvaluationPrompt(
            Long userId,
            Long sessionId,
            Long questionId,
            String userAnswer,
            InterviewPrepPromptBuilder promptBuilder
    ) {
        InterviewPrepSession session = sessionRepository.findWithJobByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview prep session not found with id: " + sessionId));

        InterviewQuestion question = questionRepository.findByIdAndSessionId(questionId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview question not found with id: " + questionId));

        return promptBuilder.buildEvaluationPrompt(session, question, userAnswer);
    }

    @Transactional(readOnly = true)
    public LoadedSessionData loadSessionWithQuestions(Long userId, Long sessionId) {
        InterviewPrepSession session = sessionRepository.findWithJobByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview prep session not found with id: " + sessionId));
        List<InterviewQuestion> questions = questionRepository.findAllBySessionIdOrderByOrderIndexAscIdAsc(sessionId);
        return new LoadedSessionData(session, questions);
    }

    @Transactional
    public InterviewPrepSession saveGeneratedSession(
            Long userId,
            Long jobId,
            List<QuestionCategory> requestedCategories,
            QuestionDifficulty targetDifficulty,
            ValidatedInterviewSessionPayload validated,
            String provider,
            String modelUsed
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        List<Map<String, String>> topicMaps = new ArrayList<>();
        for (CompanyPrepTopicDto topic : validated.companyPrepTopics()) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("topic", topic.getTopic());
            entry.put("whyRelevant", topic.getWhyRelevant());
            entry.put("groundingBasis", topic.getGroundingBasis());
            topicMaps.add(entry);
        }

        List<String> categoryNames = requestedCategories.stream().map(QuestionCategory::name).toList();

        InterviewPrepSession session = sessionRepository.saveAndFlush(InterviewPrepSession.builder()
                .user(user)
                .job(job)
                .sessionTitle(validated.sessionTitle())
                .overallFeedback(validated.overallPrepAdvice())
                .targetCategories(new ArrayList<>(categoryNames))
                .targetDifficulty(targetDifficulty)
                .companyPrepTopics(topicMaps)
                .improvementRecommendations(new ArrayList<>(validated.improvementRecommendations()))
                .aiProvider(provider)
                .aiModelUsed(modelUsed)
                .build());

        List<InterviewQuestion> questions = new ArrayList<>(validated.questions().size());
        int index = 1;
        for (ValidatedQuestionItem item : validated.questions()) {
            questions.add(InterviewQuestion.builder()
                    .session(session)
                    .orderIndex(index++)
                    .category(item.category())
                    .difficulty(item.difficulty())
                    .questionText(item.questionText())
                    .idealAnswer(item.idealAnswer())
                    .focusTags(new ArrayList<>(item.focusTags()))
                    .build());
        }
        questionRepository.saveAllAndFlush(questions);
        return session;
    }

    @Transactional
    public InterviewPrepSession saveEvaluatedAnswer(
            Long userId,
            Long sessionId,
            Long questionId,
            String userAnswer,
            ValidatedAnswerEvaluationPayload validated,
            String provider,
            String modelUsed
    ) {
        InterviewPrepSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview prep session not found with id: " + sessionId));

        InterviewQuestion question = questionRepository.findByIdAndSessionId(questionId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview question not found with id: " + questionId));

        question.setUserAnswer(userAnswer.trim());
        question.setAiFeedback(validated.aiFeedback());
        question.setScore(validated.score());
        question.setStrongPoints(new ArrayList<>(validated.strongPoints()));
        question.setMissingElements(new ArrayList<>(validated.missingElements()));
        question.setAnsweredAt(Instant.now());
        question.setEvaluationProvider(provider);
        question.setEvaluationModelUsed(modelUsed);
        questionRepository.saveAndFlush(question);

        List<InterviewQuestion> allQuestions = questionRepository.findAllBySessionIdOrderByOrderIndexAscIdAsc(sessionId);
        List<BigDecimal> answeredScores = allQuestions.stream()
                .map(InterviewQuestion::getScore)
                .filter(java.util.Objects::nonNull)
                .toList();

        if (!answeredScores.isEmpty()) {
            BigDecimal sum = answeredScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal average = sum.divide(BigDecimal.valueOf(answeredScores.size()), 2, RoundingMode.HALF_UP);
            session.setScore(average);
        }
        if (validated.sessionOverallFeedback() != null && !validated.sessionOverallFeedback().isBlank()) {
            session.setOverallFeedback(validated.sessionOverallFeedback());
        }
        if (validated.improvementRecommendations() != null && !validated.improvementRecommendations().isEmpty()) {
            session.setImprovementRecommendations(new ArrayList<>(validated.improvementRecommendations()));
        }

        return sessionRepository.saveAndFlush(session);
    }
}
