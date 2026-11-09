package com.eurojobcopilot.modules.job.normalization;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.ingestion.RawJobPosting;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JobNormalizerTest {

    private final JobNormalizer normalizer = new JobNormalizer(new CanonicalHashGenerator());

    @Test
    void shouldSanitizeHtmlInferVisaSkillsCountryAndSwapInvertedSalaryBounds() {
        RawJobPosting raw = RawJobPosting.builder()
                .sourceProvider("arbeitnow")
                .sourceJobId("job-123")
                .title("Senior Java & Spring Boot Engineer (m/f/d)")
                .companyName("Adyen B.V.")
                .location("Amsterdam, NL")
                .remoteFlag(false)
                .workArrangementHint("hybrid")
                .employmentTypeHint("full_time")
                .salaryMin(new BigDecimal("110000"))
                .salaryMax(new BigDecimal("85000"))
                .salaryCurrency("€")
                .salaryPeriodHint("annual")
                .rawDescription("""
                        <script>alert('xss')</script>
                        <p>Build <strong>distributed</strong> payment APIs in Java 21, Spring Boot &amp; PostgreSQL.</p>
                        <ul><li>Full EU Blue Card / Highly Skilled Migrant visa sponsorship provided</li><li>Kafka & Kubernetes</li></ul>
                        """)
                .tags(List.of("java", "Spring Boot", "Microservices"))
                .originalUrl("https://jobs.example.com/123")
                .postedAt(Instant.parse("2026-11-08T10:00:00Z"))
                .build();

        Optional<NormalizedJobData> resultOpt = normalizer.normalize(raw);
        assertThat(resultOpt).isPresent();

        NormalizedJobData data = resultOpt.get();
        assertThat(data.sourceProvider()).isEqualTo("ARBEITNOW");
        assertThat(data.country()).isEqualTo("Netherlands");
        assertThat(data.city()).isEqualTo("Amsterdam");
        assertThat(data.workArrangement()).isEqualTo(WorkArrangement.HYBRID);
        assertThat(data.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(data.experienceLevel()).isEqualTo(ExperienceLevel.SENIOR);
        assertThat(data.salaryMin()).isEqualByComparingTo("85000");
        assertThat(data.salaryMax()).isEqualByComparingTo("110000");
        assertThat(data.salaryCurrency()).isEqualTo("EUR");
        assertThat(data.salaryPeriod()).isEqualTo(SalaryPeriod.YEARLY);
        assertThat(data.sponsorStatus()).isEqualTo(JobSponsorStatus.SPONSORSHIP_MENTIONED);
        assertThat(data.description()).doesNotContain("<script>", "<p>", "<li>", "<strong>");
        assertThat(data.description()).contains("Spring Boot & PostgreSQL");
        assertThat(data.skills()).contains("Java", "Spring Boot", "Microservices", "PostgreSQL", "Kafka", "Kubernetes");
        assertThat(data.canonicalHash()).hasSize(64);
    }

    @Test
    void shouldRespectExplicitNegativeSponsorshipWarningAndDetectRelocationOffers() {
        RawJobPosting noSponsorRaw = RawJobPosting.builder()
                .sourceProvider("ADZUNA")
                .title("Backend Kotlin Developer")
                .companyName("Local Berlin Tech")
                .city("Berlin")
                .country("DE")
                .rawDescription("Great role in Berlin. Note: Visa sponsorship is not available for this position.")
                .originalUrl("https://adzuna.de/job/9")
                .build();

        NormalizedJobData noSponsor = normalizer.normalize(noSponsorRaw).orElseThrow();
        assertThat(noSponsor.country()).isEqualTo("Germany");
        assertThat(noSponsor.sponsorStatus()).isEqualTo(JobSponsorStatus.NO_SPONSORSHIP);

        RawJobPosting relocationRaw = RawJobPosting.builder()
                .sourceProvider("ADZUNA")
                .title("Frontend Angular Architect")
                .companyName("Nordic FinTech")
                .location("Stockholm, Sverige")
                .rawDescription("Join our team! Generous relocation package included for EU/EEA moves.")
                .originalUrl("https://adzuna.se/job/10")
                .build();

        NormalizedJobData relocation = normalizer.normalize(relocationRaw).orElseThrow();
        assertThat(relocation.country()).isEqualTo("Sweden");
        assertThat(relocation.experienceLevel()).isEqualTo(ExperienceLevel.LEAD);
        assertThat(relocation.sponsorStatus()).isEqualTo(JobSponsorStatus.RELOCATION_OFFERED);
    }

    @Test
    void shouldRejectInvalidOrIncompletePayloadsAndClampOversizedFields() {
        RawJobPosting invalidUrl = RawJobPosting.builder()
                .sourceProvider("ARBEITNOW")
                .title("Java Engineer")
                .companyName("Acme")
                .country("Germany")
                .originalUrl("javascript:alert(1)")
                .build();
        assertThat(normalizer.normalize(invalidUrl)).isEmpty();

        RawJobPosting missingCountry = RawJobPosting.builder()
                .sourceProvider("ARBEITNOW")
                .title("Java Engineer")
                .companyName("Acme")
                .location("Unknown Hub")
                .originalUrl("https://example.com/job/1")
                .build();
        assertThat(normalizer.normalize(missingCountry)).isEmpty();

        String longTitle = "Senior Software Engineer ".repeat(20);
        String longDescription = "Java Spring Boot cloud engineering role. ".repeat(30);
        RawJobPosting oversized = RawJobPosting.builder()
                .sourceProvider("ARBEITNOW")
                .title(longTitle)
                .companyName("Mega Corp GmbH")
                .country("Deutschland")
                .rawDescription(longDescription)
                .originalUrl("https://example.com/jobs/" + "a".repeat(1200))
                .build();

        NormalizedJobData normalized = normalizer.normalize(oversized).orElseThrow();
        assertThat(normalized.title().length()).isLessThanOrEqualTo(255);
        assertThat(normalized.normalizedTitle().length()).isLessThanOrEqualTo(255);
        assertThat(normalized.descriptionSnippet().length()).isLessThanOrEqualTo(500);
        assertThat(normalized.originalUrl().length()).isLessThanOrEqualTo(1000);
        assertThat(normalized.country()).isEqualTo("Germany");
    }

    @Test
    void shouldExtractFrontendAndExpandedTechnicalSkillsFromDescription() {
        RawJobPosting frontendRaw = RawJobPosting.builder()
                .sourceProvider("ADZUNA")
                .title("Senior Angular Lead")
                .companyName("Berlin Digital GmbH")
                .city("Berlin")
                .country("DE")
                .rawDescription("We use Angular 17, RxJS, NgRx, HTML5, SCSS, Git, Jenkins, and micro-frontends alongside Spring Boot APIs.")
                .originalUrl("https://adzuna.de/job/frontend-42")
                .build();

        NormalizedJobData data = normalizer.normalize(frontendRaw).orElseThrow();
        assertThat(data.skills())
                .contains("Spring Boot", "Angular", "RxJS", "NgRx", "HTML", "CSS", "Git", "Jenkins", "Micro-Frontends");
    }
}

