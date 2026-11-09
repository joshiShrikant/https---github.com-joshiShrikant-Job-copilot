package com.eurojobcopilot.modules.job.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanySponsorshipRecordRepository;
import com.eurojobcopilot.support.TestEntityFactory;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobControllerDetailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private JobAiAnalysisRepository jobAiAnalysisRepository;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Autowired
    private UserJobInteractionRepository userJobInteractionRepository;

    @Autowired
    private CompanySponsorshipRecordRepository companySponsorshipRecordRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private String tokenA;
    private String tokenB;
    private User userA;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        tokenA = registerUser("detail.a@eurojob.eu", "Lukas", "Mueller");
        tokenB = registerUser("detail.b@eurojob.eu", "Elena", "Weber");
        userA = userRepository.findByEmailIgnoreCase("detail.a@eurojob.eu").orElseThrow();
    }

    @AfterEach
    void cleanDatabase() {
        jobApplicationRepository.deleteAllInBatch();
        jobAiAnalysisRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        userJobInteractionRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companySponsorshipRecordRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void getJobDetail_unauthenticatedInvalidOrMissingId_returnsExpectedErrors() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/jobs/0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/jobs/not-a-number")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/jobs/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getJobDetail_returnsFullIntelligenceWithStrictCrossUserIsolation() throws Exception {
        Instant now = Instant.now();

        Company adyen = companyRepository.saveAndFlush(Company.builder()
                .name("Adyen N.V.")
                .normalizedName("adyen")
                .websiteUrl("https://www.adyen.com")
                .careersUrl("https://careers.adyen.com")
                .headquartersCountry("Netherlands")
                .headquartersCity("Amsterdam")
                .industry("FinTech Payments")
                .companySize("1000-5000")
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .build());

        companySponsorshipRecordRepository.saveAndFlush(CompanySponsorshipRecord.builder()
                .company(adyen)
                .country("Netherlands")
                .registrySource("NL_IND_PUBLIC_REGISTER")
                .registryIdentifier("IND-34259528")
                .officialRegisteredName("Adyen N.V.")
                .status(SponsorshipRecordStatus.RECOGNIZED_SPONSOR)
                .evidenceNotes("Recognized referent on IND public list for Highly Skilled Migrants.")
                .sourceUrl("https://ind.nl/en/public-register-recognised-sponsors")
                .verifiedAt(now.minus(3, ChronoUnit.DAYS))
                .build());

        Job job = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("detail-adyen-1"))
                .company(adyen)
                .companyName("Adyen N.V.")
                .title("Senior Java & Spring Boot Backend Engineer")
                .normalizedTitle("senior java spring boot backend engineer")
                .location("Amsterdam, Netherlands")
                .city("Amsterdam")
                .country("Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .salaryMin(new BigDecimal("85000.00"))
                .salaryMax(new BigDecimal("112000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .description("Design resilient distributed payment systems with visa sponsorship and relocation package support.")
                .descriptionSnippet("Design resilient distributed payment systems.")
                .skills(List.of("Java 21", "Spring Boot", "PostgreSQL", "Kafka"))
                .sourceProvider("ARBEITNOW")
                .sourceJobId("arb-991")
                .originalUrl("https://careers.adyen.com/jobs/991")
                .applicationUrl("https://careers.adyen.com/jobs/991/apply")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(now.minus(4, ChronoUnit.HOURS))
                .active(true)
                .build());

        jobAiAnalysisRepository.saveAndFlush(JobAiAnalysis.builder()
                .job(job)
                .requiredSkills(List.of("Java 21", "Spring Boot", "PostgreSQL"))
                .niceToHaveSkills(List.of("Kafka", "Kubernetes"))
                .responsibilities(List.of("Own high-throughput payment settlement APIs"))
                .benefits(List.of("Relocation budget", "25 vacation days"))
                .languageRequirements(List.of("English (fluent)"))
                .visaSponsorshipAnalysis("IND Recognized Sponsor confirming HSM permit support.")
                .relocationAnalysis("Employer covers international relocation flight and temporary housing.")
                .summary("Senior backend Java engineering role in Amsterdam.")
                .aiModelUsed("gemini-2.5-flash")
                .build());

        jobMatchScoreRepository.saveAndFlush(JobMatchScore.builder()
                .user(userA)
                .job(job)
                .deterministicScore(new BigDecimal("87.00"))
                .aiScore(new BigDecimal("92.00"))
                .finalScore(new BigDecimal("89.50"))
                .skillScore(new BigDecimal("92.00"))
                .experienceScore(new BigDecimal("88.00"))
                .titleScore(new BigDecimal("95.00"))
                .locationScore(new BigDecimal("100.00"))
                .sponsorshipScore(new BigDecimal("100.00"))
                .salaryScore(new BigDecimal("82.00"))
                .matchingSkills(List.of("Java 21", "Spring Boot", "PostgreSQL"))
                .missingSkills(List.of("Kafka"))
                .aiRationale("Excellent Java and FinTech fit; emphasize distributed streaming experience.")
                .calculatedAt(now.minus(1, ChronoUnit.HOURS))
                .build());

        userJobInteractionRepository.saveAndFlush(UserJobInteraction.builder()
                .user(userA)
                .job(job)
                .saved(true)
                .savedAt(now.minus(2, ChronoUnit.HOURS))
                .interested(true)
                .interestedAt(now.minus(2, ChronoUnit.HOURS))
                .hidden(false)
                .hasApplied(true)
                .appliedAt(now.minus(30, ChronoUnit.MINUTES))
                .redirectClickedAt(now.minus(35, ChronoUnit.MINUTES))
                .build());

        jobApplicationRepository.saveAndFlush(JobApplication.builder()
                .user(userA)
                .job(job)
                .status(ApplicationStatus.APPLIED)
                .appliedDate(now.minus(30, ChronoUnit.MINUTES))
                .nextFollowUpDate(LocalDate.now().plusDays(7))
                .build());

        // User A sees complete personalized intelligence + public company/AI sections
        MvcResult resultA = mockMvc.perform(get("/api/v1/jobs/" + job.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(job.getId()))
                .andExpect(jsonPath("$.title").value("Senior Java & Spring Boot Backend Engineer"))
                .andExpect(jsonPath("$.sourceJobId").value("arb-991"))
                .andExpect(jsonPath("$.applicationUrl").value("https://careers.adyen.com/jobs/991/apply"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.isSaved").value(true))
                .andExpect(jsonPath("$.isInterested").value(true))
                .andExpect(jsonPath("$.isHidden").value(false))
                .andExpect(jsonPath("$.hasApplied").value(true))
                .andExpect(jsonPath("$.matchPercentage").value(90))
                .andExpect(jsonPath("$.company.id").value(adyen.getId()))
                .andExpect(jsonPath("$.company.isVerifiedSponsor").value(true))
                .andExpect(jsonPath("$.company.sponsorConfidenceLevel").value("VERIFIED"))
                .andExpect(jsonPath("$.sponsorshipBreakdown.sponsorStatus").value("VERIFIED_SPONSOR"))
                .andExpect(jsonPath("$.sponsorshipBreakdown.isVerifiedSponsor").value(true))
                .andExpect(jsonPath("$.sponsorshipBreakdown.confidenceLevel").value("VERIFIED"))
                .andExpect(jsonPath("$.sponsorshipBreakdown.registryRecords.length()").value(1))
                .andExpect(jsonPath("$.sponsorshipBreakdown.registryRecords[0].registrySource").value("NL_IND_PUBLIC_REGISTER"))
                .andExpect(jsonPath("$.sponsorshipBreakdown.registryRecords[0].registryIdentifier").value("IND-34259528"))
                .andExpect(jsonPath("$.sponsorshipBreakdown.evidenceSignals.length()").value(3))
                .andExpect(jsonPath("$.matchBreakdown.finalScore").value(89.5))
                .andExpect(jsonPath("$.matchBreakdown.matchPercentage").value(90))
                .andExpect(jsonPath("$.matchBreakdown.skillScore").value(92.0))
                .andExpect(jsonPath("$.matchBreakdown.sponsorshipScore").value(100.0))
                .andExpect(jsonPath("$.matchBreakdown.matchingSkills.length()").value(3))
                .andExpect(jsonPath("$.matchBreakdown.missingSkills[0]").value("Kafka"))
                .andExpect(jsonPath("$.aiAnalysis.aiModelUsed").value("gemini-2.5-flash"))
                .andExpect(jsonPath("$.aiAnalysis.requiredSkills.length()").value(3))
                .andExpect(jsonPath("$.application.status").value("APPLIED"))
                .andReturn();

        JsonNode jsonA = objectMapper.readTree(resultA.getResponse().getContentAsString());
        assertThat(jsonA.has("active")).isFalse();
        assertThat(jsonA.has("saved")).isFalse();
        assertThat(jsonA.has("interested")).isFalse();
        assertThat(jsonA.has("hidden")).isFalse();
        assertThat(jsonA.path("company").has("verifiedSponsor")).isFalse();
        assertThat(jsonA.path("sponsorshipBreakdown").has("verifiedSponsor")).isFalse();

        // User B receives public job, sponsorship, and AI analysis, with zero leaked User A interaction/match/application data
        mockMvc.perform(get("/api/v1/jobs/" + job.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(job.getId()))
                .andExpect(jsonPath("$.company.isVerifiedSponsor").value(true))
                .andExpect(jsonPath("$.sponsorshipBreakdown.registryRecords.length()").value(1))
                .andExpect(jsonPath("$.aiAnalysis.aiModelUsed").value("gemini-2.5-flash"))
                .andExpect(jsonPath("$.matchPercentage").isEmpty())
                .andExpect(jsonPath("$.matchBreakdown").isEmpty())
                .andExpect(jsonPath("$.application").isEmpty())
                .andExpect(jsonPath("$.isSaved").value(false))
                .andExpect(jsonPath("$.isInterested").value(false))
                .andExpect(jsonPath("$.isHidden").value(false))
                .andExpect(jsonPath("$.hasApplied").value(false));
    }

    @Test
    void getJobDetail_inactiveJobWithoutLinkedCompanyOrMatch_stillLoadsCleanlyForApplicantHistory() throws Exception {
        Job archivedJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("detail-archived-no-company"))
                .company(null)
                .companyName("Small Boutique GmbH")
                .title("Backend Developer")
                .normalizedTitle("backend developer")
                .country("Germany")
                .city("Munich")
                .location("Munich, Germany")
                .description("Local backend role without visa sponsorship available.")
                .sourceProvider("ADZUNA")
                .originalUrl("https://jobs.example.eu/boutique-1")
                .sponsorStatus(JobSponsorStatus.NO_SPONSORSHIP)
                .postedAt(Instant.now().minus(30, ChronoUnit.DAYS))
                .active(false)
                .build());

        mockMvc.perform(get("/api/v1/jobs/" + archivedJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(archivedJob.getId()))
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.company").isEmpty())
                .andExpect(jsonPath("$.matchBreakdown").isEmpty())
                .andExpect(jsonPath("$.aiAnalysis").isEmpty())
                .andExpect(jsonPath("$.sponsorshipBreakdown.sponsorStatus").value("NO_SPONSORSHIP"))
                .andExpect(jsonPath("$.sponsorshipBreakdown.isVerifiedSponsor").value(false))
                .andExpect(jsonPath("$.sponsorshipBreakdown.confidenceLevel").value("LOW"))
                .andExpect(jsonPath("$.sponsorshipBreakdown.registryRecords.length()").value(0));
    }

    private String registerUser(String email, String firstName, String lastName) throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email(email)
                .password("SecureP@ssw0rd2026!")
                .firstName(firstName)
                .lastName(lastName)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}

