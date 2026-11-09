package com.eurojobcopilot.modules.resume.tailored;

import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.matching.config.MatchingProperties;
import com.eurojobcopilot.modules.matching.engine.DeterministicJobMatchingEngine;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeEducationDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProjectDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.UpdateTailoredResumeRequest;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import com.eurojobcopilot.modules.resume.tailored.RawAiTailoredResumePayload.RawOptimizedExperience;
import com.eurojobcopilot.modules.resume.tailored.RawAiTailoredResumePayload.RawTailoredProject;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.EducationEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.HeaderEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.JobAiAnalysisEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.MasterResumeEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.SkillEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.WorkExperienceEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TailoredResumeValidatorTest {

    private TailoredResumeValidator validator;

    @BeforeEach
    void setUp() {
        CanonicalHashGenerator hashGenerator = new CanonicalHashGenerator();
        JobNormalizer jobNormalizer = new JobNormalizer(hashGenerator);
        DeterministicJobMatchingEngine matchingEngine =
                new DeterministicJobMatchingEngine(hashGenerator, jobNormalizer, new MatchingProperties());
        validator = new TailoredResumeValidator(matchingEngine);
    }

    @Test
    void validateAiDraft_stripsFabricatedEmployersAndUnverifiedSkillsAndComputesExactAtsCoverage() {
        TailoredResumeContextSnapshot snapshot = buildRichSnapshot();

        RawOptimizedExperience verifiedRole = new RawOptimizedExperience();
        verifiedRole.setCompanyName("ClearPay Systems");
        verifiedRole.setDesignation("VP of Architecture (AI Promotion Attempt)");
        verifiedRole.setTailoredBullets(List.of(
                "Architected SEPA Instant payment clearing APIs in Java 21 and Spring Boot handling 5,000 TPS",
                "Scaled PostgreSQL ledger clusters with zero downtime"
        ));
        verifiedRole.setMatchedKeywords(List.of("Java 21", "Spring Boot", "Rust"));
        verifiedRole.setRelevanceScore(94);

        RawOptimizedExperience fakeRole = new RawOptimizedExperience();
        fakeRole.setCompanyName("Invented MegaCorp AG");
        fakeRole.setDesignation("Principal Rust Architect");
        fakeRole.setTailoredBullets(List.of("Built Rust kernel engines"));
        fakeRole.setRelevanceScore(99);

        RawTailoredProject verifiedProj = new RawTailoredProject();
        verifiedProj.setName("SEPA Clearing Sandbox");
        verifiedProj.setDescription("High-throughput ISO 20022 simulator");
        verifiedProj.setTechnologies(List.of("Java 21", "PostgreSQL", "Rust"));

        RawTailoredProject fakeProj = new RawTailoredProject();
        fakeProj.setName("Hallucinated Quantum Compiler");
        fakeProj.setDescription("Fake project");

        RawAiTailoredResumePayload raw = new RawAiTailoredResumePayload();
        raw.setHeadline("Senior Java Backend Architect");
        raw.setTailoredSummary("Backend specialist in Java 21, Spring Boot, PostgreSQL, and Rust distributed settlement systems.");
        raw.setHighlightedSkills(List.of("Java 21", "Spring Boot", "Rust"));
        raw.setAdditionalSkills(List.of("Docker", "Terraform"));
        raw.setMissingKeywords(List.of("Kafka", "Rust"));
        raw.setTailoringRationale("Prioritized payment throughput and JVM reliability.");
        raw.setOptimizedExperiences(List.of(verifiedRole, fakeRole));
        raw.setProjects(List.of(verifiedProj, fakeProj));

        ValidatedTailoredResume result = validator.validateAiDraft(raw, snapshot);
        var data = result.resumeData();

        // Locked employer, designation, and dates come from verified profile history
        assertThat(data.getOptimizedExperiences()).hasSize(1);
        TailoredResumeExperienceDto keptExp = data.getOptimizedExperiences().get(0);
        assertThat(keptExp.getCompanyName()).isEqualTo("ClearPay Systems");
        assertThat(keptExp.getDesignation()).isEqualTo("Senior Java Engineer");
        assertThat(keptExp.getStartDate()).isEqualTo(LocalDate.of(2021, 3, 1));
        assertThat(keptExp.isCurrent()).isTrue();
        assertThat(keptExp.getOriginalBullets()).hasSize(2);
        assertThat(keptExp.getMatchedKeywords()).containsExactly("Java 21", "Spring Boot");

        // Unpossessed Rust/Terraform stripped; Docker verified; Rust denied ATS credit even though mentioned in summary
        assertThat(data.getHighlightedSkills()).containsExactly("Java 21", "Spring Boot");
        assertThat(data.getAdditionalSkills()).contains("Docker");
        assertThat(data.getAdditionalSkills()).doesNotContain("Terraform", "Rust");

        // Fake project stripped, real project URL preserved and technologies filtered
        assertThat(data.getProjects()).hasSize(1);
        assertThat(data.getProjects().get(0).getName()).isEqualTo("SEPA Clearing Sandbox");
        assertThat(data.getProjects().get(0).getUrl()).isEqualTo("https://github.com/elena/sepa-sandbox");
        assertThat(data.getProjects().get(0).getTechnologies()).containsExactly("Java 21", "PostgreSQL");

        // JD keywords: Java 21, Spring Boot, PostgreSQL, Kafka, Rust -> candidate has Java, Spring Boot, PostgreSQL (3 / 5 = 60.00%)
        assertThat(data.getMatchedKeywords()).containsExactlyInAnyOrder("Java 21", "Spring Boot", "PostgreSQL");
        assertThat(data.getMissingKeywords()).containsExactlyInAnyOrder("Kafka", "Rust");
        assertThat(result.atsKeywordCoverage()).isEqualByComparingTo("60.00");

        assertThat(data.getEducations()).hasSize(1);
        assertThat(data.getEducations().get(0).getInstitution()).isEqualTo("Technical University of Munich");
        assertThat(data.getLanguages()).containsExactly("English (C2)", "German (B1)");
        assertThat(data.getGroundingWarnings())
                .anyMatch(w -> w.contains("Invented MegaCorp AG"))
                .anyMatch(w -> w.contains("Rust"))
                .anyMatch(w -> w.contains("Hallucinated Quantum Compiler"));
    }

    @Test
    void validateAiDraft_throws502WhenAllReturnedExperiencesAreFabricated() {
        TailoredResumeContextSnapshot snapshot = buildRichSnapshot();

        RawOptimizedExperience onlyFake = new RawOptimizedExperience();
        onlyFake.setCompanyName("NonExistent Unicorn B.V.");
        onlyFake.setDesignation("CTO");
        onlyFake.setTailoredBullets(List.of("Everything fabricated"));

        RawAiTailoredResumePayload raw = new RawAiTailoredResumePayload();
        raw.setHeadline("CTO");
        raw.setTailoredSummary("Fabricated career summary.");
        raw.setOptimizedExperiences(List.of(onlyFake));

        assertThatThrownBy(() -> validator.validateAiDraft(raw, snapshot))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("none of the returned work experiences matched verified candidate employers");
    }

    @Test
    void revalidateEditedDraft_recalculatesAtsCoverageLiveFromHumanEdits() {
        TailoredResumeContextSnapshot snapshot = buildRichSnapshot();

        RawOptimizedExperience verifiedRole = new RawOptimizedExperience();
        verifiedRole.setCompanyName("ClearPay Systems");
        verifiedRole.setDesignation("Senior Java Engineer");
        verifiedRole.setTailoredBullets(List.of("Built SEPA Instant ledger on Java 21 and Spring Boot"));

        RawAiTailoredResumePayload initialRaw = new RawAiTailoredResumePayload();
        initialRaw.setHeadline("Senior Java Engineer");
        initialRaw.setTailoredSummary("Java 21 and Spring Boot backend specialist.");
        initialRaw.setHighlightedSkills(List.of("Java 21", "Spring Boot"));
        initialRaw.setOptimizedExperiences(List.of(verifiedRole));

        ValidatedTailoredResume initial = validator.validateAiDraft(initialRaw, snapshot);
        // Candidate has Java, Spring Boot, PostgreSQL; draft covered 3/5 (PostgreSQL is in project/skills) -> 60%
        assertThat(initial.atsKeywordCoverage()).isEqualByComparingTo("60.00");

        UpdateTailoredResumeRequest editRequest = UpdateTailoredResumeRequest.builder()
                .title("Edited Custom Draft")
                .templateName(ResumeTemplate.MINIMAL_ATS)
                .tailoredSummary("Distributed Java 21, Spring Boot, PostgreSQL, and Apache Kafka settlement architect.")
                .highlightedSkills(List.of("Java 21", "Spring Boot", "PostgreSQL", "Kafka", "Rust"))
                .build();

        ValidatedTailoredResume edited = validator.revalidateEditedDraft(initial.resumeData(), editRequest, snapshot);
        assertThat(edited.atsKeywordCoverage()).isEqualByComparingTo("100.00");
        assertThat(edited.resumeData().getMissingKeywords()).isEmpty();
        assertThat(edited.resumeData().getMatchedKeywords())
                .containsExactlyInAnyOrder("Java 21", "Spring Boot", "PostgreSQL", "Kafka", "Rust");
        assertThat(edited.resumeData().getGroundingWarnings())
                .anyMatch(w -> w.contains("Kafka"))
                .anyMatch(w -> w.contains("Rust"));
    }

    private TailoredResumeContextSnapshot buildRichSnapshot() {
        HeaderEvidence header = new HeaderEvidence(
                "Elena Weber",
                "elena.weber@eurojob.eu",
                "Senior Java Engineer",
                new BigDecimal("6.5"),
                "Germany",
                "Berlin",
                "EU_CITIZEN",
                "ALREADY_IN_EUROPE",
                "https://linkedin.com/in/elena-weber",
                "https://github.com/elena-weber",
                null,
                "Senior FinTech backend specialist focused on SEPA settlement."
        );

        List<SkillEvidence> skills = List.of(
                new SkillEvidence("Java 21", new BigDecimal("6.0"), "EXPERT", true),
                new SkillEvidence("Spring Boot", new BigDecimal("5.0"), "EXPERT", true),
                new SkillEvidence("PostgreSQL", new BigDecimal("5.0"), "ADVANCED", false),
                new SkillEvidence("Docker", new BigDecimal("4.0"), "INTERMEDIATE", false)
        );

        List<WorkExperienceEvidence> work = List.of(
                new WorkExperienceEvidence(
                        "ClearPay Systems",
                        "Senior Java Engineer",
                        "Berlin, Germany",
                        LocalDate.of(2021, 3, 1),
                        null,
                        true,
                        "- Built SEPA Instant ledger processing 5,000 TPS in Java 21 and Spring Boot\n- Managed PostgreSQL partitioning",
                        List.of("Java 21", "Spring Boot", "PostgreSQL")
                )
        );

        List<EducationEvidence> edus = List.of(
                new EducationEvidence(
                        "Technical University of Munich",
                        "M.Sc.",
                        "Computer Science",
                        LocalDate.of(2016, 10, 1),
                        LocalDate.of(2018, 9, 30),
                        "1.3",
                        "Germany"
                )
        );

        ParsedResumeJsonDto parsed = ParsedResumeJsonDto.builder()
                .summary("Senior FinTech Java engineer")
                .projects(List.of(ParsedResumeProjectDto.builder()
                        .name("SEPA Clearing Sandbox")
                        .description("ISO 20022 settlement simulator using Java 21 and PostgreSQL.")
                        .technologies(List.of("Java 21", "PostgreSQL"))
                        .url("https://github.com/elena/sepa-sandbox")
                        .build()))
                .educations(List.of(ParsedResumeEducationDto.builder()
                        .institution("Technical University of Munich")
                        .degree("M.Sc.")
                        .fieldOfStudy("Computer Science")
                        .country("Germany")
                        .build()))
                .languages(List.of("English (C2)", "German (B1)"))
                .certifications(List.of("Oracle Certified Java 21 Developer"))
                .build();

        MasterResumeEvidence master = new MasterResumeEvidence(
                10L,
                "11111111-1111-1111-1111-111111111111",
                "Elena Master CV",
                "elena-master.pdf",
                "application/pdf",
                "users/elena/elena-master.pdf",
                1,
                true,
                parsed,
                null
        );

        JobEvidence job = new JobEvidence(
                42L,
                "Staff Java Settlement Engineer",
                "Adyen N.V.",
                "Netherlands",
                "Amsterdam",
                "Amsterdam, Netherlands",
                "HYBRID",
                "FULL_TIME",
                "SENIOR",
                new BigDecimal("5.0"),
                "VERIFIED_SPONSOR",
                "FinTech",
                List.of("Java 21", "Spring Boot", "PostgreSQL", "Kafka"),
                "Architect real-time clearing APIs with Java, Spring Boot, Kafka, and optional Rust edge workers."
        );

        JobAiAnalysisEvidence jobAi = new JobAiAnalysisEvidence(
                "5+ years backend Java settlement experience",
                List.of("Java 21", "Spring Boot", "PostgreSQL", "Kafka"),
                List.of("Rust"),
                List.of("Lead SEPA Instant platform scalability"),
                List.of("English (Fluent)"),
                "Senior JVM backend opportunity in Amsterdam."
        );

        return new TailoredResumeContextSnapshot(
                1L,
                42L,
                ResumeTemplate.MODERN,
                "Focus on SEPA ledger throughput",
                header,
                skills,
                work,
                edus,
                master,
                job,
                jobAi,
                new BigDecimal("88.50"),
                null
        );
    }
}

