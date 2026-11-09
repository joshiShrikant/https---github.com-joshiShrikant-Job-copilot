package com.eurojobcopilot.modules.coverletter.generator;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.coverletter.dto.UpdateCoverLetterRequest;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetter;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.CandidateHeaderEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.EducationEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.JobAiAnalysisEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.PreferenceEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.SkillEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.WorkExperienceEvidence;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.matching.config.MatchingProperties;
import com.eurojobcopilot.modules.matching.engine.DeterministicJobMatchingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverLetterValidatorTest {

    private CoverLetterValidator validator;

    @BeforeEach
    void setUp() {
        CanonicalHashGenerator hashGenerator = new CanonicalHashGenerator();
        JobNormalizer jobNormalizer = new JobNormalizer(hashGenerator);
        DeterministicJobMatchingEngine matchingEngine =
                new DeterministicJobMatchingEngine(hashGenerator, jobNormalizer, new MatchingProperties());
        validator = new CoverLetterValidator(matchingEngine);
    }

    @Test
    void validateAiDraft_stripsHtmlEnforcesSkillVerificationAndFlagsVisaAndUnverifiedJdLeaks() {
        CoverLetterContextSnapshot snapshot = buildSnapshot("NEEDS_SPONSORSHIP", "WILLING_TO_RELOCATE", 30);

        RawAiCoverLetterPayload raw = new RawAiCoverLetterPayload();
        raw.setSalutation("<script>alert(1)</script>Dear Adyen Hiring Team,");
        raw.setOpeningParagraph("I am excited to apply for Staff Java Engineer at <b>Adyen N.V.</b>");
        raw.setBodyParagraphs(List.of(
                "At ClearPay Systems, I scaled SEPA settlement microservices in Java 21, Spring Boot, and Rust.",
                "With 6+ years building fault-tolerant European payment rails on PostgreSQL, I thrive in high-reliability fintech teams."
        ));
        raw.setRelocationAndAuthorizationParagraph(
                "As an unrestricted EU citizen with no visa sponsorship required, I can join in Amsterdam with 30 days notice."
        );
        raw.setClosingParagraph("I would welcome the opportunity to discuss how my architecture experience supports Adyen.");
        raw.setSignOff("Kind regards,\nElena Weber");
        raw.setCompanyConnectionSummary("Strong architectural fit for Adyen's SEPA instant payments mission.");
        raw.setKeyAlignmentPoints(List.of(
                "6.5 years Java 21 & Spring Boot payments leadership",
                "Verified M.Sc. Computer Science from TU Munich"
        ));
        raw.setHighlightedSkills(List.of("Java 21", "Spring Boot", "Rust", "GraphQL"));
        raw.setWarnings(List.of("No Dutch fluency claimed."));

        ValidatedCoverLetter result = validator.validateAiDraft(raw, snapshot);

        assertThat(result.content())
                .doesNotContain("<script>", "<b>")
                .contains("Dear Adyen Hiring Team,", "ClearPay Systems", "Kind regards,\nElena Weber");
        assertThat(result.wordCount()).isGreaterThan(35);

        // Unverified skills stripped from highlightedSkills
        assertThat(result.metadata().getHighlightedSkills())
                .containsExactly("Java 21", "Spring Boot");

        // Grounding notices flag both unverified JD skill "Rust" leak in prose AND sponsorship contradiction
        assertThat(result.metadata().getGroundingWarnings())
                .anyMatch(w -> w.contains("Removed unverified skill 'Rust'"))
                .anyMatch(w -> w.contains("Removed unverified skill 'GraphQL'"))
                .anyMatch(w -> w.contains("referenced unverified JD skill 'Rust'"))
                .anyMatch(w -> w.contains("visa sponsorship is required"));

        assertThat(result.metadata().getWorkAuthorizationSummary())
                .contains("Requires Visa Sponsorship", "Open to relocation", "30-day notice");
        assertThat(result.metadata().getRelocationReadinessNote())
                .contains("relocating to Netherlands", "verified sponsor in Netherlands", "Notice period: 30 days");
    }

    @Test
    void validateAiDraft_throwsAiValidationExceptionWhenEmptyProse() {
        CoverLetterContextSnapshot snapshot = buildSnapshot("EU_CITIZEN", "ALREADY_IN_EUROPE", 30);
        RawAiCoverLetterPayload empty = new RawAiCoverLetterPayload();
        empty.setSalutation("Dear Team,");
        empty.setOpeningParagraph("Hi.");

        assertThatThrownBy(() -> validator.validateAiDraft(empty, snapshot))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("empty or unusable draft");
    }

    @Test
    void revalidateEditedDraft_recalculatesWordCountStripsHtmlAndFlagsManualEditIssues() {
        CoverLetterContextSnapshot snapshot = buildSnapshot("NEEDS_SPONSORSHIP", "WILLING_TO_RELOCATE", 45);

        RawAiCoverLetterPayload initialRaw = new RawAiCoverLetterPayload();
        initialRaw.setOpeningParagraph("Grounded opening for Adyen Staff Java Engineer position.");
        initialRaw.setBodyParagraphs(List.of("Built SEPA ledgers in Java 21 and Spring Boot at ClearPay Systems."));
        initialRaw.setClosingParagraph("Thank you for considering my application.");

        ValidatedCoverLetter initial = validator.validateAiDraft(initialRaw, snapshot);

        CoverLetter persisted = CoverLetter.builder()
                .user(null)
                .job(null)
                .title("Initial Draft")
                .content(initial.content())
                .tone(CoverLetterTone.PROFESSIONAL)
                .versionNumber(1)
                .build();

        UpdateCoverLetterRequest blankEdit = UpdateCoverLetterRequest.builder()
                .content("   <b>  </b>   ")
                .build();
        assertThatThrownBy(() -> validator.revalidateEditedDraft(persisted, initial.metadata(), blankEdit, snapshot))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("must not be blank");

        UpdateCoverLetterRequest validEdit = UpdateCoverLetterRequest.builder()
                .title("Edited Custom Letter")
                .content("<p>Dear Hiring Team,</p>\n\nI bring deep Java 21 and PostgreSQL clearing expertise and hold no visa sponsorship required status for Amsterdam.")
                .build();

        ValidatedCoverLetter updated = validator.revalidateEditedDraft(
                persisted, initial.metadata(), validEdit, snapshot);
        assertThat(updated.content()).doesNotContain("<p>", "</p>");
        assertThat(updated.wordCount()).isEqualTo(21);
        assertThat(updated.metadata().getWordCount()).isEqualTo(21);
        assertThat(updated.metadata().getGroundingWarnings())
                .anyMatch(w -> w.startsWith("Manual edit note:") && w.contains("visa sponsorship is required"));
    }

    private CoverLetterContextSnapshot buildSnapshot(
            String workAuthStatus,
            String relocationPref,
            Integer noticeDays
    ) {
        CandidateHeaderEvidence header = new CandidateHeaderEvidence(
                "Elena Weber",
                "elena.weber@eurojob.eu",
                "Senior Java Engineer",
                new BigDecimal("6.5"),
                "Germany",
                "Berlin",
                noticeDays,
                workAuthStatus,
                relocationPref,
                "https://linkedin.com/in/elena-weber",
                null,
                null,
                "Payments infrastructure backend engineer."
        );
        PreferenceEvidence pref = new PreferenceEvidence(
                List.of("Netherlands", "Germany"),
                List.of("Amsterdam", "Berlin"),
                "NEEDS_SPONSORSHIP".equals(workAuthStatus),
                true,
                "HYBRID"
        );
        List<SkillEvidence> skills = List.of(
                new SkillEvidence("Java 21", new BigDecimal("6.5"), "EXPERT", true),
                new SkillEvidence("Spring Boot", new BigDecimal("5.5"), "EXPERT", true),
                new SkillEvidence("PostgreSQL", new BigDecimal("5.0"), "ADVANCED", false)
        );
        List<WorkExperienceEvidence> work = List.of(
                new WorkExperienceEvidence(
                        "ClearPay Systems",
                        "Senior Java Engineer",
                        "Berlin, Germany",
                        LocalDate.of(2021, 3, 1),
                        null,
                        true,
                        "Built SEPA settlement platform in Java 21 & Spring Boot.",
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
                "Global payments platform powering European enterprises.",
                "1000-5000",
                List.of("Java 21", "PostgreSQL"),
                true,
                "VERIFIED",
                List.of("Java 21", "Spring Boot", "PostgreSQL", "Rust"),
                "Architect real-time SEPA APIs using Java 21, Spring Boot, PostgreSQL, and Rust workers."
        );
        JobAiAnalysisEvidence ai = new JobAiAnalysisEvidence(
                "5+ years backend Java",
                List.of("Java 21", "Spring Boot", "Rust"),
                List.of(),
                List.of("Scale SEPA instant services"),
                List.of("English (Fluent)"),
                true,
                "Verified Netherlands IND sponsor offering visa sponsorship.",
                true,
                "Relocation assistance provided to Amsterdam.",
                "Staff fintech backend role."
        );

        return new CoverLetterContextSnapshot(
                1L,
                42L,
                CoverLetterTone.PROFESSIONAL,
                null,
                header,
                pref,
                skills,
                work,
                edus,
                null,
                null,
                job,
                ai,
                new BigDecimal("86.00"),
                null
        );
    }
}
