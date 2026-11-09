package com.eurojobcopilot.modules.company.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.company.dto.SaveCompanyRequest;
import com.eurojobcopilot.modules.company.entity.CompanyPriorityTier;
import com.eurojobcopilot.modules.company.repository.UserSavedCompanyRepository;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipCatalog;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.OfficialSponsorRegistryEntry;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanySponsorshipRecordRepository;
import com.eurojobcopilot.modules.sponsorship.repository.OfficialSponsorRegistryEntryRepository;
import com.eurojobcopilot.support.TestEntityFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompanyControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanySponsorshipRecordRepository sponsorshipRecordRepository;

    @Autowired
    private OfficialSponsorRegistryEntryRepository registryEntryRepository;

    @Autowired
    private UserSavedCompanyRepository savedCompanyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserJobInteractionRepository jobInteractionRepository;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        savedCompanyRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        jobInteractionRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        sponsorshipRecordRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        registryEntryRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void shouldRequireAuthenticationAcrossAllCompanyDirectoryEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/companies/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/companies/1/jobs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/companies/1/save"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/companies/1/save"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnEmptyDirectoryCleanlyBeforeAnyCompaniesAreIndexed() throws Exception {
        String token = registerUser("empty.companies@eurojob.eu");

        mockMvc.perform(get("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.verifiedSponsorsTotal").value(0))
                .andExpect(jsonPath("$.savedCompaniesTotal").value(0))
                .andExpect(jsonPath("$.isFirst").value(true))
                .andExpect(jsonPath("$.isLast").value(true));
    }

    @Test
    void shouldSearchFilterEnrichDossierPaginateOpenJobsAndIsolateSavedCompaniesPerUser() throws Exception {
        String userAToken = registerUser("alice.companies@eurojob.eu");
        String userBToken = registerUser("bob.companies@eurojob.eu");
        User userA = userRepository.findByEmailIgnoreCase("alice.companies@eurojob.eu").orElseThrow();

        Company adyen = companyRepository.saveAndFlush(Company.builder()
                .name("Adyen N.V.")
                .normalizedName("adyen")
                .description("Unified commerce and global payments platform built on Java.")
                .websiteUrl("https://www.adyen.com")
                .careersUrl("javascript:alert(1)")
                .logoUrl("https://www.adyen.com/logo.svg")
                .headquartersCountry("Netherlands")
                .headquartersCity("Amsterdam")
                .industry("FinTech")
                .companySize("1000-5000")
                .techStack(List.of("Java", "PostgreSQL", "Kafka"))
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .build());

        Company gitlab = companyRepository.saveAndFlush(Company.builder()
                .name("GitLab B.V.")
                .normalizedName("gitlab")
                .description("DevSecOps cloud engineering platform operating remote-first across Europe.")
                .websiteUrl("https://about.gitlab.com")
                .careersUrl("https://about.gitlab.com/jobs")
                .headquartersCountry("Germany")
                .headquartersCity("Berlin")
                .industry("Developer Tools")
                .companySize("1000-5000")
                .techStack(List.of("Go", "Ruby", "Kubernetes"))
                .verifiedSponsor(false)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.UNVERIFIED)
                .build());

        sponsorshipRecordRepository.saveAndFlush(CompanySponsorshipRecord.builder()
                .company(adyen)
                .country("Netherlands")
                .registrySource(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE)
                .registryIdentifier("KVK-34259528")
                .officialRegisteredName("Adyen N.V.")
                .status(SponsorshipRecordStatus.RECOGNIZED_SPONSOR)
                .confidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .evidenceNotes("Matched official IND recognized sponsor register")
                .sourceUrl("https://ind.nl/en/public-register-recognised-sponsors")
                .verifiedAt(Instant.now().minusSeconds(7200))
                .lastCheckedAt(Instant.now())
                .build());

        // Official live IND entry matches GitLab B.V. too to verify live registry merge & effective verification
        registryEntryRepository.saveAndFlush(OfficialSponsorRegistryEntry.builder()
                .country("Netherlands")
                .registrySource(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE)
                .registryIdentifier("KVK-66778899")
                .officialName("GitLab B.V.")
                .normalizedName("gitlab")
                .status(SponsorshipRecordStatus.RECOGNIZED_SPONSOR)
                .evidenceNotes("Synchronized from NL IND public register")
                .sourceUrl("https://ind.nl/en/public-register-recognised-sponsors")
                .active(true)
                .syncedAt(Instant.now())
                .build());

        Job adyenJavaRole = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("adyen-java-1"))
                .company(adyen)
                .companyName("Adyen N.V.")
                .title("Senior Java Backend Engineer")
                .location("Amsterdam, Netherlands")
                .city("Amsterdam")
                .country("Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .salaryMin(new BigDecimal("90000.00"))
                .salaryMax(new BigDecimal("115000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .description("Build resilient high-throughput payment services.")
                .skills(List.of("Java", "Spring Boot", "Angular"))
                .sourceProvider("ARBEITNOW")
                .sourceJobId("adyen-java-1")
                .originalUrl("https://careers.adyen.com/vacancies/java-1")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(Instant.now().minusSeconds(1800))
                .active(true)
                .build());

        Job adyenHiddenRole = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("adyen-hidden-1"))
                .company(adyen)
                .companyName("Adyen N.V.")
                .title("Payments Ops Specialist")
                .location("Madrid, Spain")
                .city("Madrid")
                .country("Spain")
                .workArrangement(WorkArrangement.REMOTE)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.MID)
                .description("Local contract operations support.")
                .skills(List.of("SQL"))
                .sourceProvider("ARBEITNOW")
                .sourceJobId("adyen-hidden-1")
                .originalUrl("https://careers.adyen.com/vacancies/ops-1")
                .sponsorStatus(JobSponsorStatus.NO_SPONSORSHIP)
                .postedAt(Instant.now().minusSeconds(900))
                .active(true)
                .build());

        jobMatchScoreRepository.saveAndFlush(JobMatchScore.builder()
                .user(userA)
                .job(adyenJavaRole)
                .deterministicScore(new BigDecimal("92.00"))
                .skillScore(new BigDecimal("90.00"))
                .experienceScore(new BigDecimal("90.00"))
                .titleScore(new BigDecimal("90.00"))
                .locationScore(new BigDecimal("100.00"))
                .sponsorshipScore(new BigDecimal("100.00"))
                .salaryScore(new BigDecimal("90.00"))
                .finalScore(new BigDecimal("92.00"))
                .matchingSkills(List.of("Java", "Spring Boot"))
                .missingSkills(List.of())
                .calculatedAt(Instant.now())
                .build());

        jobInteractionRepository.saveAndFlush(UserJobInteraction.builder()
                .user(userA)
                .job(adyenHiddenRole)
                .hidden(true)
                .hiddenAt(Instant.now())
                .build());

        // 1. Directory browse & filtering by country alias 'NL' + tech 'Angular'
        mockMvc.perform(get("/api/v1/companies")
                        .queryParam("country", "NL")
                        .queryParam("tech", "Angular")
                        .queryParam("verifiedSponsorOnly", "true")
                        .queryParam("withOpenJobsOnly", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.verifiedSponsorsTotal").value(1))
                .andExpect(jsonPath("$.savedCompaniesTotal").value(0))
                .andExpect(jsonPath("$.content[0].id").value(adyen.getId()))
                .andExpect(jsonPath("$.content[0].isVerifiedSponsor").value(true))
                .andExpect(jsonPath("$.content[0].activeJobsCount").value(2))
                .andExpect(jsonPath("$.content[0].sponsorshipJobsCount").value(1))
                .andExpect(jsonPath("$.content[0].remoteJobsCount").value(1))
                .andExpect(jsonPath("$.content[0].careersUrl").value(nullValue()))
                .andExpect(jsonPath("$.content[0].techStack", hasItem("Angular")))
                .andExpect(jsonPath("$.content[0].activeCountries", hasItem("Spain")))
                .andExpect(jsonPath("$.content[0].isSaved").value(false));

        // 2. Employer dossier endpoint checks sanitized links, merged IND records & mandatory caveat
        mockMvc.perform(get("/api/v1/companies/" + adyen.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Adyen N.V."))
                .andExpect(jsonPath("$.websiteUrl").value("https://www.adyen.com"))
                .andExpect(jsonPath("$.careersUrl").value(nullValue()))
                .andExpect(jsonPath("$.isVerifiedSponsor").value(true))
                .andExpect(jsonPath("$.vacancyCaveat").value(containsString("does NOT guarantee")))
                .andExpect(jsonPath("$.officialRegisterUrl").value(containsString("ind.nl")))
                .andExpect(jsonPath("$.registryRecords[0].registryIdentifier").value("KVK-34259528"))
                .andExpect(jsonPath("$.activeCities", hasItem("Madrid")));

        // Verify GitLab inherits live IND registry match smoothly on detail dossier
        mockMvc.perform(get("/api/v1/companies/" + gitlab.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVerifiedSponsor").value(true))
                .andExpect(jsonPath("$.registryRecords[0].registryIdentifier").value("KVK-66778899"))
                .andExpect(jsonPath("$.vacancyCaveat").value(containsString("vacancy")));

        // 3. Paginated company open jobs respects user-hidden flag and match score
        mockMvc.perform(get("/api/v1/companies/" + adyen.getId() + "/jobs")
                        .queryParam("sort", "matchScore,desc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(adyenJavaRole.getId()))
                .andExpect(jsonPath("$.content[0].matchPercentage").value(92));

        mockMvc.perform(get("/api/v1/companies/" + adyen.getId() + "/jobs")
                        .queryParam("includeHidden", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // 4. Bookmark save, update, and multi-tenant isolation
        SaveCompanyRequest saveReq = SaveCompanyRequest.builder()
                .priorityTier(CompanyPriorityTier.TOP_PRIORITY)
                .notes("Target Q3 payment engineering team")
                .build();

        MvcResult saveResult = mockMvc.perform(post("/api/v1/companies/" + adyen.getId() + "/save")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSaved").value(true))
                .andExpect(jsonPath("$.priorityTier").value("TOP_PRIORITY"))
                .andExpect(jsonPath("$.notes").value("Target Q3 payment engineering team"))
                .andReturn();

        String originalSavedAt = objectMapper.readTree(saveResult.getResponse().getContentAsString())
                .get("savedAt").asText();

        // Idempotent update retains original savedAt and updates tier/notes
        SaveCompanyRequest updateReq = SaveCompanyRequest.builder()
                .priorityTier(CompanyPriorityTier.WATCHLIST)
                .notes("Updated note after recruiter call")
                .build();

        mockMvc.perform(post("/api/v1/companies/" + adyen.getId() + "/save")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSaved").value(true))
                .andExpect(jsonPath("$.priorityTier").value("WATCHLIST"))
                .andExpect(jsonPath("$.notes").value("Updated note after recruiter call"))
                .andExpect(jsonPath("$.savedAt").value(originalSavedAt));

        // User B must NOT see User A's bookmark or notes
        mockMvc.perform(get("/api/v1/companies")
                        .queryParam("savedOnly", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.savedCompaniesTotal").value(0));

        mockMvc.perform(get("/api/v1/companies/" + adyen.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSaved").value(false))
                .andExpect(jsonPath("$.userNotes").value(nullValue()));

        // User A removes bookmark
        mockMvc.perform(delete("/api/v1/companies/" + adyen.getId() + "/save")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSaved").value(false))
                .andExpect(jsonPath("$.priorityTier").value(nullValue()));

        // 5. Validation & 404 contracts
        mockMvc.perform(get("/api/v1/companies/0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/companies/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/companies")
                        .queryParam("sort", "invalidSort,desc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/companies/" + adyen.getId() + "/save")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                SaveCompanyRequest.builder().notes("x".repeat(2005)).build())))
                .andExpect(status().isBadRequest());
    }

    private String registerUser(String email) throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email(email)
                .password("SecureP@ssw0rd2026!")
                .firstName("Test")
                .lastName("Candidate")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
