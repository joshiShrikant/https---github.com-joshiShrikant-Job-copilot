package com.eurojobcopilot.modules.dashboard.controller;

import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.notification.entity.Notification;
import com.eurojobcopilot.modules.notification.entity.NotificationType;
import com.eurojobcopilot.modules.notification.repository.NotificationRepository;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Autowired
    private UserJobInteractionRepository userJobInteractionRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserJobPreferenceRepository userJobPreferenceRepository;

    @Autowired
    private UserWorkExperienceRepository userWorkExperienceRepository;

    @Autowired
    private UserEducationRepository userEducationRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        notificationRepository.deleteAllInBatch();
        jobApplicationRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        userJobInteractionRepository.deleteAllInBatch();
        resumeRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        userJobPreferenceRepository.deleteAllInBatch();
        userWorkExperienceRepository.deleteAllInBatch();
        userEducationRepository.deleteAllInBatch();
        userSkillRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void summaryEndpoint_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void summaryEndpoint_aggregatesUserScopedAndMarketMetricsWithStrictTenantIsolation() throws Exception {
        String tokenA = registerUser("dash.a@eurojob.eu", "Alex", "Mueller");
        String tokenB = registerUser("dash.b@eurojob.eu", "Lukas", "Meyer");

        User userA = userRepository.findByEmailIgnoreCase("dash.a@eurojob.eu").orElseThrow();

        Company adyen = companyRepository.saveAndFlush(Company.builder()
                .name("Adyen")
                .normalizedName("adyen")
                .headquartersCountry("Netherlands")
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.HIGH)
                .build());

        Job jobSponsoredToday = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash("hash-job-1-dash")
                .company(adyen)
                .companyName("Adyen")
                .title("Senior Java Engineer")
                .normalizedTitle("senior java engineer")
                .country("Netherlands")
                .city("Amsterdam")
                .description("Build resilient payment systems with visa sponsorship.")
                .sourceProvider("arbeitnow")
                .originalUrl("https://jobs.example.eu/1")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(Instant.now())
                .active(true)
                .build());

        Job jobOlderUnsponsored = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash("hash-job-2-dash")
                .companyName("Berlin Tech GmbH")
                .title("Backend Developer")
                .normalizedTitle("backend developer")
                .country("Germany")
                .city("Berlin")
                .description("Local Berlin engineering role.")
                .sourceProvider("adzuna")
                .originalUrl("https://jobs.example.eu/2")
                .sponsorStatus(JobSponsorStatus.UNCONFIRMED)
                .postedAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .active(true)
                .build());

        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(userA)
                .currentDesignation("Senior Java Engineer")
                .currentCountry("India")
                .bio("6+ years distributed systems experience.")
                .build());

        userSkillRepository.saveAndFlush(UserSkill.builder()
                .user(userA)
                .skillName("Java")
                .normalizedSkill("java")
                .yearsExperience(new BigDecimal("6.0"))
                .primary(true)
                .build());

        userJobPreferenceRepository.saveAndFlush(UserJobPreference.builder()
                .user(userA)
                .preferredJobTitles(List.of("Senior Java Engineer"))
                .preferredCountries(List.of("Netherlands", "Germany"))
                .minMatchScore(75)
                .autopilotEnabled(true)
                .build());

        resumeRepository.saveAndFlush(Resume.builder()
                .user(userA)
                .title("Master Java Resume 2026")
                .fileName("master.pdf")
                .filePath("/tmp/master.pdf")
                .fileSizeBytes(102400L)
                .mimeType("application/pdf")
                .versionNumber(1)
                .masterApproved(true)
                .build());

        jobMatchScoreRepository.saveAndFlush(JobMatchScore.builder()
                .user(userA)
                .job(jobSponsoredToday)
                .finalScore(new BigDecimal("88.50"))
                .deterministicScore(new BigDecimal("85.00"))
                .build());

        jobMatchScoreRepository.saveAndFlush(JobMatchScore.builder()
                .user(userA)
                .job(jobOlderUnsponsored)
                .finalScore(new BigDecimal("52.00"))
                .deterministicScore(new BigDecimal("52.00"))
                .build());

        userJobInteractionRepository.saveAndFlush(UserJobInteraction.builder()
                .user(userA)
                .job(jobSponsoredToday)
                .saved(true)
                .savedAt(Instant.now())
                .interested(true)
                .interestedAt(Instant.now())
                .build());

        jobApplicationRepository.saveAndFlush(JobApplication.builder()
                .user(userA)
                .job(jobSponsoredToday)
                .status(ApplicationStatus.APPLIED)
                .appliedDate(Instant.now())
                .build());

        jobApplicationRepository.saveAndFlush(JobApplication.builder()
                .user(userA)
                .job(jobOlderUnsponsored)
                .status(ApplicationStatus.REJECTED)
                .appliedDate(Instant.now().minus(2, ChronoUnit.DAYS))
                .build());

        notificationRepository.saveAndFlush(Notification.builder()
                .user(userA)
                .type(NotificationType.HIGH_MATCH_JOB)
                .title("New 88% match at Adyen")
                .message("Senior Java Engineer in Amsterdam matches your sponsorship profile.")
                .read(false)
                .build());

        // User A sees complete personalized metrics
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobsDiscovered").value(2))
                .andExpect(jsonPath("$.newJobsToday").value(1))
                .andExpect(jsonPath("$.highMatchJobsCount").value(1))
                .andExpect(jsonPath("$.sponsorshipOpportunitiesCount").value(1))
                .andExpect(jsonPath("$.verifiedSponsorsCount").value(1))
                .andExpect(jsonPath("$.savedJobsCount").value(1))
                .andExpect(jsonPath("$.interestedJobsCount").value(1))
                .andExpect(jsonPath("$.activeApplicationsCount").value(1))
                .andExpect(jsonPath("$.unreadNotificationsCount").value(1))
                .andExpect(jsonPath("$.readiness.profileExists").value(true))
                .andExpect(jsonPath("$.readiness.profileCompletenessPercent").value(60))
                .andExpect(jsonPath("$.readiness.skillsCount").value(1))
                .andExpect(jsonPath("$.readiness.resumesCount").value(1))
                .andExpect(jsonPath("$.readiness.masterResumeApproved").value(true))
                .andExpect(jsonPath("$.readiness.preferencesConfigured").value(true))
                .andExpect(jsonPath("$.readiness.autopilotEnabled").value(true))
                .andExpect(jsonPath("$.readiness.minMatchScore").value(75))
                .andExpect(jsonPath("$.readiness.preferredCountries", hasItem("Netherlands")))
                .andExpect(jsonPath("$.pipelineBreakdown.length()").value(ApplicationStatus.values().length))
                .andExpect(jsonPath("$.pipelineBreakdown[?(@.status=='APPLIED')].count").value(hasItem(1)))
                .andExpect(jsonPath("$.pipelineBreakdown[?(@.status=='REJECTED')].count").value(hasItem(1)))
                .andExpect(jsonPath("$.recentActivity.length()").value(5));

        // User B sees shared market job counts, zero user-scoped activity/applications/readiness
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobsDiscovered").value(2))
                .andExpect(jsonPath("$.highMatchJobsCount").value(0))
                .andExpect(jsonPath("$.savedJobsCount").value(0))
                .andExpect(jsonPath("$.activeApplicationsCount").value(0))
                .andExpect(jsonPath("$.unreadNotificationsCount").value(0))
                .andExpect(jsonPath("$.readiness.profileExists").value(false))
                .andExpect(jsonPath("$.readiness.profileCompletenessPercent").value(0))
                .andExpect(jsonPath("$.readiness.masterResumeApproved").value(false))
                .andExpect(jsonPath("$.recentActivity.length()").value(0));
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
