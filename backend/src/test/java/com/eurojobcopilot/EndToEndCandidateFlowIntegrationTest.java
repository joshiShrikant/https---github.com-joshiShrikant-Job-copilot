package com.eurojobcopilot;

import com.eurojobcopilot.modules.auth.controller.AuthController;
import com.eurojobcopilot.modules.auth.dto.LoginRequest;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.resume.repository.TailoredResumeRepository;
import com.eurojobcopilot.modules.user.dto.UpsertEducationRequest;
import com.eurojobcopilot.modules.user.dto.UpsertProfileRequest;
import com.eurojobcopilot.modules.user.dto.UpsertSkillRequest;
import com.eurojobcopilot.modules.user.dto.UpsertWorkExperienceRequest;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndToEndCandidateFlowIntegrationTest {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @TempDir
    static Path tempStorageDir;

    @DynamicPropertySource
    static void registerStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.resumes-path", () -> tempStorageDir.toString());
        registry.add("app.storage.max-file-size-bytes", () -> String.valueOf(10L * 1024L * 1024L));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TailoredResumeRepository tailoredResumeRepository;

    @Autowired
    private ResumeRepository resumeRepository;

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
    void cleanDatabaseAndStorage() throws IOException {
        tailoredResumeRepository.deleteAllInBatch();
        resumeRepository.deleteAllInBatch();
        userJobPreferenceRepository.deleteAllInBatch();
        userWorkExperienceRepository.deleteAllInBatch();
        userEducationRepository.deleteAllInBatch();
        userSkillRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        if (Files.exists(tempStorageDir)) {
            try (Stream<Path> walk = Files.walk(tempStorageDir)) {
                walk.filter(p -> !p.equals(tempStorageDir))
                        .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
    }

    @Test
    void fullCandidateJourney_healthAuthProfileResumeStorageSecurityAndLogout_worksEndToEnd() throws Exception {
        // 1. Public health and OpenAPI probes succeed without auth
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/health']").exists());

        // 2. Unauthenticated access to protected endpoints fails cleanly with 401 ApiErrorResponse
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/v1/resumes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 3. Register candidate -> 201 Created + JWT access token + HttpOnly refresh_token cookie
        RegisterRequest register = RegisterRequest.builder()
                .email("Shrikant.Candidate@EuroJob.EU")
                .password("SecureP@ssw0rd2026!")
                .firstName("Shrikant")
                .lastName("Joshi")
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("shrikant.candidate@eurojob.eu"))
                .andReturn();

        String accessToken1 = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        Cookie refreshCookie1 = registerResult.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(refreshCookie1).isNotNull();

        User persistedUser = userRepository.findByEmailIgnoreCase("shrikant.candidate@eurojob.eu").orElseThrow();
        assertThat(passwordEncoder.matches("SecureP@ssw0rd2026!", persistedUser.getPasswordHash())).isTrue();

        // 4. Verify /api/v1/auth/me with issued JWT
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(persistedUser.getUuid()))
                .andExpect(jsonPath("$.email").value("shrikant.candidate@eurojob.eu"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));

        // 5. Create complete candidate profile + skills + education + work experience
        UpsertProfileRequest createProfile = UpsertProfileRequest.builder()
                .currentDesignation("Senior Full-Stack Java Engineer")
                .yearsOfExperience(new BigDecimal("6.5"))
                .currentCountry("India")
                .currentCity("Pune")
                .expectedSalaryMin(new BigDecimal("85000.00"))
                .expectedSalaryCurrency("eur")
                .noticePeriodDays(30)
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .relocationPreference(RelocationPreference.WILLING_TO_RELOCATE)
                .linkedinUrl("https://www.linkedin.com/in/shrikant-joshi")
                .githubUrl("https://github.com/shrikant-joshi")
                .portfolioUrl("https://shrikant.dev")
                .bio("Building distributed Java/Spring & Angular SaaS systems for Europe.")
                .preferredJobTitles(List.of("Senior Java Engineer", "Staff Full-Stack Engineer"))
                .preferredCountries(List.of("Germany", "Netherlands", "Ireland"))
                .build();

        mockMvc.perform(post("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createProfile)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expectedSalaryCurrency").value("EUR"));

        mockMvc.perform(post("/api/v1/profile/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertSkillRequest.builder()
                                .skillName("Java")
                                .yearsExperience(new BigDecimal("6.5"))
                                .proficiencyLevel(ProficiencyLevel.EXPERT)
                                .primary(true)
                                .build())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/profile/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertSkillRequest.builder()
                                .skillName("Angular")
                                .yearsExperience(new BigDecimal("4.0"))
                                .proficiencyLevel(ProficiencyLevel.ADVANCED)
                                .primary(false)
                                .build())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/profile/education")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertEducationRequest.builder()
                                .institution("Pune Institute of Computer Technology")
                                .degree("B.E.")
                                .fieldOfStudy("Computer Engineering")
                                .startDate(LocalDate.of(2015, 8, 1))
                                .endDate(LocalDate.of(2019, 6, 30))
                                .gradeOrGpa("9.1/10")
                                .country("India")
                                .build())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/profile/experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertWorkExperienceRequest.builder()
                                .companyName("EuroTech Systems")
                                .designation("Lead Backend Engineer")
                                .location("Remote")
                                .startDate(LocalDate.of(2022, 1, 10))
                                .endDate(LocalDate.of(2025, 12, 31))
                                .current(true)
                                .description("Architected payment & identity microservices.")
                                .skillsUsed(List.of("Java 21", "Spring Boot", "MySQL"))
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endDate").value(nullValue()));

        // 6. Aggregated GET /api/v1/profile includes sorted skills, education, work experience, and preferences
        mockMvc.perform(get("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Shrikant Joshi"))
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.skills[0].skillName").value("Java"))
                .andExpect(jsonPath("$.skills[1].skillName").value("Angular"))
                .andExpect(jsonPath("$.educations[0].degree").value("B.E."))
                .andExpect(jsonPath("$.experiences[0].companyName").value("EuroTech Systems"))
                .andExpect(jsonPath("$.experiences[0].endDate").value(nullValue()))
                .andExpect(jsonPath("$.preferredCountries", containsInAnyOrder("Germany", "Netherlands", "Ireland")));

        // 7. Spoofed file upload rejected with 400 ApiErrorResponse and writes zero bytes
        MockMultipartFile spoofedPdf = new MockMultipartFile(
                "file", "spoofed.pdf", MIME_PDF, "NOT A PDF".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(spoofedPdf)
                        .param("title", "Malicious Payload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertThat(resumeRepository.count()).isZero();

        // 8. Valid PDF (v1) + OOXML DOCX (v2) master resume uploads succeed
        byte[] pdfBytes = "%PDF-1.7\n1 0 obj\n<< /Title (Master CV) >>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MvcResult pdfUploadResult = mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(new MockMultipartFile("file", "shrikant-master-2026.pdf", MIME_PDF, pdfBytes))
                        .param("title", "Master Full-Stack PDF CV")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.isMasterApproved").value(false))
                .andReturn();
        String pdfUuid = objectMapper.readTree(pdfUploadResult.getResponse().getContentAsString())
                .get("uuid").asText();

        byte[] docxBytes = createValidDocxBytes();
        MvcResult docxUploadResult = mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(new MockMultipartFile("file", "shrikant-europass.docx", MIME_DOCX, docxBytes))
                        .param("title", "Master Europass DOCX CV")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andReturn();
        String docxUuid = objectMapper.readTree(docxUploadResult.getResponse().getContentAsString())
                .get("uuid").asText();

        Resume pdfEntity = resumeRepository.findByUuid(pdfUuid).orElseThrow();
        Path pdfDiskPath = tempStorageDir.resolve(pdfEntity.getFilePath()).normalize();
        assertThat(pdfEntity.getParsedContentJson()).isNull();
        assertThat(Files.exists(pdfDiskPath)).isTrue();

        // 9. Refresh token rotation issues new JWT access token & rotates HttpOnly cookie
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String accessToken2 = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        Cookie refreshCookie2 = refreshResult.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(refreshCookie2).isNotNull();
        assertThat(refreshCookie2.getValue()).isNotEqualTo(refreshCookie1.getValue());

        // 10. Use rotated accessToken2 to list, download both binaries, and delete v1 PDF
        mockMvc.perform(get("/api/v1/resumes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].uuid").value(docxUuid))
                .andExpect(jsonPath("$[1].uuid").value(pdfUuid));

        MvcResult pdfDownload = mockMvc.perform(get("/api/v1/resumes/" + pdfUuid + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken2))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(pdfDownload.getResponse().getContentAsByteArray()).isEqualTo(pdfBytes);

        MvcResult docxDownload = mockMvc.perform(get("/api/v1/resumes/" + docxUuid + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken2))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(docxDownload.getResponse().getContentAsByteArray()).isEqualTo(docxBytes);

        // 11. Delete PDF -> gone from DB & disk while DOCX remains
        mockMvc.perform(delete("/api/v1/resumes/" + pdfUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(Files.exists(pdfDiskPath)).isFalse();
        assertThat(resumeRepository.findByUuid(docxUuid)).isPresent();

        // 12. Replay old refreshCookie1 -> 401 and revokes entire session family (including refreshCookie2)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie2))
                .andExpect(status().isUnauthorized());

        // 13. Re-login cleanly, update profile, verify updated data, and logout invalidating session
        MvcResult reloginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("shrikant.candidate@eurojob.eu", "SecureP@ssw0rd2026!"))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken3 = objectMapper.readTree(reloginResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        Cookie refreshCookie3 = reloginResult.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(refreshCookie3).isNotNull();

        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertProfileRequest.builder()
                                .currentDesignation("Principal Java Architect")
                                .currentCountry("Germany")
                                .currentCity("Munich")
                                .expectedSalaryCurrency("EUR")
                                .workAuthorizationStatus(WorkAuthorizationStatus.BLUE_CARD_HOLDER)
                                .relocationPreference(RelocationPreference.ALREADY_IN_EUROPE)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCity").value("Munich"))
                .andExpect(jsonPath("$.workAuthorizationStatus").value("BLUE_CARD_HOLDER"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken3)
                        .cookie(refreshCookie3))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie3))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crossModuleTenantIsolationAndErrorHygiene_preventsIdorAndSensitiveLeaks() throws Exception {
        String tokenA = registerAndGetAccessToken("owner.e2e@eurojob.eu", "Alice", "Weber");
        String tokenB = registerAndGetAccessToken("other.e2e@eurojob.eu", "Bob", "Klein");

        // Candidate A sets up profile, skill, and resume
        mockMvc.perform(post("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentDesignation\":\"Alice Confidential Title\"}"))
                .andExpect(status().isCreated());

        Long skillIdA = objectMapper.readTree(mockMvc.perform(post("/api/v1/profile/skills")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(UpsertSkillRequest.builder()
                                        .skillName("Kubernetes")
                                        .yearsExperience(new BigDecimal("4.0"))
                                        .proficiencyLevel(ProficiencyLevel.ADVANCED)
                                        .primary(true)
                                        .build())))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        byte[] pdfBytes = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj".getBytes(StandardCharsets.US_ASCII);
        String resumeUuidA = objectMapper.readTree(mockMvc.perform(multipart("/api/v1/resumes/upload")
                                .file(new MockMultipartFile("file", "alice-cv.pdf", MIME_PDF, pdfBytes))
                                .param("title", "Alice Confidential CV")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("uuid").asText();

        // Candidate B is completely isolated from Candidate A's profile, skills, and resumes
        mockMvc.perform(get("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/profile/skills/" + skillIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/resumes/" + resumeUuidA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/resumes/" + resumeUuidA + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/resumes/" + resumeUuidA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Candidate B is denied access to admin-only actuator metrics
        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // Validation error redacts password cleanly across public endpoints
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"password\":\"weak\",\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='password')].rejectedValue",
                        hasItem("[REDACTED]")));
    }

    private String registerAndGetAccessToken(String email, String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                                .email(email)
                                .password("SecureP@ssw0rd2026!")
                                .firstName(firstName)
                                .lastName(lastName)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private byte[] createValidDocxBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>"
                    .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document><w:body><w:p><w:r><w:t>Master CV</w:t></w:r></w:p></w:body></w:document>"
                    .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}

