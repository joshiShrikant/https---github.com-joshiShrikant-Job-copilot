package com.eurojobcopilot.modules.matching.ai.engine;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.config.AiProperties;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.EducationEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.JobAiAnalysisEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.PreferenceEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.ProfileEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.ResumeEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.SkillEvidence;
import com.eurojobcopilot.modules.matching.ai.model.SemanticMatchContextSnapshot.WorkExperienceEvidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticMatchPromptBuilderTest {

    @Test
    void buildPromptRequest_includesProfileMasterResumeProjectsResponsibilitiesAndAntiHallucinationGuardrails() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.setMaxInputChars(12000);
        SemanticMatchPromptBuilder builder = new SemanticMatchPromptBuilder(aiProperties, new ObjectMapper());

        SemanticMatchContextSnapshot snapshot = new SemanticMatchContextSnapshot(
                1L,
                2L,
                new ProfileEvidence(
                        "Lukas Weber",
                        "Senior Java & Angular Engineer",
                        new BigDecimal("6.5"),
                        "India",
                        "Bengaluru",
                        new BigDecimal("85000.00"),
                        "EUR",
                        30,
                        "NEEDS_SPONSORSHIP",
                        "WILLING_TO_RELOCATE",
                        "https://linkedin.com/in/lukas",
                        "https://github.com/lukas",
                        null,
                        "Backend specialist in FinTech clearing systems."
                ),
                new PreferenceEvidence(
                        List.of("Senior Java Engineer"),
                        List.of("Netherlands", "Germany"),
                        List.of("Amsterdam"),
                        new BigDecimal("90000"),
                        "EUR",
                        true,
                        true,
                        "HYBRID",
                        true
                ),
                List.of(new SkillEvidence("Java 21", new BigDecimal("6.0"), "EXPERT", true)),
                List.of(new WorkExperienceEvidence(
                        "FinPay Corp",
                        "Senior Backend Engineer",
                        "Remote",
                        LocalDate.of(2022, 1, 1),
                        null,
                        true,
                        "Led SEPA Instant settlement microservice migration.",
                        List.of("Java 21", "Spring Boot", "PostgreSQL")
                )),
                List.of(new EducationEvidence(
                        "TU Munich",
                        "M.Sc.",
                        "Computer Science",
                        LocalDate.of(2017, 10, 1),
                        LocalDate.of(2019, 9, 30),
                        "1.4",
                        "Germany"
                )),
                new ResumeEvidence(
                        "res-uuid-1",
                        "Master Java CV 2026",
                        "master-java-cv.pdf",
                        3,
                        true,
                        Map.of(
                                "summary", "7 years enterprise Java systems",
                                "projects", List.of("SEPA Instant Payment Gateway handling 4k TPS")
                        )
                ),
                new JobEvidence(
                        "Staff Java Engineer",
                        "Adyen N.V.",
                        "Netherlands",
                        "Amsterdam",
                        "Amsterdam, Netherlands",
                        "HYBRID",
                        "FULL_TIME",
                        "SENIOR",
                        new BigDecimal("5.0"),
                        new BigDecimal("95000"),
                        new BigDecimal("120000"),
                        "EUR",
                        "YEARLY",
                        "VERIFIED_SPONSOR",
                        true,
                        "VERIFIED",
                        "FinTech",
                        List.of("Java 21", "Spring Boot", "Kafka"),
                        "Own multi-region payment orchestration APIs in Amsterdam."
                ),
                new JobAiAnalysisEvidence(
                        "5+ years high-scale JVM engineering",
                        List.of("Java 21", "Spring Boot"),
                        List.of("Kafka"),
                        List.of("Architect resilient clearing pipelines", "Mentor platform engineers"),
                        List.of("English (C1)"),
                        "IND Recognized Sponsor.",
                        "Full relocation offered.",
                        "Core payments engineering role."
                ),
                null
        );

        AiJsonPromptRequest request = builder.buildPromptRequest(snapshot);

        assertThat(request.taskName()).isEqualTo(SemanticMatchPromptBuilder.TASK_NAME);
        assertThat(request.systemPrompt()).contains("NEVER invent, fabricate, infer, or assume unstated skills");
        assertThat(request.systemPrompt()).contains("\"strongMatchingAreas\"");
        assertThat(request.systemPrompt()).contains("\"improvementSuggestions\"");

        assertThat(request.userPrompt()).contains("Senior Java & Angular Engineer");
        assertThat(request.userPrompt()).contains("FinPay Corp");
        assertThat(request.userPrompt()).contains("SEPA Instant settlement microservice migration");
        assertThat(request.userPrompt()).contains("Master Java CV 2026");
        assertThat(request.userPrompt()).contains("SEPA Instant Payment Gateway handling 4k TPS");
        assertThat(request.userPrompt()).contains("TU Munich");
        assertThat(request.userPrompt()).contains("Architect resilient clearing pipelines");
        assertThat(request.userPrompt()).contains("Own multi-region payment orchestration APIs");
        assertThat(request.userPrompt()).doesNotContain("password");
    }

    @Test
    void buildPromptRequest_handlesMissingResumeAndTruncatesLongInputsGracefully() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.setMaxInputChars(2600);
        SemanticMatchPromptBuilder builder = new SemanticMatchPromptBuilder(aiProperties, new ObjectMapper());

        String hugeDescription = "Distribute resilient payments infrastructure across EU banking rails. ".repeat(120);
        SemanticMatchContextSnapshot snapshot = new SemanticMatchContextSnapshot(
                1L,
                2L,
                new ProfileEvidence(
                        "Elena Rossi", "Java Engineer", new BigDecimal("4.0"), "Italy", "Milan",
                        null, "EUR", 30, "EU_CITIZEN", "FLEXIBLE", null, null, null, "Summary"
                ),
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                new JobEvidence(
                        "Backend Engineer", "Stripe", "Ireland", "Dublin", "Dublin, Ireland",
                        "HYBRID", "FULL_TIME", "MID", new BigDecimal("3.0"), null, null, null, null,
                        "UNCONFIRMED", false, null, null, List.of("Java"), hugeDescription
                ),
                null,
                null
        );

        AiJsonPromptRequest request = builder.buildPromptRequest(snapshot);
        assertThat(request.userPrompt()).contains("No master resume uploaded.");
        assertThat(request.userPrompt()).contains("[TRUNCATED]");
    }
}

