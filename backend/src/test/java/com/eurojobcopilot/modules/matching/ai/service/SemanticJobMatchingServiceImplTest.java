package com.eurojobcopilot.modules.matching.ai.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobMatchBreakdownDto;
import com.eurojobcopilot.modules.matching.ai.engine.SemanticMatchPromptBuilder;
import com.eurojobcopilot.modules.matching.ai.engine.SemanticMatchValidator;
import com.eurojobcopilot.modules.matching.ai.model.RawAiSemanticMatchPayload;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.SkillEvidence;
import com.eurojobcopilot.modules.matching.ai.model.ValidatedSemanticMatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticJobMatchingServiceImplTest {

    @Mock
    private SemanticJobMatchingPersistenceHelper persistenceHelper;
    @Mock
    private SemanticMatchPromptBuilder promptBuilder;
    @Mock
    private AiGatewayService aiGatewayService;
    @Mock
    private SemanticMatchValidator semanticMatchValidator;

    @InjectMocks
    private SemanticJobMatchingServiceImpl service;

    private AuthenticatedUserPrincipal principal;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(42L)
                .uuid("user-42")
                .email("semantic@eurojob.eu")
                .firstName("Noah")
                .lastName("Vogel")
                .passwordHash("hash")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        principal = AuthenticatedUserPrincipal.from(user);
    }

    @Test
    void evaluateSemanticMatch_validatesAuthJobIdAndNonEmptyCandidateOrJobEvidence() {
        assertThatThrownBy(() -> service.evaluateSemanticMatch(null, 10L, true))
                .isInstanceOf(UnauthorizedAccessException.class);
        assertThatThrownBy(() -> service.evaluateSemanticMatch(principal, 0L, true))
                .isInstanceOf(BusinessValidationException.class);

        SemanticMatchContextSnapshot emptyCandidate = new SemanticMatchContextSnapshot(
                42L,
                10L,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                new JobEvidence("Java Dev", "Corp", "Germany", "Berlin", "Berlin", "HYBRID",
                        "FULL_TIME", "SENIOR", null, null, null, null, null, "UNCONFIRMED",
                        false, null, null, List.of("Java"), "Backend services."),
                null,
                null
        );
        when(persistenceHelper.loadEvaluationSnapshot(42L, 10L)).thenReturn(emptyCandidate);

        assertThatThrownBy(() -> service.evaluateSemanticMatch(principal, 10L, true))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("empty");
        verifyNoInteractions(aiGatewayService, promptBuilder, semanticMatchValidator);
    }

    @Test
    void evaluateSemanticMatch_returnsCachedBreakdownWhenForceRefreshFalse() {
        JobMatchBreakdownDto cached = JobMatchBreakdownDto.builder()
                .deterministicScore(new BigDecimal("82.00"))
                .aiScore(new BigDecimal("90.00"))
                .finalScore(new BigDecimal("86.00"))
                .aiRationale("Cached semantic fit.")
                .build();
        SemanticMatchContextSnapshot withCache = new SemanticMatchContextSnapshot(
                42L,
                10L,
                null,
                null,
                List.of(new SkillEvidence("Java", new BigDecimal("5"), "EXPERT", true)),
                List.of(),
                List.of(),
                null,
                new JobEvidence("Java Dev", "Corp", "Germany", "Berlin", "Berlin", "HYBRID",
                        "FULL_TIME", "SENIOR", null, null, null, null, null, "UNCONFIRMED",
                        false, null, null, List.of("Java"), "Backend services."),
                null,
                cached
        );
        when(persistenceHelper.loadEvaluationSnapshot(42L, 10L)).thenReturn(withCache);

        JobMatchBreakdownDto result = service.evaluateSemanticMatch(principal, 10L, false);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(aiGatewayService, promptBuilder, semanticMatchValidator);
    }

    @Test
    void evaluateSemanticMatch_doesNotPersistWhenValidationFailsAndPersistsWhenValid() {
        SemanticMatchContextSnapshot snapshot = new SemanticMatchContextSnapshot(
                42L,
                10L,
                null,
                null,
                List.of(new SkillEvidence("Java", new BigDecimal("5"), "EXPERT", true)),
                List.of(),
                List.of(),
                null,
                new JobEvidence("Java Dev", "Corp", "Germany", "Berlin", "Berlin", "HYBRID",
                        "FULL_TIME", "SENIOR", null, null, null, null, null, "UNCONFIRMED",
                        false, null, null, List.of("Java"), "Backend services."),
                null,
                null
        );
        AiJsonPromptRequest promptRequest = new AiJsonPromptRequest("semantic-job-matching", "sys", "usr", 0.15, null);
        RawAiSemanticMatchPayload invalidRaw = RawAiSemanticMatchPayload.builder().aiScore(new BigDecimal("140")).build();
        AiStructuredResponse<RawAiSemanticMatchPayload> badResponse =
                new AiStructuredResponse<>("GEMINI", "gemini-2.5-flash", invalidRaw, "{}", 100, 50);

        when(persistenceHelper.loadEvaluationSnapshot(42L, 10L)).thenReturn(snapshot);
        when(promptBuilder.buildPromptRequest(snapshot)).thenReturn(promptRequest);
        when(aiGatewayService.executeStructuredJsonPrompt(promptRequest, RawAiSemanticMatchPayload.class))
                .thenReturn(badResponse);
        when(semanticMatchValidator.validate(invalidRaw))
                .thenThrow(new AiResponseValidationException("aiScore out of range"));

        assertThatThrownBy(() -> service.evaluateSemanticMatch(principal, 10L, true))
                .isInstanceOf(AiResponseValidationException.class);
        verify(persistenceHelper, org.mockito.Mockito.never()).saveValidatedSemanticMatch(any(), any(), any(), any());

        RawAiSemanticMatchPayload validRaw = RawAiSemanticMatchPayload.builder()
                .aiScore(new BigDecimal("92"))
                .strongMatchingAreas(List.of("Strong JVM backend alignment"))
                .missingSkills(List.of("Kafka"))
                .experienceGaps(List.of("Limited streaming ledger exposure"))
                .potentialConcerns(List.of("Master resume lacks project metrics"))
                .improvementSuggestions(List.of("Add event-driven project notes"))
                .rationale("Strong backend fit with minor streaming gap.")
                .build();
        AiStructuredResponse<RawAiSemanticMatchPayload> goodResponse =
                new AiStructuredResponse<>("GEMINI", "gemini-2.5-flash", validRaw, "{}", 200, 120);
        ValidatedSemanticMatchResult validated = new ValidatedSemanticMatchResult(
                new BigDecimal("92.00"),
                List.of("Strong JVM backend alignment"),
                List.of("Kafka"),
                List.of("Limited streaming ledger exposure"),
                List.of("Master resume lacks project metrics"),
                List.of("Add event-driven project notes"),
                "Strong backend fit with minor streaming gap."
        );
        JobMatchBreakdownDto expected = JobMatchBreakdownDto.builder()
                .deterministicScore(new BigDecimal("88.00"))
                .aiScore(new BigDecimal("92.00"))
                .finalScore(new BigDecimal("90.00"))
                .matchPercentage(90)
                .aiStrongMatches(validated.strongMatchingAreas())
                .build();

        when(aiGatewayService.executeStructuredJsonPrompt(promptRequest, RawAiSemanticMatchPayload.class))
                .thenReturn(goodResponse);
        when(semanticMatchValidator.validate(validRaw)).thenReturn(validated);
        when(persistenceHelper.saveValidatedSemanticMatch(42L, 10L, validated, goodResponse)).thenReturn(expected);

        JobMatchBreakdownDto actual = service.evaluateSemanticMatch(principal, 10L, true);
        assertThat(actual.getFinalScore()).isEqualByComparingTo("90.00");
        assertThat(actual.getAiStrongMatches()).containsExactly("Strong JVM backend alignment");
        verify(persistenceHelper).saveValidatedSemanticMatch(42L, 10L, validated, goodResponse);
    }
}

