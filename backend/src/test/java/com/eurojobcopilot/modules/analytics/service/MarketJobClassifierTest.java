package com.eurojobcopilot.modules.analytics.service;

import com.eurojobcopilot.modules.analytics.service.MarketJobClassifier.EnglishLanguageStatus;
import com.eurojobcopilot.modules.analytics.service.MarketJobClassifier.JobClassification;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketJobClassifierTest {

    private final MarketJobClassifier classifier = new MarketJobClassifier();

    @Test
    void classify_distinguishesJavaFromJavaScriptAndIdentifiesAngularFullStackAndEnglish() {
        Job jsFrontend = Job.builder()
                .title("Senior Angular Frontend Engineer")
                .skills(List.of("Angular", "TypeScript", "JavaScript", "RxJS"))
                .description("English speaking product squad building web apps.")
                .salaryMin(new BigDecimal("6000.00"))
                .salaryMax(new BigDecimal("8000.00"))
                .salaryPeriod(SalaryPeriod.MONTHLY)
                .build();

        JobClassification c1 = classifier.classify(jsFrontend, null);
        assertThat(c1.angular()).isTrue();
        assertThat(c1.frontend()).isTrue();
        assertThat(c1.java()).isFalse();
        assertThat(c1.fullStack()).isFalse();
        assertThat(c1.englishStatus()).isEqualTo(EnglishLanguageStatus.CONFIRMED);
        assertThat(classifier.normalizeAnnualSalaryMidpoint(jsFrontend)).isEqualByComparingTo("84000.00");

        Job fullStackJava = Job.builder()
                .title("Staff Full-Stack Engineer (Java + Angular)")
                .skills(List.of("Java 21", "Spring Boot", "Angular"))
                .description("Fluent German required for public sector client meetings.")
                .build();

        JobClassification c2 = classifier.classify(fullStackJava, null);
        assertThat(c2.angular()).isTrue();
        assertThat(c2.frontend()).isTrue();
        assertThat(c2.java()).isTrue();
        assertThat(c2.fullStack()).isTrue();
        assertThat(c2.englishStatus()).isEqualTo(EnglishLanguageStatus.LOCAL_LANGUAGE_REQUIRED);
    }

    @Test
    void classify_treatsUnspecifiedCopyHonestlyWithoutGuessingEnglish() {
        Job unspecified = Job.builder()
                .title("Backend Developer")
                .skills(List.of("Spring Boot", "PostgreSQL"))
                .description("Join our fintech engineering team in Amsterdam.")
                .build();

        JobAiAnalysis optionalDutch = JobAiAnalysis.builder()
                .languageRequirements(List.of("English", "Dutch (nice to have)"))
                .build();

        assertThat(classifier.classify(unspecified, null).englishStatus())
                .isEqualTo(EnglishLanguageStatus.UNSPECIFIED);
        assertThat(classifier.classify(unspecified, optionalDutch).englishStatus())
                .isEqualTo(EnglishLanguageStatus.CONFIRMED);
    }
}

