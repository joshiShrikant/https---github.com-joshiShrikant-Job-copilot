package com.eurojobcopilot.modules.job.repository;

import com.eurojobcopilot.config.JpaAuditingConfig;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.FetchStatus;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobFetchLog;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanySponsorshipRecordRepository;
import com.eurojobcopilot.support.TestEntityFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class SponsorshipAndJobRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanySponsorshipRecordRepository sponsorshipRecordRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobFetchLogRepository jobFetchLogRepository;

    @Autowired
    private JobAiAnalysisRepository jobAiAnalysisRepository;

    @Autowired
    private UserJobInteractionRepository interactionRepository;

    @Autowired
    private JobMatchScoreRepository matchScoreRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistCompanySponsorshipJobsAiAnalysisInteractionsAndScores() {
        User user = userRepository.saveAndFlush(TestEntityFactory.createUser("matcher@eurojob.eu"));

        Company company = companyRepository.saveAndFlush(
                TestEntityFactory.createCompany("Adyen N.V.", "Netherlands", true)
        );

        sponsorshipRecordRepository.saveAndFlush(CompanySponsorshipRecord.builder()
                .company(company)
                .country("Netherlands")
                .registrySource("IND_NETHERLANDS")
                .registryIdentifier("KVK-34259528")
                .officialRegisteredName("Adyen N.V.")
                .status(SponsorshipRecordStatus.RECOGNIZED_SPONSOR)
                .evidenceNotes("Verified in official IND public register")
                .verifiedAt(Instant.now())
                .build());

        Job job = TestEntityFactory.createJob(company, "Staff Java & Angular Engineer", "Netherlands", "adyen-1");
        String longDescription = "Long job description block. ".repeat(3000); // > 80KB -> validates LONGTEXT storage
        job.setDescription(longDescription);
        Job savedJob = jobRepository.saveAndFlush(job);

        jobFetchLogRepository.saveAndFlush(JobFetchLog.builder()
                .providerName("ARBEITNOW")
                .status(FetchStatus.SUCCESS)
                .jobsFetched(50)
                .jobsInserted(12)
                .jobsUpdated(4)
                .jobsSkippedDuplicates(34)
                .executionDurationMs(1420L)
                .build());

        jobAiAnalysisRepository.saveAndFlush(JobAiAnalysis.builder()
                .job(savedJob)
                .requiredSkills(List.of("Java 21", "Spring Boot", "MySQL"))
                .niceToHaveSkills(List.of("Angular", "Docker"))
                .responsibilities(List.of("Design resilient payment APIs", "Mentor engineers"))
                .benefits(List.of("30% ruling eligibility", "Relocation package"))
                .languageRequirements(List.of("English"))
                .visaSponsorshipAnalysis("Confirmed IND recognized sponsor offering full HSM visa transfer.")
                .aiModelUsed("gemini-2.5-pro")
                .build());

        interactionRepository.saveAndFlush(UserJobInteraction.builder()
                .user(user)
                .job(savedJob)
                .saved(true)
                .savedAt(Instant.now())
                .interested(true)
                .interestedAt(Instant.now())
                .build());

        matchScoreRepository.saveAndFlush(JobMatchScore.builder()
                .user(user)
                .job(savedJob)
                .deterministicScore(new BigDecimal("88.50"))
                .aiScore(new BigDecimal("92.00"))
                .finalScore(new BigDecimal("90.25"))
                .skillScore(new BigDecimal("36.00"))
                .experienceScore(new BigDecimal("19.00"))
                .titleScore(new BigDecimal("14.00"))
                .locationScore(new BigDecimal("9.50"))
                .sponsorshipScore(new BigDecimal("10.00"))
                .salaryScore(new BigDecimal("4.00"))
                .matchingSkills(List.of("Java 21", "Spring Boot"))
                .missingSkills(List.of("Kubernetes"))
                .aiRationale("Strong alignment with European fintech stack.")
                .build());

        entityManager.flush();
        entityManager.clear();

        assertThat(companyRepository.findByNormalizedName("adyen n.v.")).isPresent();
        assertThat(companyRepository.findAllByVerifiedSponsorTrue(PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(sponsorshipRecordRepository.findByCompanyIdAndCountryIgnoreCaseAndRegistrySourceIgnoreCase(
                company.getId(), "netherlands", "ind_netherlands"
        )).isPresent();

        Job reloadedJob = jobRepository.findByCanonicalHash(savedJob.getCanonicalHash()).orElseThrow();
        assertThat(reloadedJob.getDescription()).hasSize(longDescription.length());
        assertThat(reloadedJob.getSkills()).containsExactly("Java 21", "Spring Boot", "Angular", "MySQL");
        assertThat(jobRepository.findAllByActiveTrueAndCountryIgnoreCaseAndSponsorStatus(
                "netherlands", JobSponsorStatus.VERIFIED_SPONSOR, PageRequest.of(0, 10)
        ).getContent()).hasSize(1);

        assertThat(jobRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("country"), "Netherlands"),
                cb.isTrue(root.get("active"))
        ), PageRequest.of(0, 10)).getContent()).hasSize(1);

        assertThat(jobFetchLogRepository.findTopByProviderNameOrderByFetchedAtDesc("ARBEITNOW")).isPresent();
        assertThat(jobAiAnalysisRepository.findByJobId(savedJob.getId()))
                .isPresent()
                .get()
                .satisfies(analysis -> assertThat(analysis.getRequiredSkills()).contains("Java 21", "Spring Boot"));

        assertThat(interactionRepository.findAllByUserIdAndSavedTrueOrderBySavedAtDesc(user.getId(), PageRequest.of(0, 10))
                .getContent()).hasSize(1);

        assertThat(matchScoreRepository.findAllByUserIdAndFinalScoreGreaterThanEqualOrderByFinalScoreDesc(
                user.getId(), new BigDecimal("85.00"), PageRequest.of(0, 10)
        ).getContent()).singleElement().satisfies(score -> {
            assertThat(score.getMatchingSkills()).containsExactly("Java 21", "Spring Boot");
            assertThat(score.getMissingSkills()).containsExactly("Kubernetes");
        });
    }
}
