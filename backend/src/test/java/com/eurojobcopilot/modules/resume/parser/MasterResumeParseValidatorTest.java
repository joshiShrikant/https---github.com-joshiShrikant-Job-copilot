package com.eurojobcopilot.modules.resume.parser;

import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MasterResumeParseValidatorTest {

    private MasterResumeParseValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MasterResumeParseValidator();
    }

    @Test
    void validate_normalizesDatesUrlsCountriesSkillsAndEmitsHumanReviewWarnings() {
        RawAiParsedResumePayload raw = new RawAiParsedResumePayload();
        RawAiParsedResumePayload.RawProfile profile = new RawAiParsedResumePayload.RawProfile();
        profile.setFullName("Elena Weber");
        profile.setCurrentDesignation("Senior Java Architect");
        profile.setYearsOfExperience(new BigDecimal("8.26"));
        profile.setCurrentCountry("deutschland");
        profile.setCurrentCity("Berlin");
        profile.setLinkedinUrl("www.linkedin.com/in/elena-weber");
        profile.setGithubUrl("javascript:alert(1)");
        profile.setWorkAuthorizationStatus("EU_CITIZEN");
        profile.setRelocationPreference("ALREADY_IN_EUROPE");
        raw.setProfile(profile);
        raw.setSummary("Backend architect with 8+ years designing European fintech systems.");

        RawAiParsedResumePayload.RawSkill s1 = new RawAiParsedResumePayload.RawSkill();
        s1.setSkillName("Java 21");
        s1.setYearsExperience(new BigDecimal("8"));
        s1.setProficiencyLevel("EXPERT");
        s1.setPrimary(false);

        RawAiParsedResumePayload.RawSkill s2 = new RawAiParsedResumePayload.RawSkill();
        s2.setSkillName("  java 21 ");
        s2.setPrimary(true);
        raw.setSkills(List.of(s1, s2));

        RawAiParsedResumePayload.RawExperience exp = new RawAiParsedResumePayload.RawExperience();
        exp.setCompanyName("Adyen N.V.");
        exp.setDesignation("Staff Engineer");
        exp.setStartDate("2022-03");
        exp.setEndDate("Present");
        exp.setDescription("Built low-latency payment clearing pipelines.");
        exp.setSkillsUsed(List.of("Java 21", "Kafka", "java 21"));
        raw.setExperiences(List.of(exp));

        RawAiParsedResumePayload.RawEducation edu = new RawAiParsedResumePayload.RawEducation();
        edu.setInstitution("Technical University of Munich");
        edu.setDegree("M.Sc.");
        edu.setFieldOfStudy("Computer Science");
        edu.setStartDate("2016");
        edu.setEndDate("2018-09-30");
        edu.setCountry(null);
        raw.setEducations(List.of(edu));

        ExtractedResumeText extracted = new ExtractedResumeText("CV text", 240, 1, "PDF", List.of());

        ValidatedMasterResumeParse validated = validator.validate(raw, extracted, true);

        assertThat(validated.parsedData().getProfile().getCurrentCountry()).isEqualTo("Germany");
        assertThat(validated.parsedData().getProfile().getYearsOfExperience()).isEqualByComparingTo("8.3");
        assertThat(validated.parsedData().getProfile().getLinkedinUrl()).isEqualTo("https://www.linkedin.com/in/elena-weber");
        assertThat(validated.parsedData().getProfile().getGithubUrl()).isNull();
        assertThat(validated.parsedData().getProfile().getWorkAuthorizationStatus()).isEqualTo(WorkAuthorizationStatus.EU_CITIZEN);
        assertThat(validated.parsedData().getProfile().getRelocationPreference()).isEqualTo(RelocationPreference.ALREADY_IN_EUROPE);

        assertThat(validated.parsedData().getSkills()).hasSize(1);
        assertThat(validated.parsedData().getSkills().get(0).isPrimary()).isTrue();
        assertThat(validated.parsedData().getSkills().get(0).getProficiencyLevel()).isEqualTo(ProficiencyLevel.EXPERT);

        assertThat(validated.parsedData().getExperiences()).hasSize(1);
        assertThat(validated.parsedData().getExperiences().get(0).getStartDate()).isEqualTo(LocalDate.of(2022, 3, 1));
        assertThat(validated.parsedData().getExperiences().get(0).isCurrent()).isTrue();
        assertThat(validated.parsedData().getExperiences().get(0).getEndDate()).isNull();
        assertThat(validated.parsedData().getExperiences().get(0).getSkillsUsed()).containsExactly("Java 21", "Kafka");

        assertThat(validated.parsedData().getEducations()).hasSize(1);
        assertThat(validated.parsedData().getEducations().get(0).getStartDate()).isEqualTo(LocalDate.of(2016, 1, 1));
        assertThat(validated.warnings()).anyMatch(w -> w.contains("githubUrl"));
        assertThat(validated.warnings()).anyMatch(w -> w.contains("Missing country for education"));
        assertThat(validated.warnings()).anyMatch(w -> w.contains("truncated"));
    }

    @Test
    void validate_nullOrEmptyPayload_throwsAiResponseValidationException() {
        ExtractedResumeText extracted = new ExtractedResumeText("CV text", 50, 1, "PDF", List.of());

        assertThatThrownBy(() -> validator.validate(null, extracted, false))
                .isInstanceOf(AiResponseValidationException.class);

        RawAiParsedResumePayload empty = new RawAiParsedResumePayload();
        assertThatThrownBy(() -> validator.validate(empty, extracted, false))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("empty extraction");
    }

    @Test
    void validate_invertedDatesOrOutOfBoundsExperience_throwsAiResponseValidationException() {
        ExtractedResumeText extracted = new ExtractedResumeText("CV text", 50, 1, "PDF", List.of());

        RawAiParsedResumePayload invalidYears = new RawAiParsedResumePayload();
        RawAiParsedResumePayload.RawProfile p = new RawAiParsedResumePayload.RawProfile();
        p.setYearsOfExperience(new BigDecimal("99.0"));
        invalidYears.setProfile(p);
        invalidYears.setSummary("Summary");

        assertThatThrownBy(() -> validator.validate(invalidYears, extracted, false))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("yearsOfExperience");

        RawAiParsedResumePayload invertedDates = new RawAiParsedResumePayload();
        RawAiParsedResumePayload.RawExperience exp = new RawAiParsedResumePayload.RawExperience();
        exp.setCompanyName("Acme");
        exp.setDesignation("Engineer");
        exp.setStartDate("2024-06-01");
        exp.setEndDate("2023-01-01");
        invertedDates.setExperiences(List.of(exp));

        assertThatThrownBy(() -> validator.validate(invertedDates, extracted, false))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("preceding startDate");
    }
}

