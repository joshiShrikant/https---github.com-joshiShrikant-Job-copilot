package com.eurojobcopilot.modules.company.repository;

import com.eurojobcopilot.config.JpaAuditingConfig;
import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.company.dto.CompanySearchCriteria;
import com.eurojobcopilot.modules.company.dto.CompanySortOption;
import com.eurojobcopilot.modules.company.entity.CompanyPriorityTier;
import com.eurojobcopilot.modules.company.entity.UserSavedCompany;
import com.eurojobcopilot.modules.job.dto.JobSortOption;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.support.TestEntityFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, CompanyDirectoryRepositoryImpl.class})
class CompanyDirectoryRepositoryTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyDirectoryRepository companyDirectoryRepository;

    @Autowired
    private UserSavedCompanyRepository userSavedCompanyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserJobInteractionRepository interactionRepository;

    @Test
    void shouldFilterSortAndQuerySavedCompaniesTechStackAndCompanyJobs() {
        User userA = userRepository.saveAndFlush(TestEntityFactory.createUser("repo.a@eurojob.eu"));
        User userB = userRepository.saveAndFlush(TestEntityFactory.createUser("repo.b@eurojob.eu"));

        Company adyen = companyRepository.saveAndFlush(Company.builder()
                .name("Adyen")
                .normalizedName("adyen")
                .headquartersCountry("Netherlands")
                .headquartersCity("Amsterdam")
                .industry("FinTech Payments")
                .companySize("1000+")
                .description("Global financial technology platform processing European card and APM transactions.")
                .techStack(List.of("Java", "PostgreSQL", "Kafka"))
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .build());

        Company sap = companyRepository.saveAndFlush(Company.builder()
                .name("SAP SE")
                .normalizedName("sap")
                .headquartersCountry("Germany")
                .headquartersCity("Walldorf")
                .industry("Enterprise Cloud")
                .companySize("10000+")
                .description("Enterprise ERP cloud leader across DACH and Europe.")
                .techStack(List.of("Java", "Kubernetes", "SAP HANA"))
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .build());

        Company studio = companyRepository.saveAndFlush(Company.builder()
                .name("Nordic Indie Studio")
                .normalizedName("nordic indie studio")
                .headquartersCountry("Sweden")
                .headquartersCity("Stockholm")
                .industry("Consumer Apps")
                .companySize("11-50")
                .description("Small boutique frontend studio.")
                .techStack(List.of("TypeScript", "React"))
                .verifiedSponsor(false)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.UNVERIFIED)
                .build());

        Job adyenJob1 = TestEntityFactory.createJob(adyen, "Staff Java Engineer", "Netherlands", "adyen-1");
        adyenJob1.setSkills(List.of("Java", "Angular", "Docker"));
        adyenJob1.setPostedAt(Instant.now().minusSeconds(3600));
        jobRepository.saveAndFlush(adyenJob1);

        Job adyenJob2 = TestEntityFactory.createJob(adyen, "Platform SRE", "Germany", "adyen-2");
        adyenJob2.setSkills(List.of("Go", "Kubernetes"));
        adyenJob2.setWorkArrangement(WorkArrangement.REMOTE);
        adyenJob2.setSponsorStatus(JobSponsorStatus.SPONSORSHIP_MENTIONED);
        adyenJob2.setPostedAt(Instant.now().minusSeconds(7200));
        jobRepository.saveAndFlush(adyenJob2);

        Job adyenExpiredJob = TestEntityFactory.createJob(adyen, "Expired Legacy Role", "Netherlands", "adyen-exp");
        adyenExpiredJob.setExpiresAt(Instant.now().minusSeconds(3600));
        jobRepository.saveAndFlush(adyenExpiredJob);

        // Unlinked job referencing Adyen by companyName
        Job adyenUnlinkedJob = Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("adyen-unlinked"))
                .company(null)
                .companyName("Adyen")
                .title("Backend Kotlin Developer")
                .location("Amsterdam, Netherlands")
                .city("Amsterdam")
                .country("Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .description("Unlinked job record.")
                .skills(List.of("Kotlin", "Kafka"))
                .sourceProvider("ARBEITNOW")
                .sourceJobId("adyen-unlinked")
                .originalUrl("https://careers.adyen.com/jobs/unlinked")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(Instant.now().minusSeconds(600))
                .active(true)
                .build();
        jobRepository.saveAndFlush(adyenUnlinkedJob);

        Job sapJob = TestEntityFactory.createJob(sap, "Cloud Java Architect", "Germany", "sap-1");
        sapJob.setDescription("Enterprise Java and Spring Boot cloud architecture in Walldorf.");
        sapJob.setSkills(List.of("Java", "Spring Boot"));
        jobRepository.saveAndFlush(sapJob);

        // User A hides adyenJob2 and bookmarks SAP + Adyen
        interactionRepository.saveAndFlush(UserJobInteraction.builder()
                .user(userA)
                .job(adyenJob2)
                .hidden(true)
                .hiddenAt(Instant.now())
                .build());

        userSavedCompanyRepository.saveAndFlush(UserSavedCompany.builder()
                .user(userA)
                .company(sap)
                .priorityTier(CompanyPriorityTier.WATCHLIST)
                .notes("Track Q3 cloud hiring")
                .savedAt(Instant.now().minusSeconds(500))
                .build());

        userSavedCompanyRepository.saveAndFlush(UserSavedCompany.builder()
                .user(userA)
                .company(adyen)
                .priorityTier(CompanyPriorityTier.TOP_PRIORITY)
                .notes("Primary NL target")
                .savedAt(Instant.now())
                .build());

        userSavedCompanyRepository.saveAndFlush(UserSavedCompany.builder()
                .user(userB)
                .company(studio)
                .priorityTier(CompanyPriorityTier.TARGET)
                .savedAt(Instant.now())
                .build());

        Instant now = Instant.now();

        // 1. Default sort: activeJobs,desc -> Adyen (3 active) before SAP (1 active) before Studio (0 active)
        Page<Company> defaultPage = companyDirectoryRepository.searchCompanies(
                userA.getId(),
                CompanySearchCriteria.builder().build(),
                null,
                PageRequest.of(0, 10),
                CompanySortOption.ACTIVE_JOBS_DESC,
                now
        );
        assertThat(defaultPage.getContent()).extracting(Company::getName)
                .containsExactly("Adyen", "SAP SE", "Nordic Indie Studio");

        // 2. Tech filter across curated tech_stack and job skills (Angular only in adyenJob1 skills!)
        Page<Company> angularCompanies = companyDirectoryRepository.searchCompanies(
                userA.getId(),
                CompanySearchCriteria.builder().tech("Angular").build(),
                null,
                PageRequest.of(0, 10),
                CompanySortOption.NAME_ASC,
                now
        );
        assertThat(angularCompanies.getContent()).extracting(Company::getName).containsExactly("Adyen");

        // Job skills JSON cast match specifically ('Docker' is ONLY in adyenJob1.skills, not in description)
        Page<Company> dockerCompanies = companyDirectoryRepository.searchCompanies(
                userA.getId(),
                CompanySearchCriteria.builder().tech("Docker").build(),
                null,
                PageRequest.of(0, 10),
                CompanySortOption.NAME_ASC,
                now
        );
        assertThat(dockerCompanies.getContent()).extracting(Company::getName).containsExactly("Adyen");

        // Curated techStack match ("Kafka" in Adyen company tech_stack + "SAP HANA" in SAP)
        Page<Company> hanaCompanies = companyDirectoryRepository.searchCompanies(
                userA.getId(),
                CompanySearchCriteria.builder().tech("HANA").build(),
                null,
                PageRequest.of(0, 10),
                CompanySortOption.NAME_ASC,
                now
        );
        assertThat(hanaCompanies.getContent()).extracting(Company::getName).containsExactly("SAP SE");

        // 3. Country filter includes employers with active jobs in Germany (both SAP HQ and Adyen DE role)
        Page<Company> germanyCompanies = companyDirectoryRepository.searchCompanies(
                userA.getId(),
                CompanySearchCriteria.builder().country("Germany").build(),
                "Germany",
                PageRequest.of(0, 10),
                CompanySortOption.NAME_ASC,
                now
        );
        assertThat(germanyCompanies.getContent()).extracting(Company::getName)
                .containsExactly("Adyen", "SAP SE");

        // 4. verifiedSponsorOnly + withOpenJobsOnly
        assertThat(companyDirectoryRepository.countVerifiedSponsorCompanies()).isEqualTo(2L);
        Page<Company> verifiedOpen = companyDirectoryRepository.searchCompanies(
                userA.getId(),
                CompanySearchCriteria.builder().verifiedSponsorOnly(true).withOpenJobsOnly(true).build(),
                null,
                PageRequest.of(0, 10),
                CompanySortOption.NAME_ASC,
                now
        );
        assertThat(verifiedOpen.getContent()).extracting(Company::getName)
                .containsExactly("Adyen", "SAP SE");

        // 5. savedOnly + savedAt,desc tenant isolation
        Page<Company> userASaved = companyDirectoryRepository.searchCompanies(
                userA.getId(),
                CompanySearchCriteria.builder().savedOnly(true).build(),
                null,
                PageRequest.of(0, 10),
                CompanySortOption.SAVED_AT_DESC,
                now
        );
        assertThat(userASaved.getContent()).extracting(Company::getName)
                .containsExactly("Adyen", "SAP SE");
        assertThat(userSavedCompanyRepository.countByUserId(userA.getId())).isEqualTo(2L);
        assertThat(userSavedCompanyRepository.countByUserId(userB.getId())).isEqualTo(1L);

        // 6. Company active jobs excludes expired and user-hidden unless includeHidden=true
        Page<Job> adyenVisibleJobs = jobRepository.searchCompanyActiveJobs(
                userA.getId(),
                adyen.getId(),
                adyen.getName(),
                false,
                PageRequest.of(0, 10),
                JobSortOption.POSTED_AT_DESC,
                now
        );
        assertThat(adyenVisibleJobs.getContent()).extracting(Job::getTitle)
                .containsExactly("Backend Kotlin Developer", "Staff Java Engineer");

        Page<Job> adyenAllActiveJobs = jobRepository.searchCompanyActiveJobs(
                userA.getId(),
                adyen.getId(),
                adyen.getName(),
                true,
                PageRequest.of(0, 10),
                JobSortOption.POSTED_AT_DESC,
                now
        );
        assertThat(adyenAllActiveJobs.getContent()).extracting(Job::getTitle)
                .containsExactly("Backend Kotlin Developer", "Staff Java Engineer", "Platform SRE");
    }
}
