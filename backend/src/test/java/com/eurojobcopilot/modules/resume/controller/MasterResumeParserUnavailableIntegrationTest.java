package com.eurojobcopilot.modules.resume.controller;

import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.resume.dto.ApproveMasterResumeRequest;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProfileDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeSkillDto;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MasterResumeParserUnavailableIntegrationTest {

    private static final String MIME_PDF = "application/pdf";

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
    private ResumeRepository resumeRepository;

    @Autowired
    private UserEducationRepository userEducationRepository;

    @Autowired
    private UserWorkExperienceRepository userWorkExperienceRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        RegisterRequest register = RegisterRequest.builder()
                .email("resume.ai.disabled@eurojob.eu")
                .password("SecureP@ssw0rd2026!")
                .firstName("Lina")
                .lastName("Vogel")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        token = json.get("accessToken").asText();
    }

    @AfterEach
    void cleanUp() throws IOException {
        resumeRepository.deleteAllInBatch();
        userEducationRepository.deleteAllInBatch();
        userWorkExperienceRepository.deleteAllInBatch();
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
    void parseEndpoint_returns503WhenAiDisabled_whileManualReviewAndApproveRemainsOperational() throws Exception {
        String fakeUuid = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/resumes/" + fakeUuid + "/parse"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/resumes/" + fakeUuid + "/parsed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/v1/resumes/master/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/v1/resumes/not-a-uuid/parse")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/v1/resumes/" + fakeUuid + "/parse")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        byte[] pdfBytes = createReadablePdfBytes(
                "Lina Vogel - Senior Java Engineer",
                "Munich, Germany | Java 21, Spring Boot, Docker, PostgreSQL"
        );
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(new MockMultipartFile("file", "lina-cv.pdf", MIME_PDF, pdfBytes))
                        .param("title", "Lina Master CV")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        String resumeUuid = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("uuid").asText();

        // app.ai.enabled=false in test profile -> explicit 503 without fabricating fake AI parse data
        mockMvc.perform(post("/api/v1/resumes/" + resumeUuid + "/parse")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        mockMvc.perform(get("/api/v1/resumes/" + resumeUuid + "/parsed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("NOT_PARSED"))
                .andExpect(jsonPath("$.isMasterApproved").value(false))
                .andExpect(jsonPath("$.parsedData").isEmpty());

        // Candidate can still enter/review manual structured CV data and approve cleanly offline/without AI
        ApproveMasterResumeRequest manualApprove = ApproveMasterResumeRequest.builder()
                .resumeUuid(resumeUuid)
                .approvedParsedData(ParsedResumeJsonDto.builder()
                        .profile(ParsedResumeProfileDto.builder()
                                .currentDesignation("Senior Java Engineer")
                                .currentCity("Munich")
                                .currentCountry("Germany")
                                .build())
                        .summary("Senior Java Engineer based in Munich.")
                        .skills(List.of(ParsedResumeSkillDto.builder()
                                .skillName("Java 21")
                                .proficiencyLevel(ProficiencyLevel.EXPERT)
                                .primary(true)
                                .build()))
                        .build())
                .build();

        mockMvc.perform(post("/api/v1/resumes/master/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manualApprove))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isMasterApproved").value(true))
                .andExpect(jsonPath("$.parseStatus").value("APPROVED"))
                .andExpect(jsonPath("$.profileSyncSummary.skillsAdded").value(1));
    }

    private byte[] createReadablePdfBytes(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.setLeading(15f);
                cs.newLineAtOffset(40, 740);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}

