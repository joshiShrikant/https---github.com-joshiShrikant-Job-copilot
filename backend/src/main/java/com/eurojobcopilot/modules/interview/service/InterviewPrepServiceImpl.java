package com.eurojobcopilot.modules.interview.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.analytics.service.MarketJobClassifier;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.interview.ai.InterviewPrepPersistenceHelper;
import com.eurojobcopilot.modules.interview.ai.InterviewPrepPersistenceHelper.LoadedSessionData;
import com.eurojobcopilot.modules.interview.ai.InterviewPrepPromptBuilder;
import com.eurojobcopilot.modules.interview.ai.InterviewPrepValidator;
import com.eurojobcopilot.modules.interview.ai.InterviewSessionContextSnapshot;
import com.eurojobcopilot.modules.interview.ai.RawAiAnswerEvaluationPayload;
import com.eurojobcopilot.modules.interview.ai.RawAiInterviewSessionPayload;
import com.eurojobcopilot.modules.interview.ai.ValidatedAnswerEvaluationPayload;
import com.eurojobcopilot.modules.interview.ai.ValidatedInterviewSessionPayload;
import com.eurojobcopilot.modules.interview.dto.CategoryPerformanceStatDto;
import com.eurojobcopilot.modules.interview.dto.CompanyPrepTopicDto;
import com.eurojobcopilot.modules.interview.dto.CreateInterviewPrepSessionRequest;
import com.eurojobcopilot.modules.interview.dto.DifficultyPerformanceStatDto;
import com.eurojobcopilot.modules.interview.dto.InterviewPerformanceHistoryResponse;
import com.eurojobcopilot.modules.interview.dto.InterviewPrepSessionDetailResponse;
import com.eurojobcopilot.modules.interview.dto.InterviewPrepSessionSummaryDto;
import com.eurojobcopilot.modules.interview.dto.InterviewQuestionDto;
import com.eurojobcopilot.modules.interview.dto.InterviewSessionScorePointDto;
import com.eurojobcopilot.modules.interview.dto.SubmitInterviewAnswerRequest;
import com.eurojobcopilot.modules.interview.entity.InterviewPrepSession;
import com.eurojobcopilot.modules.interview.entity.InterviewQuestion;
import com.eurojobcopilot.modules.interview.entity.QuestionCategory;
import com.eurojobcopilot.modules.interview.entity.QuestionDifficulty;
import com.eurojobcopilot.modules.interview.repository.InterviewPrepSessionRepository;
import com.eurojobcopilot.modules.interview.repository.InterviewQuestionRepository;
import com.eurojobcopilot.modules.job.dto.CompanySponsorshipSummaryDto;
import com.eurojobcopilot.modules.job.dto.JobAiAnalysisDto;
import com.eurojobcopilot.modules.job.dto.JobDetailDto;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.service.JobQueryService;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPrepServiceImpl implements InterviewPrepService {

    private static final int DEFAULT_QUESTION_COUNT = 6;

    private final InterviewPrepSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewPrepPersistenceHelper persistenceHelper;
    private final InterviewPrepPromptBuilder promptBuilder;
    private final InterviewPrepValidator validator;
    private final JobQueryService jobQueryService;
    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserWorkExperienceRepository workExperienceRepository;
    private final ResumeRepository resumeRepository;
    private final MarketJobClassifier marketJobClassifier;
    private final AiGatewayService aiGatewayService;

    @Override
    public InterviewPrepSessionDetailResponse createSession(
            AuthenticatedUserPrincipal currentUser,
            CreateInterviewPrepSessionRequest request
    ) {
        Long userId = requireUserId(currentUser);
        if (request == null || request.getJobId() == null || request.getJobId() <= 0) {
            throw new BusinessValidationException("jobId must be a positive integer");
        }

        JobDetailDto jobDetail = jobQueryService.getJobDetail(currentUser, request.getJobId());
        List<QuestionCategory> resolvedCategories = resolveCategories(request.getCategories(), jobDetail);
        QuestionDifficulty resolvedDifficulty = resolveDifficulty(request.getDifficulty(), jobDetail.getExperienceLevel());
        int count = request.getQuestionCount() != null
                ? Math.max(3, Math.min(12, request.getQuestionCount()))
                : DEFAULT_QUESTION_COUNT;

        InterviewSessionContextSnapshot snapshot = buildSessionContext(
                userId,
                jobDetail,
                request.getSessionTitle(),
                resolvedCategories,
                resolvedDifficulty,
                count,
                request.getFocusNotes()
        );

        AiJsonPromptRequest promptRequest = promptBuilder.buildGenerationPrompt(snapshot);
        AiStructuredResponse<RawAiInterviewSessionPayload> aiResponse =
                aiGatewayService.executeStructuredJsonPrompt(promptRequest, RawAiInterviewSessionPayload.class);

        ValidatedInterviewSessionPayload validated = validator.validateSessionPayload(aiResponse.payload(), snapshot);
        InterviewPrepSession saved = persistenceHelper.saveGeneratedSession(
                userId,
                jobDetail.getId(),
                resolvedCategories,
                resolvedDifficulty,
                validated,
                aiResponse.provider(),
                aiResponse.model()
        );

        log.info("Created interview prep session id={} userId={} jobId={} questions={}",
                saved.getId(), userId, jobDetail.getId(), validated.questions().size());
        LoadedSessionData loaded = persistenceHelper.loadSessionWithQuestions(userId, saved.getId());
        return toDetailResponse(loaded.session(), loaded.questions());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewPrepSessionSummaryDto> listSessions(AuthenticatedUserPrincipal currentUser, Long jobId) {
        Long userId = requireUserId(currentUser);
        if (jobId != null && jobId <= 0) {
            throw new BusinessValidationException("jobId must be a positive integer");
        }

        List<InterviewPrepSession> sessions = jobId != null
                ? sessionRepository.findAllWithJobByUserIdAndJobIdOrderByCreatedAtDescIdDesc(userId, jobId)
                : sessionRepository.findAllWithJobByUserIdOrderByCreatedAtDescIdDesc(userId);

        if (sessions.isEmpty()) {
            return List.of();
        }

        List<Long> sessionIds = sessions.stream().map(InterviewPrepSession::getId).toList();
        Map<Long, List<InterviewQuestion>> questionsBySession = questionRepository
                .findAllBySessionIdInOrderByOrderIndexAscIdAsc(sessionIds)
                .stream()
                .collect(Collectors.groupingBy(q -> q.getSession().getId()));

        return sessions.stream()
                .map(s -> toSummaryDto(s, questionsBySession.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewPrepSessionDetailResponse getSession(AuthenticatedUserPrincipal currentUser, Long sessionId) {
        Long userId = requireUserId(currentUser);
        if (sessionId == null || sessionId <= 0) {
            throw new BusinessValidationException("Session ID must be a positive integer");
        }

        InterviewPrepSession session = sessionRepository.findWithJobByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview prep session not found with id: " + sessionId));

        List<InterviewQuestion> questions = questionRepository.findAllBySessionIdOrderByOrderIndexAscIdAsc(sessionId);
        return toDetailResponse(session, questions);
    }

    @Override
    public InterviewPrepSessionDetailResponse submitAnswer(
            AuthenticatedUserPrincipal currentUser,
            Long sessionId,
            SubmitInterviewAnswerRequest request
    ) {
        Long userId = requireUserId(currentUser);
        if (sessionId == null || sessionId <= 0) {
            throw new BusinessValidationException("Session ID must be a positive integer");
        }
        if (request == null || request.getQuestionId() == null || request.getQuestionId() <= 0) {
            throw new BusinessValidationException("questionId must be a positive integer");
        }
        if (request.getUserAnswer() == null || request.getUserAnswer().isBlank()) {
            throw new BusinessValidationException("userAnswer must not be blank");
        }

        AiJsonPromptRequest evalPrompt = persistenceHelper.prepareEvaluationPrompt(
                userId,
                sessionId,
                request.getQuestionId(),
                request.getUserAnswer(),
                promptBuilder
        );
        AiStructuredResponse<RawAiAnswerEvaluationPayload> aiResponse =
                aiGatewayService.executeStructuredJsonPrompt(evalPrompt, RawAiAnswerEvaluationPayload.class);

        ValidatedAnswerEvaluationPayload validated = validator.validateEvaluationPayload(aiResponse.payload());
        persistenceHelper.saveEvaluatedAnswer(
                userId,
                sessionId,
                request.getQuestionId(),
                request.getUserAnswer(),
                validated,
                aiResponse.provider(),
                aiResponse.model()
        );

        log.info("Evaluated interview answer sessionId={} questionId={} score={}",
                sessionId, request.getQuestionId(), validated.score());
        LoadedSessionData loaded = persistenceHelper.loadSessionWithQuestions(userId, sessionId);
        return toDetailResponse(loaded.session(), loaded.questions());
    }

    @Override
    @Transactional
    public void deleteSession(AuthenticatedUserPrincipal currentUser, Long sessionId) {
        Long userId = requireUserId(currentUser);
        if (sessionId == null || sessionId <= 0) {
            throw new BusinessValidationException("Session ID must be a positive integer");
        }
        InterviewPrepSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview prep session not found with id: " + sessionId));
        questionRepository.deleteAllBySessionId(sessionId);
        questionRepository.flush();
        sessionRepository.delete(session);
        sessionRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewPerformanceHistoryResponse getPerformanceHistory(AuthenticatedUserPrincipal currentUser) {
        Long userId = requireUserId(currentUser);
        List<InterviewPrepSession> sessions = sessionRepository.findAllWithJobByUserIdOrderByCreatedAtDescIdDesc(userId);
        if (sessions.isEmpty()) {
            return InterviewPerformanceHistoryResponse.builder()
                    .totalSessions(0)
                    .totalQuestions(0)
                    .totalAnsweredQuestions(0)
                    .averageScore(null)
                    .categoryStats(List.of())
                    .difficultyStats(List.of())
                    .recentScoreTrend(List.of())
                    .weakestCategories(List.of())
                    .improvementRecommendations(List.of("Generate your first role-specific interview prep session and submit answers to start tracking score trends."))
                    .sessions(List.of())
                    .build();
        }

        List<Long> sessionIds = sessions.stream().map(InterviewPrepSession::getId).toList();
        List<InterviewQuestion> allQuestions = questionRepository.findAllBySessionIdInOrderByOrderIndexAscIdAsc(sessionIds);
        Map<Long, List<InterviewQuestion>> bySession = allQuestions.stream()
                .collect(Collectors.groupingBy(q -> q.getSession().getId()));

        int totalQuestions = allQuestions.size();
        List<BigDecimal> allScores = allQuestions.stream()
                .map(InterviewQuestion::getScore)
                .filter(java.util.Objects::nonNull)
                .toList();
        BigDecimal overallAvg = average(allScores);

        Map<QuestionCategory, List<InterviewQuestion>> byCategory = new EnumMap<>(QuestionCategory.class);
        Map<QuestionDifficulty, List<InterviewQuestion>> byDifficulty = new EnumMap<>(QuestionDifficulty.class);
        for (InterviewQuestion q : allQuestions) {
            byCategory.computeIfAbsent(q.getCategory(), k -> new ArrayList<>()).add(q);
            byDifficulty.computeIfAbsent(q.getDifficulty(), k -> new ArrayList<>()).add(q);
        }

        List<CategoryPerformanceStatDto> categoryStats = byCategory.entrySet().stream()
                .map(e -> {
                    List<BigDecimal> scores = e.getValue().stream().map(InterviewQuestion::getScore)
                            .filter(java.util.Objects::nonNull).toList();
                    return CategoryPerformanceStatDto.builder()
                            .category(e.getKey())
                            .totalQuestions(e.getValue().size())
                            .answeredQuestions(scores.size())
                            .averageScore(average(scores))
                            .build();
                })
                .sorted(Comparator.comparingInt(CategoryPerformanceStatDto::getTotalQuestions).reversed())
                .toList();

        List<DifficultyPerformanceStatDto> difficultyStats = byDifficulty.entrySet().stream()
                .map(e -> {
                    List<BigDecimal> scores = e.getValue().stream().map(InterviewQuestion::getScore)
                            .filter(java.util.Objects::nonNull).toList();
                    return DifficultyPerformanceStatDto.builder()
                            .difficulty(e.getKey())
                            .totalQuestions(e.getValue().size())
                            .answeredQuestions(scores.size())
                            .averageScore(average(scores))
                            .build();
                })
                .toList();

        List<InterviewPrepSessionSummaryDto> summaries = sessions.stream()
                .map(s -> toSummaryDto(s, bySession.getOrDefault(s.getId(), List.of())))
                .toList();

        List<InterviewPrepSessionSummaryDto> chronological = new ArrayList<>(summaries);
        Collections.reverse(chronological);
        List<InterviewSessionScorePointDto> trend = chronological.stream()
                .map(s -> InterviewSessionScorePointDto.builder()
                        .sessionId(s.getId())
                        .sessionTitle(s.getSessionTitle())
                        .companyName(s.getCompanyName())
                        .score(s.getScore())
                        .answeredQuestions(s.getAnsweredQuestions())
                        .totalQuestions(s.getTotalQuestions())
                        .createdAt(s.getCreatedAt())
                        .build())
                .toList();

        List<String> weakestCategories = categoryStats.stream()
                .filter(c -> c.getAverageScore() != null)
                .sorted(Comparator.comparing(CategoryPerformanceStatDto::getAverageScore))
                .limit(3)
                .map(c -> c.getCategory().name() + " (" + c.getAverageScore() + "%)")
                .toList();

        LinkedHashSet<String> recommendations = new LinkedHashSet<>();
        for (InterviewPrepSession s : sessions) {
            if (s.getImprovementRecommendations() != null) {
                for (String rec : s.getImprovementRecommendations()) {
                    if (rec != null && !rec.isBlank() && recommendations.size() < 6) {
                        recommendations.add(rec.trim());
                    }
                }
            }
        }
        if (recommendations.isEmpty() && !weakestCategories.isEmpty()) {
            recommendations.add("Prioritize additional timed drills in: " + String.join(", ", weakestCategories));
        }

        return InterviewPerformanceHistoryResponse.builder()
                .totalSessions(sessions.size())
                .totalQuestions(totalQuestions)
                .totalAnsweredQuestions(allScores.size())
                .averageScore(overallAvg)
                .categoryStats(categoryStats)
                .difficultyStats(difficultyStats)
                .recentScoreTrend(trend)
                .weakestCategories(weakestCategories)
                .improvementRecommendations(new ArrayList<>(recommendations))
                .sessions(summaries)
                .build();
    }

    private InterviewSessionContextSnapshot buildSessionContext(
            Long userId,
            JobDetailDto jobDetail,
            String customSessionTitle,
            List<QuestionCategory> resolvedCategories,
            QuestionDifficulty targetDifficulty,
            int count,
            String focusNotes
    ) {
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        List<UserSkill> skills = userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(userId);
        List<UserWorkExperience> experiences = workExperienceRepository.findAllByUserIdOrderByStartDateDesc(userId);
        Resume masterResume = resumeRepository.findFirstByUserIdAndMasterApprovedTrueOrderByVersionNumberDesc(userId)
                .orElse(null);

        CompanySponsorshipSummaryDto comp = jobDetail.getCompany();
        JobAiAnalysisDto ai = jobDetail.getAiAnalysis();

        List<String> topSkills = skills.stream().limit(15).map(UserSkill::getSkillName).toList();
        List<String> expHighlights = experiences.stream().limit(4)
                .map(e -> e.getDesignation() + " @ " + e.getCompanyName())
                .toList();

        return new InterviewSessionContextSnapshot(
                userId,
                jobDetail.getId(),
                customSessionTitle,
                resolvedCategories,
                targetDifficulty,
                count,
                focusNotes,
                jobDetail.getTitle(),
                jobDetail.getCompanyName(),
                comp != null ? comp.getIndustry() : null,
                comp != null ? comp.getWebsiteUrl() : null,
                jobDetail.getSponsorshipBreakdown() != null && jobDetail.getSponsorshipBreakdown().isVerifiedSponsor(),
                jobDetail.getCountry(),
                jobDetail.getCity(),
                jobDetail.getExperienceLevel() != null ? jobDetail.getExperienceLevel().name() : null,
                jobDetail.getSkills(),
                jobDetail.getDescription(),
                ai != null ? ai.getSummary() : null,
                ai != null ? ai.getRequiredSkills() : List.of(),
                ai != null ? ai.getResponsibilities() : List.of(),
                profile != null ? profile.getCurrentDesignation() : null,
                profile != null ? profile.getYearsOfExperience() : null,
                topSkills,
                expHighlights,
                extractMasterResumeSummary(masterResume)
        );
    }

    private List<QuestionCategory> resolveCategories(List<QuestionCategory> requested, JobDetailDto job) {
        if (requested != null && !requested.isEmpty()) {
            return List.copyOf(new LinkedHashSet<>(requested));
        }
        LinkedHashSet<QuestionCategory> defaults = new LinkedHashSet<>();
        String combined = ((job.getTitle() != null ? job.getTitle() : "") + " "
                + (job.getSkills() != null ? String.join(" ", job.getSkills()) : "") + " "
                + (job.getDescription() != null ? job.getDescription() : "")).toLowerCase();

        if (combined.contains("angular")) {
            defaults.add(QuestionCategory.ANGULAR);
            defaults.add(QuestionCategory.TYPESCRIPT);
            defaults.add(QuestionCategory.RXJS_NGRX);
        }
        if (combined.contains("java")) {
            defaults.add(QuestionCategory.JAVA);
        }
        if (combined.contains("spring")) {
            defaults.add(QuestionCategory.SPRING_BOOT);
        }
        if (combined.contains("javascript")) {
            defaults.add(QuestionCategory.JAVASCRIPT);
        }
        if (combined.contains("typescript") && !defaults.contains(QuestionCategory.TYPESCRIPT)) {
            defaults.add(QuestionCategory.TYPESCRIPT);
        }
        if (combined.contains("sql") || combined.contains("postgres") || combined.contains("mysql") || combined.contains("database")) {
            defaults.add(QuestionCategory.DATABASE);
        }
        if (job.getExperienceLevel() == ExperienceLevel.LEAD || job.getExperienceLevel() == ExperienceLevel.PRINCIPAL) {
            defaults.add(QuestionCategory.TECHNICAL_LEAD);
        }
        defaults.add(QuestionCategory.SYSTEM_DESIGN);
        defaults.add(QuestionCategory.CODING);
        defaults.add(QuestionCategory.BEHAVIORAL);
        if (defaults.size() < 3) {
            defaults.add(QuestionCategory.TECHNICAL);
        }
        return List.copyOf(defaults);
    }

    private QuestionDifficulty resolveDifficulty(QuestionDifficulty requested, ExperienceLevel jobLevel) {
        if (requested != null) {
            return requested;
        }
        if (jobLevel == null) {
            return QuestionDifficulty.MID;
        }
        return switch (jobLevel) {
            case ENTRY, JUNIOR -> QuestionDifficulty.ENTRY;
            case MID -> QuestionDifficulty.MID;
            case SENIOR -> QuestionDifficulty.SENIOR;
            case LEAD, PRINCIPAL -> QuestionDifficulty.STAFF;
        };
    }

    private InterviewPrepSessionSummaryDto toSummaryDto(InterviewPrepSession s, List<InterviewQuestion> questions) {
        int answered = (int) questions.stream().filter(q -> q.getUserAnswer() != null && !q.getUserAnswer().isBlank()).count();
        return InterviewPrepSessionSummaryDto.builder()
                .id(s.getId())
                .jobId(s.getJob().getId())
                .jobTitle(s.getJob().getTitle())
                .companyName(s.getJob().getCompanyName())
                .country(s.getJob().getCountry())
                .sessionTitle(s.getSessionTitle())
                .targetCategories(s.getTargetCategories() != null ? List.copyOf(s.getTargetCategories()) : List.of())
                .targetDifficulty(s.getTargetDifficulty())
                .totalQuestions(questions.size())
                .answeredQuestions(answered)
                .score(s.getScore())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private InterviewPrepSessionDetailResponse toDetailResponse(
            InterviewPrepSession s,
            List<InterviewQuestion> questions
    ) {
        int answered = (int) questions.stream().filter(q -> q.getUserAnswer() != null && !q.getUserAnswer().isBlank()).count();
        List<CompanyPrepTopicDto> topics = new ArrayList<>();
        if (s.getCompanyPrepTopics() != null) {
            for (Map<String, String> map : s.getCompanyPrepTopics()) {
                if (map == null) continue;
                topics.add(CompanyPrepTopicDto.builder()
                        .topic(map.getOrDefault("topic", "Role Architecture & Delivery"))
                        .whyRelevant(map.getOrDefault("whyRelevant", ""))
                        .groundingBasis(map.getOrDefault("groundingBasis", "Verified job posting context"))
                        .build());
            }
        }

        List<InterviewQuestionDto> questionDtos = questions.stream()
                .map(q -> InterviewQuestionDto.builder()
                        .id(q.getId())
                        .orderIndex(q.getOrderIndex())
                        .category(q.getCategory())
                        .difficulty(q.getDifficulty())
                        .questionText(q.getQuestionText())
                        .idealAnswer(q.getIdealAnswer())
                        .focusTags(q.getFocusTags() != null ? List.copyOf(q.getFocusTags()) : List.of())
                        .userAnswer(q.getUserAnswer())
                        .aiFeedback(q.getAiFeedback())
                        .score(q.getScore())
                        .strongPoints(q.getStrongPoints() != null ? List.copyOf(q.getStrongPoints()) : List.of())
                        .missingElements(q.getMissingElements() != null ? List.copyOf(q.getMissingElements()) : List.of())
                        .answeredAt(q.getAnsweredAt())
                        .evaluationProvider(q.getEvaluationProvider())
                        .evaluationModelUsed(q.getEvaluationModelUsed())
                        .build())
                .toList();

        return InterviewPrepSessionDetailResponse.builder()
                .id(s.getId())
                .jobId(s.getJob().getId())
                .jobTitle(s.getJob().getTitle())
                .companyName(s.getJob().getCompanyName())
                .country(s.getJob().getCountry())
                .sessionTitle(s.getSessionTitle())
                .targetCategories(s.getTargetCategories() != null ? List.copyOf(s.getTargetCategories()) : List.of())
                .targetDifficulty(s.getTargetDifficulty())
                .totalQuestions(questions.size())
                .answeredQuestions(answered)
                .score(s.getScore())
                .overallFeedback(s.getOverallFeedback())
                .companyPrepTopics(topics)
                .improvementRecommendations(s.getImprovementRecommendations() != null
                        ? List.copyOf(s.getImprovementRecommendations())
                        : List.of())
                .aiProvider(s.getAiProvider())
                .aiModelUsed(s.getAiModelUsed())
                .questions(questionDtos)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private String extractMasterResumeSummary(Resume masterResume) {
        if (masterResume == null || masterResume.getParsedContentJson() == null) {
            return null;
        }
        Map<String, Object> parsed = masterResume.getParsedContentJson();
        Object profileObj = parsed.get("profile");
        if (profileObj instanceof Map<?, ?> profileMap) {
            Object summaryObj = profileMap.get("summary");
            if (summaryObj instanceof String summaryText && !summaryText.isBlank()) {
                return summaryText.trim();
            }
        }
        return null;
    }

    private Long requireUserId(AuthenticatedUserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return currentUser.getId();
    }
}
