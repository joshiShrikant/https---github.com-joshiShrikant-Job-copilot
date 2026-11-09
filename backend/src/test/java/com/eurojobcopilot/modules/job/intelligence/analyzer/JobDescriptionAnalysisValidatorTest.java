package com.eurojobcopilot.modules.job.intelligence.analyzer;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobDescriptionAnalysisValidatorTest {

    private JobDescriptionAnalysisValidator validator;

    @BeforeEach
    void setUp() {
        JobNormalizer normalizer = new JobNormalizer(new CanonicalHashGenerator());
        validator = new JobDescriptionAnalysisValidator(normalizer);
    }

    @Test
    void validate_normalizesValidFourteenFieldExtractionAndDeduplicatesLists() {
        RawAiJdExtractionPayload raw = RawAiJdExtractionPayload.builder()
                .jobTitle("  Senior   Java & Angular Engineer  ")
                .company("Adyen N.V.")
                .country("nl")
                .location("Amsterdam, Netherlands")
                .requiredExperienceYears(new BigDecimal("5.24"))
                .requiredExperienceLevel("sr")
                .requiredExperienceSummary("5+ years building resilient Java backend APIs")
                .requiredSkills(List.of("Java 21", "Spring Boot", "java 21", "  ", "PostgreSQL"))
                .preferredSkills(List.of("Kafka", "Kubernetes", "KAFKA"))
                .responsibilities(List.of("Design payment settlement microservices", "Mentor junior engineers"))
                .benefits(List.of("Relocation budget", "Hybrid stipend"))
                .languageRequirements(List.of("English (C1)"))
                .salaryMin(new BigDecimal("85000"))
                .salaryMax(new BigDecimal("112500.5"))
                .salaryCurrency("€")
                .salaryPeriod("annual")
                .workArrangement("hybrid")
                .employmentType("full-time")
                .sponsorshipOffered(true)
                .visaSponsorshipAnalysis("IND Recognized Sponsor confirming EU HSM visa sponsorship.")
                .relocationAssistanceOffered(true)
                .relocationAnalysis("Includes flight reimbursement and 30-day housing.")
                .summary("Senior full-stack platform role on core checkout team.")
                .build();

        ValidatedJobDescriptionAnalysis validated = validator.validate(raw);

        assertThat(validated.extractedTitle()).isEqualTo("Senior Java & Angular Engineer");
        assertThat(validated.extractedCompany()).isEqualTo("Adyen N.V.");
        assertThat(validated.extractedCountry()).isEqualTo("Netherlands");
        assertThat(validated.extractedLocation()).isEqualTo("Amsterdam, Netherlands");
        assertThat(validated.requiredExperienceYears()).isEqualByComparingTo("5.2");
        assertThat(validated.requiredExperienceLevel()).isEqualTo(ExperienceLevel.SENIOR);
        assertThat(validated.requiredSkills()).containsExactly("Java 21", "Spring Boot", "PostgreSQL");
        assertThat(validated.niceToHaveSkills()).containsExactly("Kafka", "Kubernetes");
        assertThat(validated.responsibilities()).hasSize(2);
        assertThat(validated.benefits()).containsExactly("Relocation budget", "Hybrid stipend");
        assertThat(validated.languageRequirements()).containsExactly("English (C1)");
        assertThat(validated.salaryMin()).isEqualByComparingTo("85000.00");
        assertThat(validated.salaryMax()).isEqualByComparingTo("112500.50");
        assertThat(validated.salaryCurrency()).isEqualTo("EUR");
        assertThat(validated.salaryPeriod()).isEqualTo(SalaryPeriod.YEARLY);
        assertThat(validated.workArrangement()).isEqualTo(WorkArrangement.HYBRID);
        assertThat(validated.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(validated.sponsorshipOffered()).isTrue();
        assertThat(validated.relocationAssistanceOffered()).isTrue();
    }

    @Test
    void validate_rejectsNullEmptyAndInvalidDomainInvariants() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(AiResponseValidationException.class);

        RawAiJdExtractionPayload substanceless = RawAiJdExtractionPayload.builder()
                .jobTitle("Engineer")
                .requiredSkills(List.of())
                .responsibilities(List.of(" "))
                .summary(" ")
                .build();
        assertThatThrownBy(() -> validator.validate(substanceless))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("lacked required skills");

        RawAiJdExtractionPayload invertedSalary = RawAiJdExtractionPayload.builder()
                .summary("Valid summary")
                .salaryMin(new BigDecimal("120000"))
                .salaryMax(new BigDecimal("90000"))
                .build();
        assertThatThrownBy(() -> validator.validate(invertedSalary))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("salaryMin");

        RawAiJdExtractionPayload badCurrency = RawAiJdExtractionPayload.builder()
                .summary("Valid summary")
                .salaryCurrency("EURO")
                .build();
        assertThatThrownBy(() -> validator.validate(badCurrency))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("ISO code");

        RawAiJdExtractionPayload excessiveYears = RawAiJdExtractionPayload.builder()
                .summary("Valid summary")
                .requiredExperienceYears(new BigDecimal("55.0"))
                .build();
        assertThatThrownBy(() -> validator.validate(excessiveYears))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("between 0 and 40");

        RawAiJdExtractionPayload badArrangement = RawAiJdExtractionPayload.builder()
                .summary("Valid summary")
                .workArrangement("MOON_BASE")
                .build();
        assertThatThrownBy(() -> validator.validate(badArrangement))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("workArrangement");

        RawAiJdExtractionPayload badEmploymentType = RawAiJdExtractionPayload.builder()
                .summary("Valid summary")
                .employmentType("TEMP_SEASONAL")
                .build();
        assertThatThrownBy(() -> validator.validate(badEmploymentType))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("employmentType");
    }
}

