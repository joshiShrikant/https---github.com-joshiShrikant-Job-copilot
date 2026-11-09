package com.eurojobcopilot.modules.autopilot.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.autopilot.repository.AutopilotRunLogRepository;
import com.eurojobcopilot.modules.autopilot.service.AutopilotService;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.notification.repository.NotificationRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutopilotAndNotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AutopilotService autopilotService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private AutopilotRunLogRepository runLogRepository;
    @Autowired
    private JobMatchScoreRepository matchScoreRepository;
    @Autowired
    private JobApplicationRepository applicationRepository;
    @Autowired
    private UserJobPreferenceRepository preferenceRepository;
    @Autowired
    private UserSkillRepository skillRepository;
    @Autowired
    private UserProfileRepository profileRepository;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;

    private String tokenOwner;
    private String tokenOther;
    private User owner;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        tokenOwner = registerUser("autopilot.owner@eurojob.eu", "Lukas", "Mueller");
        tokenOther = registerUser("autopilot.other@eurojob.eu", "Emma", "Lind");
        owner = userRepository.findByEmailIgnoreCase("autopilot.owner@eurojob.eu").orElseThrow();

        profileRepository.saveAndFlush(UserProfile.builder()
                .user(owner)
                .currentDesignation("Senior Java & Angular Engineer")
                .yearsOfExperience(new BigDecimal("6.0"))
                .currentCountry("India")
                .build());

        for (String skill : List.of("Java 21", "Spring Boot", "Angular", "TypeScript", "MySQL")) {
            skillRepository.saveAndFlush(UserSkill.builder()
                    .user(owner)
                    .skillName(skill)
                    .normalizedSkill(skill.toLowerCase())
                    .yearsExperience(new BigDecimal("5.0"))
                    .proficiencyLevel(ProficiencyLevel.EXPERT)
                    .primary(true)
                    .build());
        }

        preferenceRepository.saveAndFlush(UserJobPreference.builder()
                .user(owner)
                .preferredJobTitles(List.of("Senior Fullstack", "Java", "Angular"))
                .preferredCountries(List.of("Netherlands", "Germany"))
                .minSalary(new BigDecimal("80000.00"))
                .requiresSponsorship(true)
                .minMatchScore(60)
                .autopilotEnabled(true)
                .autopilotPaused(false)
                .build());

        Company nlCompany = companyRepository.saveAndFlush(
                TestEntityFactory.createCompany("Adyen Autopilot NL", "Netherlands", true));
        Company noSponsorCompany = companyRepository.saveAndFlush(
                TestEntityFactory.createCompany("Local Only GmbH", "Germany", false));

        Job topMatchJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("autopilot-job-1"))
                .company(nlCompany)
                .companyName(nlCompany.getName())
                .title("Senior Fullstack Java + Angular Engineer")
                .country("Netherlands")
                .city("Amsterdam")
                .location("Amsterdam, Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .salaryMin(new BigDecimal("90000.00"))
                .salaryMax(new BigDecimal("115000.00"))
                .salaryCurrency("EUR")
                .skills(List.of("Java 21", "Spring Boot", "Angular", "TypeScript", "MySQL"))
                .description("English speaking international team building payment platforms with verified visa sponsorship.")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.example.eu/ap-1")
                .postedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .active(true)
                .build());

        Job trackedFollowUpJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("autopilot-job-2"))
                .company(nlCompany)
                .companyName(nlCompany.getName())
                .title("Lead Backend Java Architect")
                .country("Netherlands")
                .city("Amsterdam")
                .location("Amsterdam, Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .experienceLevel(ExperienceLevel.SENIOR)
                .skills(List.of("Java 21", "Spring Boot"))
                .description("Senior backend Java architecture role with full IND visa sponsorship.")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .sourceProvider("ADZUNA")
                .originalUrl("https://jobs.example.eu/ap-2")
                .postedAt(Instant.now().minus(5, ChronoUnit.HOURS))
                .active(true)
                .build());

        jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("autopilot-job-3-no-sponsor"))
                .company(noSponsorCompany)
                .companyName(noSponsorCompany.getName())
                .title("Angular Engineer")
                .country("Germany")
                .city("Munich")
                .location("Munich, Germany")
                .workArrangement(WorkArrangement.ONSITE)
                .skills(List.of("Angular", "Java"))
                .description("Onsite role without visa sponsorship support.")
                .sponsorStatus(JobSponsorStatus.NO_SPONSORSHIP)
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.example.eu/ap-3")
                .postedAt(Instant.now().minus(6, ChronoUnit.HOURS))
                .active(true)
                .build());

        applicationRepository.saveAndFlush(JobApplication.builder()
                .user(owner)
                .job(trackedFollowUpJob)
                .status(ApplicationStatus.APPLIED)
                .appliedDate(Instant.now().minus(4, ChronoUnit.DAYS))
                .nextFollowUpDate(LocalDate.now())
                .build());

        assertThat(topMatchJob.getId()).isPositive();
    }

    @AfterEach
    void cleanDatabase() {
        notificationRepository.deleteAllInBatch();
        runLogRepository.deleteAllInBatch();
        matchScoreRepository.deleteAllInBatch();
        applicationRepository.deleteAllInBatch();
        preferenceRepository.deleteAllInBatch();
        skillRepository.deleteAllInBatch();
        profileRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void autopilotAndNotifications_fullWorkflowWithDedupePauseResumeAndIsolation() throws Exception {
        // Initial status check
        mockMvc.perform(get("/api/v1/autopilot/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autopilotEnabled").value(true))
                .andExpect(jsonPath("$.autopilotPaused").value(false))
                .andExpect(jsonPath("$.serverEmailRelayEnabled").value(false));

        // Run manual scan -> scores jobs, recommends Adyen NL (excludes NO_SPONSORSHIP & already-applied), creates deduped alerts
        mockMvc.perform(post("/api/v1/autopilot/run")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshExternalJobs\":false,\"analyzeTopMatchesWithAi\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.jobsEvaluated").value(3))
                .andExpect(jsonPath("$.recommendedJobsCount").value(1))
                .andExpect(jsonPath("$.notificationsCreated").value(2))
                .andExpect(jsonPath("$.emailDispatched").value(false));

        // Recommended jobs endpoint lists ranked item with verified sponsor & skill overlap
        mockMvc.perform(get("/api/v1/autopilot/recommended-jobs?excludeApplied=true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].verifiedSponsor").value(true))
                .andExpect(jsonPath("$.content[0].matchesPreferredCountry").value(true))
                .andExpect(jsonPath("$.content[0].job.companyName").value("Adyen Autopilot NL"));

        // Running manual scan a second time creates 0 duplicate notifications
        mockMvc.perform(post("/api/v1/autopilot/run")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationsCreated").value(0));

        // Run history recorded 2 runs
        mockMvc.perform(get("/api/v1/autopilot/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        // Notifications list + unread count + mark one read + tenant isolation + mark all read + delete
        MvcResult notifListRes = mockMvc.perform(get("/api/v1/notifications?unreadOnly=true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn();

        long firstNotifId = objectMapper.readTree(notifListRes.getResponse().getContentAsString())
                .get("content").get(0).get("id").asLong();

        // Other user cannot access or read owner's notifications
        mockMvc.perform(patch("/api/v1/notifications/" + firstNotifId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/notifications/" + firstNotifId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(post("/api/v1/notifications/mark-all-read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedCount").value(1))
                .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(delete("/api/v1/notifications/" + firstNotifId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk());

        // Pause -> scheduled scan logs SKIPPED_PAUSED; Resume restores active state
        mockMvc.perform(post("/api/v1/autopilot/pause")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autopilotPaused").value(true));

        var scheduledResult = autopilotService.executeScheduledScan(AuthenticatedUserPrincipal.from(owner));
        assertThat(scheduledResult.getStatus().name()).isEqualTo("SKIPPED_PAUSED");

        mockMvc.perform(post("/api/v1/autopilot/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autopilotPaused").value(false));
    }

    private String registerUser(String email, String firstName, String lastName) throws Exception {
        RegisterRequest reg = RegisterRequest.builder()
                .email(email)
                .password("SecureP@ssw0rd2026!")
                .firstName(firstName)
                .lastName(lastName)
                .build();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}
