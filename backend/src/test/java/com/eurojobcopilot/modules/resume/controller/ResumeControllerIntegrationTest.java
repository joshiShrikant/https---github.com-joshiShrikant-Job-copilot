package com.eurojobcopilot.modules.resume.controller;

import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import com.eurojobcopilot.modules.resume.entity.StorageProvider;
import com.eurojobcopilot.modules.resume.entity.TailoredResume;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.resume.repository.TailoredResumeRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.support.TestEntityFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeControllerIntegrationTest {

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
    private TailoredResumeRepository tailoredResumeRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() throws IOException {
        tailoredResumeRepository.deleteAllInBatch();
        resumeRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
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
    void resumeEndpoints_unauthenticatedRequests_return401() throws Exception {
        String someUuid = UUID.randomUUID().toString();
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "cv.pdf", MIME_PDF, "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)
        );

        mockMvc.perform(multipart("/api/v1/resumes/upload").file(pdfFile).param("title", "CV"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/resumes"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/resumes/" + someUuid))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/resumes/" + someUuid + "/download"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/resumes/" + someUuid))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resumeLifecycle_uploadPdfAndDocxListGetDownloadAndDelete_worksEndToEnd() throws Exception {
        String token = registerUser("resume.owner@eurojob.eu", "Elena", "Weber");

        byte[] pdfBytes = "%PDF-1.7\n1 0 obj\n<< /Title (Master CV) >>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile pdfUpload = new MockMultipartFile("file", "elena-cv-2026.pdf", MIME_PDF, pdfBytes);

        // 1. Upload PDF -> 201
        MvcResult pdfResult = mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(pdfUpload)
                        .param("title", "Master Java Architect CV")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").isString())
                .andExpect(jsonPath("$.title").value("Master Java Architect CV"))
                .andExpect(jsonPath("$.fileName").value("elena-cv-2026.pdf"))
                .andExpect(jsonPath("$.fileSizeBytes").value(pdfBytes.length))
                .andExpect(jsonPath("$.mimeType").value(MIME_PDF))
                .andExpect(jsonPath("$.resumeType").value("MASTER"))
                .andExpect(jsonPath("$.storageProvider").value("LOCAL"))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.isMasterApproved").value(false))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        String pdfUuid = objectMapper.readTree(pdfResult.getResponse().getContentAsString()).get("uuid").asText();

        // 2. Upload valid OOXML DOCX -> 201 (version 2)
        byte[] docxBytes = createValidDocxBytes();
        MockMultipartFile docxUpload = new MockMultipartFile("file", "elena-europass.docx", MIME_DOCX, docxBytes);

        MvcResult docxResult = mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(docxUpload)
                        .param("title", "Europass DOCX CV")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.mimeType").value(MIME_DOCX))
                .andExpect(jsonPath("$.isMasterApproved").value(false))
                .andReturn();

        String docxUuid = objectMapper.readTree(docxResult.getResponse().getContentAsString()).get("uuid").asText();

        // Verify metadata stored safely in DB without AI parsing and without leaking original filename on disk
        Resume storedPdfEntity = resumeRepository.findByUuid(pdfUuid).orElseThrow();
        assertThat(storedPdfEntity.getResumeType()).isEqualTo(ResumeType.MASTER);
        assertThat(storedPdfEntity.getStorageProvider()).isEqualTo(StorageProvider.LOCAL);
        assertThat(storedPdfEntity.isMasterApproved()).isFalse();
        assertThat(storedPdfEntity.getParsedContentJson()).isNull();
        assertThat(storedPdfEntity.getFilePath()).endsWith(".pdf");
        assertThat(storedPdfEntity.getFilePath()).doesNotContain("elena-cv-2026");

        Path diskPdfPath = tempStorageDir.resolve(storedPdfEntity.getFilePath()).normalize();
        assertThat(Files.exists(diskPdfPath)).isTrue();
        assertThat(diskPdfPath.startsWith(tempStorageDir)).isTrue();

        // 3. List resumes -> newest version first
        mockMvc.perform(get("/api/v1/resumes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].uuid").value(docxUuid))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].uuid").value(pdfUuid))
                .andExpect(jsonPath("$[1].versionNumber").value(1));

        // 4. Get single resume metadata
        mockMvc.perform(get("/api/v1/resumes/" + pdfUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(pdfUuid))
                .andExpect(jsonPath("$.fileName").value("elena-cv-2026.pdf"));

        // 5. Download PDF -> exact binary match + safe headers
        MvcResult pdfDownload = mockMvc.perform(get("/api/v1/resumes/" + pdfUuid + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MIME_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("elena-cv-2026.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(pdfDownload.getResponse().getContentAsByteArray()).isEqualTo(pdfBytes);

        // 6. Download DOCX -> exact binary match + OOXML Content-Type
        MvcResult docxDownload = mockMvc.perform(get("/api/v1/resumes/" + docxUuid + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MIME_DOCX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("elena-europass.docx")))
                .andReturn();
        assertThat(docxDownload.getResponse().getContentAsByteArray()).isEqualTo(docxBytes);

        // 7. Delete PDF -> removes DB row and disk file
        mockMvc.perform(delete("/api/v1/resumes/" + pdfUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(resumeRepository.findByUuid(pdfUuid)).isEmpty();
        assertThat(Files.exists(diskPdfPath)).isFalse();

        mockMvc.perform(get("/api/v1/resumes/" + pdfUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/resumes/" + pdfUuid + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadValidation_spoofedMagicBytesOversizedAndMalformedRequests_return400Or413() throws Exception {
        String token = registerUser("validator@eurojob.eu", "Marcus", "Klein");

        // Spoofed text pretending to be PDF -> 400
        MockMultipartFile spoofedPdf = new MockMultipartFile(
                "file", "cv.pdf", MIME_PDF, "plain text pretending to be pdf".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(spoofedPdf)
                        .param("title", "Fake PDF")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Plain ZIP pretending to be DOCX -> 400
        byte[] plainZip = createPlainZipBytes("readme.txt", "not a word doc");
        MockMultipartFile spoofedDocx = new MockMultipartFile("file", "cv.docx", MIME_DOCX, plainZip);
        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(spoofedDocx)
                        .param("title", "Fake DOCX")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Path traversal filename -> 400
        MockMultipartFile traversalFile = new MockMultipartFile(
                "file", "../../etc/passwd.pdf", MIME_PDF, "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)
        );
        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(traversalFile)
                        .param("title", "Traversal Probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        // Blank title -> 400
        MockMultipartFile validPdf = new MockMultipartFile(
                "file", "good.pdf", MIME_PDF, "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)
        );
        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(validPdf)
                        .param("title", "   ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        // Missing file part -> 400
        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .param("title", "Missing File")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        // Oversized > 10MB PDF -> 413 Payload Too Large
        byte[] oversizedPdf = new byte[(10 * 1024 * 1024) + 1];
        System.arraycopy("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), 0, oversizedPdf, 0, 9);
        MockMultipartFile oversizedUpload = new MockMultipartFile("file", "huge.pdf", MIME_PDF, oversizedPdf);

        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(oversizedUpload)
                        .param("title", "Oversized CV")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413));

        // Zero DB rows or files stored for rejected uploads
        assertThat(resumeRepository.count()).isZero();
        try (Stream<Path> walk = Files.walk(tempStorageDir)) {
            assertThat(walk.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    @Test
    void ownershipIsolation_andConflictWhenReferencedByTailoredResume_enforcedStrictly() throws Exception {
        String tokenA = registerUser("alice.cv@eurojob.eu", "Alice", "Schmidt");
        String tokenB = registerUser("bob.cv@eurojob.eu", "Bob", "Intruder");

        byte[] pdfBytes = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj".getBytes(StandardCharsets.US_ASCII);
        MvcResult uploadA = mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(new MockMultipartFile("file", "alice.pdf", MIME_PDF, pdfBytes))
                        .param("title", "Alice Private Master Resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andReturn();
        String uuidA = objectMapper.readTree(uploadA.getResponse().getContentAsString()).get("uuid").asText();

        // User B sees empty list and receives 404 on metadata, download, delete for User A's resume
        mockMvc.perform(get("/api/v1/resumes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/resumes/" + uuidA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/resumes/" + uuidA + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/resumes/" + uuidA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Referenced by TailoredResume -> delete by owner returns 409 and preserves DB + disk file
        Resume masterResume = resumeRepository.findByUuid(uuidA).orElseThrow();
        User alice = userRepository.findByEmailIgnoreCase("alice.cv@eurojob.eu").orElseThrow();
        Company company = companyRepository.saveAndFlush(TestEntityFactory.createCompany("Adyen", "Netherlands", true));
        Job job = jobRepository.saveAndFlush(TestEntityFactory.createJob(company, "Java Engineer", "Netherlands", "adyen-1"));
        tailoredResumeRepository.saveAndFlush(TailoredResume.builder()
                .masterResume(masterResume)
                .job(job)
                .user(alice)
                .templateName(ResumeTemplate.MODERN)
                .resumeDataJson(Map.of("summary", "Tailored"))
                .matchScore(new BigDecimal("90.00"))
                .atsKeywordCoverage(new BigDecimal("85.00"))
                .build());

        mockMvc.perform(delete("/api/v1/resumes/" + uuidA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(resumeRepository.findByUuid(uuidA)).isPresent();
        assertThat(Files.exists(tempStorageDir.resolve(masterResume.getFilePath()))).isTrue();
    }

    @Test
    void openApiSpecification_exposesResumeEndpointsCleanly() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/resumes/upload']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/resumes']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/resumes/{uuid}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/resumes/{uuid}/download']").exists());
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

    private byte[] createValidDocxBytes() throws IOException {
        return createPlainZipBytes(
                "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
                "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships/>",
                "word/document.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document><w:body><w:p><w:r><w:t>Master CV</w:t></w:r></w:p></w:body></w:document>"
        );
    }

    private byte[] createPlainZipBytes(String... entriesAndContents) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (int i = 0; i < entriesAndContents.length; i += 2) {
                zos.putNextEntry(new ZipEntry(entriesAndContents[i]));
                zos.write(entriesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
