package com.eurojobcopilot.modules.job.intelligence.service;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.JobAiAnalysisDto;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.intelligence.analyzer.JobDescriptionAnalysisValidator;
import com.eurojobcopilot.modules.job.intelligence.analyzer.JobDescriptionPromptBuilder;
import com.eurojobcopilot.modules.job.intelligence.analyzer.RawAiJdExtractionPayload;
import com.eurojobcopilot.modules.job.intelligence.analyzer.ValidatedJobDescriptionAnalysis;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisHistoryRepository;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobAiAnalysisServiceImplTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobAiAnalysisRepository jobAiAnalysisRepository;

    @Mock
    private JobAiAnalysisHistoryRepository jobAiAnalysisHistoryRepository;

    @Mock
    private JobDescriptionPromptBuilder promptBuilder;

    @Mock
    private AiGatewayService aiGatewayService;

    @Mock
    private JobDescriptionAnalysisValidator analysisValidator;

    @Mock
    private JobAiAnalysisPersistenceHelper persistenceHelper;

    @InjectMocks
    private JobAiAnalysisServiceImpl service;

    private AuthenticatedUserPrincipal principal;
    private Job sampleJob;

    @BeforeEach
    void setUp() {
        principal = AuthenticatedUserPrincipal.from(User.builder()
                .id(77L)
                .uuid("user-uuid-77")
                .email("candidate@eurojob.eu")
                .passwordHash("hash")
                .firstName("Aarav")
                .lastName("Patel")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        sampleJob = Job.builder()
                .id(10L)
                .title("Senior Java Engineer")
                .companyName("Adyen")
                .country("Netherlands")
                .location("Amsterdam")
                .workArrangement(WorkArrangement.HYBRID)
                .description("Build distributed payment settlement services in Java 21.")
                .build();
    }

    @Test
    void analyzeJobDescription_returnsCachedResultWhenForceRefreshFalse() {
        JobAiAnalysis existing = JobAiAnalysis.builder()
                .id(5L)
                .job(sampleJob)
                .analysisVersion(2)
                .aiModelUsed("gemini-2.5-flash")
                .build();
        JobAiAnalysisDto cachedDto = JobAiAnalysisDto.builder()
                .id(5L)
                .jobId(10L)
                .analysisVersion(2)
                .summary("Cached analysis")
                .build();

        when(jobRepository.findById(10L)).thenReturn(Optional.of(sampleJob));
        when(jobAiAnalysisRepository.findByJobId(10L)).thenReturn(Optional.of(existing));
        when(persistenceHelper.toDto(existing)).thenReturn(cachedDto);

        JobAiAnalysisDto result = service.analyzeJobDescription(principal, 10L, false);

        assertThat(result.getAnalysisVersion()).isEqualTo(2);
        assertThat(result.getSummary()).isEqualTo("Cached analysis");
        verifyNoInteractions(promptBuilder, aiGatewayService, analysisValidator);
    }

    @Test
    void analyzeJobDescription_doesNotPersistWhenValidationFails() {
        AiJsonPromptRequest promptRequest = new AiJsonPromptRequest("job-description-analysis", "sys", "usr", 0.1, null);
        RawAiJdExtractionPayload invalidPayload = RawAiJdExtractionPayload.builder()
                .salaryMin(new BigDecimal("100000"))
                .salaryMax(new BigDecimal("50000"))
                .build();
        AiStructuredResponse<RawAiJdExtractionPayload> gatewayResponse = new AiStructuredResponse<>(
                "GEMINI",
                "gemini-2.5-flash",
                invalidPayload,
                "{}",
                100,
                50
        );

        when(jobRepository.findById(10L)).thenReturn(Optional.of(sampleJob));
        when(promptBuilder.buildPromptRequest(sampleJob)).thenReturn(promptRequest);
        when(aiGatewayService.executeStructuredJsonPrompt(promptRequest, RawAiJdExtractionPayload.class))
                .thenReturn(gatewayResponse);
        when(analysisValidator.validate(invalidPayload))
                .thenThrow(new AiResponseValidationException("salaryMin exceeds salaryMax"));

        assertThatThrownBy(() -> service.analyzeJobDescription(principal, 10L, true))
                .isInstanceOf(AiResponseValidationException.class);

        verify(persistenceHelper, org.mockito.Mockito.never()).saveValidatedAnalysis(any(), any(), any(), any());
    }

    @Test
    void analyzeJobDescription_rejectsBlankDescriptionAndPersistsValidOutput() {
        sampleJob.setDescription("   ");
        when(jobRepository.findById(10L)).thenReturn(Optional.of(sampleJob));

        assertThatThrownBy(() -> service.analyzeJobDescription(principal, 10L, true))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("empty");
        verifyNoInteractions(aiGatewayService);

        sampleJob.setDescription("Java Spring Boot developer role in Berlin.");
        AiJsonPromptRequest promptRequest = new AiJsonPromptRequest("job-description-analysis", "sys", "usr", 0.1, null);
        RawAiJdExtractionPayload raw = RawAiJdExtractionPayload.builder().summary("Summary").requiredSkills(List.of("Java")).build();
        AiStructuredResponse<RawAiJdExtractionPayload> gatewayResponse =
                new AiStructuredResponse<>("GEMINI", "gemini-2.5-flash", raw, "{}", 120, 80);
        ValidatedJobDescriptionAnalysis validated = new ValidatedJobDescriptionAnalysis(
                "Java Developer", "Acme", "Germany", "Berlin", null, null, null,
                List.of("Java"), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, null, null, "Summary"
        );
        JobAiAnalysisDto expectedDto = JobAiAnalysisDto.builder().jobId(10L).analysisVersion(1).summary("Summary").build();

        when(promptBuilder.buildPromptRequest(sampleJob)).thenReturn(promptRequest);
        when(aiGatewayService.executeStructuredJsonPrompt(promptRequest, RawAiJdExtractionPayload.class))
                .thenReturn(gatewayResponse);
        when(analysisValidator.validate(raw)).thenReturn(validated);
        when(persistenceHelper.saveValidatedAnalysis(eq(10L), eq(77L), eq(validated), eq(gatewayResponse)))
                .thenReturn(expectedDto);

        JobAiAnalysisDto actual = service.analyzeJobDescription(principal, 10L, true);
        assertThat(actual.getAnalysisVersion()).isEqualTo(1);
        verify(persistenceHelper).saveValidatedAnalysis(10L, 77L, validated, gatewayResponse);
    }
}

