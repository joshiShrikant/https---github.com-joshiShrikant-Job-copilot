package com.eurojobcopilot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationIntegrationTest {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String MIME_TEXT = "text/plain";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void openApiSpecification_exposesCompleteAndAccurateApiContract() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

        // Top-level metadata, servers, tags, and security scheme
        assertThat(root.path("openapi").asText()).startsWith("3.");
        assertThat(root.path("info").path("title").asText()).isEqualTo("EuroJob Copilot REST API");
        assertThat(root.path("info").path("version").asText()).isEqualTo("1.0.0");
        assertThat(root.path("info").path("contact").path("email").asText()).isEqualTo("dev@eurojobcopilot.com");

        List<String> serverUrls = new ArrayList<>();
        for (JsonNode server : root.path("servers")) {
            serverUrls.add(server.path("url").asText());
        }
        assertThat(serverUrls).containsExactly("http://localhost:8080", "https://api.eurojobcopilot.com");

        List<String> tagNames = new ArrayList<>();
        for (JsonNode tag : root.path("tags")) {
            tagNames.add(tag.path("name").asText());
            assertThat(tag.path("description").asText()).isNotBlank();
        }
        assertThat(tagNames).containsExactlyInAnyOrder(
                "Health Check",
                "Authentication",
                "User Profile",
                "Preferences",
                "Resumes",
                "Tailored Documents",
                "Applications",
                "Recruiter CRM",
                "Dashboard & Analytics",
                "Jobs",
                "AI Application Assistant",
                "Autopilot & Recommendations",
                "Notifications",
                "Interview Preparation",
                "Job Matching",
                "AI Intelligence",
                "Sponsorship Intelligence",
                "Companies",
                "Job Ingestion Admin",
                "Sponsorship Registry Admin");

        JsonNode bearerScheme = root.path("components").path("securitySchemes").path("BearerAuth");
        assertThat(bearerScheme.path("type").asText()).isEqualTo("http");
        assertThat(bearerScheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearerScheme.path("bearerFormat").asText()).isEqualTo("JWT");

        // All documented API paths and HTTP operations present, without non-API paths leaking
        JsonNode paths = root.path("paths");
        Map<String, List<String>> expectedOperations = Map.ofEntries(
                Map.entry("/api/v1/health", List.of("get")),
                Map.entry("/api/v1/auth/register", List.of("post")),
                Map.entry("/api/v1/auth/login", List.of("post")),
                Map.entry("/api/v1/auth/refresh", List.of("post")),
                Map.entry("/api/v1/auth/logout", List.of("post")),
                Map.entry("/api/v1/auth/me", List.of("get")),
                Map.entry("/api/v1/profile", List.of("get", "post", "put", "delete")),
                Map.entry("/api/v1/profile/skills", List.of("get", "post")),
                Map.entry("/api/v1/profile/skills/{id}", List.of("get", "put", "delete")),
                Map.entry("/api/v1/profile/education", List.of("get", "post")),
                Map.entry("/api/v1/profile/education/{id}", List.of("get", "put", "delete")),
                Map.entry("/api/v1/profile/experience", List.of("get", "post")),
                Map.entry("/api/v1/profile/experience/{id}", List.of("get", "put", "delete")),
                Map.entry("/api/v1/preferences", List.of("get", "put")),
                Map.entry("/api/v1/resumes/upload", List.of("post")),
                Map.entry("/api/v1/resumes", List.of("get")),
                Map.entry("/api/v1/resumes/{uuid}", List.of("get", "delete")),
                Map.entry("/api/v1/resumes/{uuid}/download", List.of("get")),
                Map.entry("/api/v1/resumes/{uuid}/parse", List.of("post")),
                Map.entry("/api/v1/resumes/{uuid}/parsed", List.of("get")),
                Map.entry("/api/v1/resumes/master/approve", List.of("post")),
                Map.entry("/api/v1/tailored-documents/resume/generate", List.of("post")),
                Map.entry("/api/v1/tailored-documents/resumes", List.of("get")),
                Map.entry("/api/v1/tailored-documents/resume/{uuid}", List.of("get", "put", "delete")),
                Map.entry("/api/v1/tailored-documents/resume/{uuid}/export", List.of("get")),
                Map.entry("/api/v1/tailored-documents/cover-letter/generate", List.of("post")),
                Map.entry("/api/v1/tailored-documents/cover-letters", List.of("get")),
                Map.entry("/api/v1/tailored-documents/cover-letter/{uuid}", List.of("get", "put", "delete")),
                Map.entry("/api/v1/tailored-documents/cover-letter/{uuid}/export", List.of("get")),
                Map.entry("/api/v1/applications", List.of("get", "post")),
                Map.entry("/api/v1/applications/{uuid}", List.of("get", "put", "delete")),
                Map.entry("/api/v1/applications/{uuid}/status", List.of("patch")),
                Map.entry("/api/v1/recruiters", List.of("get", "post")),
                Map.entry("/api/v1/recruiters/{id}", List.of("get", "put", "delete")),
                Map.entry("/api/v1/recruiters/reminders", List.of("get")),
                Map.entry("/api/v1/recruiters/{id}/outreach/generate", List.of("post")),
                Map.entry("/api/v1/recruiters/{id}/interactions", List.of("post")),
                Map.entry("/api/v1/recruiters/{recruiterId}/interactions/{interactionId}", List.of("put", "delete")),
                Map.entry("/api/v1/dashboard/summary", List.of("get")),
                Map.entry("/api/v1/analytics/market-insights", List.of("get")),
                Map.entry("/api/v1/jobs", List.of("get")),
                Map.entry("/api/v1/jobs/{id}", List.of("get")),
                Map.entry("/api/v1/jobs/{id}/save", List.of("post", "delete")),
                Map.entry("/api/v1/jobs/{id}/interested", List.of("post", "delete")),
                Map.entry("/api/v1/jobs/{id}/hide", List.of("post", "delete")),
                Map.entry("/api/v1/jobs/{id}/apply-redirect", List.of("post")),
                Map.entry("/api/v1/jobs/{id}/intelligence/match-score", List.of("get")),
                Map.entry("/api/v1/jobs/{id}/intelligence/ai-match", List.of("post")),
                Map.entry("/api/v1/jobs/{id}/intelligence/analyze-jd", List.of("post", "get")),
                Map.entry("/api/v1/jobs/{id}/intelligence/analyze-jd/history", List.of("get")),
                Map.entry("/api/v1/jobs/{jobId}/assistant/history", List.of("get")),
                Map.entry("/api/v1/jobs/{jobId}/assistant/respond", List.of("post")),
                Map.entry("/api/v1/jobs/{jobId}/assistant/{messageId}", List.of("put", "delete")),
                Map.entry("/api/v1/autopilot/status", List.of("get")),
                Map.entry("/api/v1/autopilot/run", List.of("post")),
                Map.entry("/api/v1/autopilot/pause", List.of("post")),
                Map.entry("/api/v1/autopilot/resume", List.of("post")),
                Map.entry("/api/v1/autopilot/recommended-jobs", List.of("get")),
                Map.entry("/api/v1/autopilot/history", List.of("get")),
                Map.entry("/api/v1/notifications", List.of("get")),
                Map.entry("/api/v1/notifications/unread-count", List.of("get")),
                Map.entry("/api/v1/notifications/{id}/read", List.of("patch")),
                Map.entry("/api/v1/notifications/mark-all-read", List.of("post")),
                Map.entry("/api/v1/notifications/{id}", List.of("delete")),
                Map.entry("/api/v1/interview-prep/sessions", List.of("post", "get")),
                Map.entry("/api/v1/interview-prep/sessions/{id}", List.of("get", "delete")),
                Map.entry("/api/v1/interview-prep/sessions/{id}/answers", List.of("post")),
                Map.entry("/api/v1/interview-prep/history", List.of("get")),
                Map.entry("/api/v1/matching/recalculate", List.of("post")),
                Map.entry("/api/v1/sponsorship/countries", List.of("get")),
                Map.entry("/api/v1/sponsorship/verify", List.of("get")),
                Map.entry("/api/v1/sponsorship/history", List.of("get")),
                Map.entry("/api/v1/sponsorship/registry/ind", List.of("get")),
                Map.entry("/api/v1/companies", List.of("get")),
                Map.entry("/api/v1/companies/{id}", List.of("get")),
                Map.entry("/api/v1/companies/{id}/jobs", List.of("get")),
                Map.entry("/api/v1/companies/{id}/save", List.of("post", "delete")),
                Map.entry("/api/v1/admin/sponsorship/ind/sync", List.of("post")),
                Map.entry("/api/v1/admin/sponsorship/ind/status", List.of("get")),
                Map.entry("/api/v1/admin/ingestion/trigger", List.of("post")),
                Map.entry("/api/v1/admin/ingestion/status", List.of("get")),
                Map.entry("/api/v1/admin/ingestion/logs", List.of("get")),
                Map.entry("/api/v1/admin/ingestion/cleanup-stale", List.of("post")));

        assertThat(paths.size()).isEqualTo(expectedOperations.size());
        for (Map.Entry<String, List<String>> entry : expectedOperations.entrySet()) {
            JsonNode pathItem = paths.path(entry.getKey());
            assertThat(pathItem.isMissingNode()).as("Missing OpenAPI path %s", entry.getKey()).isFalse();
            for (String method : entry.getValue()) {
                JsonNode op = pathItem.path(method);
                assertThat(op.isMissingNode()).as("Missing operation %s %s", method, entry.getKey()).isFalse();
                assertThat(op.path("operationId").asText()).isNotBlank();
                assertThat(op.path("summary").asText()).isNotBlank();
            }
        }

        // Public endpoints declare empty security: [] overriding global BearerAuth
        assertPublicSecurity(paths.path("/api/v1/health").path("get"));
        assertPublicSecurity(paths.path("/api/v1/auth/register").path("post"));
        assertPublicSecurity(paths.path("/api/v1/auth/login").path("post"));
        assertPublicSecurity(paths.path("/api/v1/auth/refresh").path("post"));

        // Protected endpoints declare BearerAuth requirement
        assertProtectedSecurity(paths.path("/api/v1/auth/logout").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/auth/me").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/profile").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/preferences").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/preferences").path("put"));
        assertProtectedSecurity(paths.path("/api/v1/resumes/upload").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/resumes/{uuid}/download").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/resumes/{uuid}/parse").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/resumes/{uuid}/parsed").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/resumes/master/approve").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/tailored-documents/resume/generate").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/tailored-documents/resumes").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/tailored-documents/resume/{uuid}").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/tailored-documents/resume/{uuid}").path("put"));
        assertProtectedSecurity(paths.path("/api/v1/tailored-documents/resume/{uuid}").path("delete"));
        assertProtectedSecurity(paths.path("/api/v1/tailored-documents/resume/{uuid}/export").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/tailored-documents/cover-letter/generate").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/tailored-documents/cover-letters").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}").path("put"));
        assertProtectedSecurity(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}").path("delete"));
        assertProtectedSecurity(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}/export").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/applications").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/applications").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/applications/{uuid}").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/applications/{uuid}").path("put"));
        assertProtectedSecurity(paths.path("/api/v1/applications/{uuid}").path("delete"));
        assertProtectedSecurity(paths.path("/api/v1/applications/{uuid}/status").path("patch"));
        assertProtectedSecurity(paths.path("/api/v1/recruiters").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/recruiters").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/recruiters/{id}").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/recruiters/{id}").path("put"));
        assertProtectedSecurity(paths.path("/api/v1/recruiters/{id}").path("delete"));
        assertProtectedSecurity(paths.path("/api/v1/recruiters/reminders").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/recruiters/{id}/outreach/generate").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/recruiters/{id}/interactions").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/recruiters/{recruiterId}/interactions/{interactionId}").path("put"));
        assertProtectedSecurity(paths.path("/api/v1/recruiters/{recruiterId}/interactions/{interactionId}").path("delete"));
        assertProtectedSecurity(paths.path("/api/v1/dashboard/summary").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/analytics/market-insights").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{jobId}/assistant/history").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{jobId}/assistant/respond").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{jobId}/assistant/{messageId}").path("put"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{jobId}/assistant/{messageId}").path("delete"));
        assertProtectedSecurity(paths.path("/api/v1/autopilot/status").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/autopilot/run").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/autopilot/pause").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/autopilot/resume").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/autopilot/recommended-jobs").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/autopilot/history").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/notifications").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/notifications/unread-count").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/notifications/{id}/read").path("patch"));
        assertProtectedSecurity(paths.path("/api/v1/notifications/mark-all-read").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/notifications/{id}").path("delete"));
        assertProtectedSecurity(paths.path("/api/v1/interview-prep/sessions").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/interview-prep/sessions").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/interview-prep/sessions/{id}").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/interview-prep/sessions/{id}").path("delete"));
        assertProtectedSecurity(paths.path("/api/v1/interview-prep/sessions/{id}/answers").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/interview-prep/history").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/jobs").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}/save").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}/save").path("delete"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}/interested").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}/interested").path("delete"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}/hide").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}/hide").path("delete"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}/apply-redirect").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}/intelligence/match-score").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}/intelligence/ai-match").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd/history").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/matching/recalculate").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/sponsorship/countries").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/sponsorship/verify").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/sponsorship/history").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/sponsorship/registry/ind").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/companies").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/companies/{id}").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/companies/{id}/jobs").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/companies/{id}/save").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/companies/{id}/save").path("delete"));
        assertProtectedSecurity(paths.path("/api/v1/admin/sponsorship/ind/sync").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/admin/sponsorship/ind/status").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/admin/ingestion/trigger").path("post"));
        assertProtectedSecurity(paths.path("/api/v1/admin/ingestion/status").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/admin/ingestion/logs").path("get"));
        assertProtectedSecurity(paths.path("/api/v1/admin/ingestion/cleanup-stale").path("post"));

        // Multipart resume upload documents both 'file' (binary) and 'title' (string)
        // in requestBody only
        JsonNode uploadOp = paths.path("/api/v1/resumes/upload").path("post");
        if (!uploadOp.path("parameters").isMissingNode() && uploadOp.path("parameters").isArray()) {
            for (JsonNode param : uploadOp.path("parameters")) {
                assertThat(param.path("name").asText()).isNotEqualTo("title");
            }
        }
        JsonNode multipartSchema = uploadOp.path("requestBody")
                .path("content")
                .path("multipart/form-data")
                .path("schema");
        assertThat(multipartSchema.path("properties").path("file").path("type").asText()).isEqualTo("string");
        assertThat(multipartSchema.path("properties").path("file").path("format").asText()).isEqualTo("binary");
        assertThat(multipartSchema.path("properties").path("title").path("type").asText()).isEqualTo("string");
        List<String> uploadRequired = new ArrayList<>();
        for (JsonNode req : multipartSchema.path("required")) {
            uploadRequired.add(req.asText());
        }
        assertThat(uploadRequired).containsExactlyInAnyOrder("file", "title");

        // Binary resume download & tailored export declare PDF & DOCX stream schemas and response headers
        JsonNode download200 = paths.path("/api/v1/resumes/{uuid}/download").path("get")
                .path("responses").path("200");
        assertThat(download200.path("content").path(MIME_PDF).path("schema").path("format").asText())
                .isEqualTo("binary");
        assertThat(download200.path("content").path(MIME_DOCX).path("schema").path("format").asText())
                .isEqualTo("binary");
        assertThat(download200.path("headers").has("Content-Disposition")).isTrue();
        assertThat(download200.path("headers").has("X-Content-Type-Options")).isTrue();

        JsonNode export200 = paths.path("/api/v1/tailored-documents/resume/{uuid}/export").path("get")
                .path("responses").path("200");
        assertThat(export200.path("content").path(MIME_PDF).path("schema").path("format").asText())
                .isEqualTo("binary");
        assertThat(export200.path("content").path(MIME_DOCX).path("schema").path("format").asText())
                .isEqualTo("binary");
        assertThat(export200.path("headers").has("Content-Disposition")).isTrue();
        assertThat(export200.path("headers").has("X-Content-Type-Options")).isTrue();

        JsonNode coverLetterExport200 = paths.path("/api/v1/tailored-documents/cover-letter/{uuid}/export").path("get")
                .path("responses").path("200");
        assertThat(coverLetterExport200.path("content").path(MIME_PDF).path("schema").path("format").asText())
                .isEqualTo("binary");
        assertThat(coverLetterExport200.path("content").path(MIME_DOCX).path("schema").path("format").asText())
                .isEqualTo("binary");
        assertThat(coverLetterExport200.path("content").path(MIME_TEXT).path("schema").path("format").asText())
                .isEqualTo("binary");
        assertThat(coverLetterExport200.path("headers").has("Content-Disposition")).isTrue();
        assertThat(coverLetterExport200.path("headers").has("X-Content-Type-Options")).isTrue();

        // Standard ApiErrorResponse references documented on error responses across
        // modules
        assertErrorRef(paths.path("/api/v1/auth/register").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/auth/register").path("post"), "409");
        assertErrorRef(paths.path("/api/v1/auth/login").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/profile").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/resumes/upload").path("post"), "413");
        assertErrorRef(paths.path("/api/v1/resumes/{uuid}").path("delete"), "409");
        assertErrorRef(paths.path("/api/v1/resumes/{uuid}/parse").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/resumes/{uuid}/parse").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/resumes/{uuid}/parse").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/resumes/{uuid}/parse").path("post"), "409");
        assertErrorRef(paths.path("/api/v1/resumes/{uuid}/parse").path("post"), "502");
        assertErrorRef(paths.path("/api/v1/resumes/{uuid}/parse").path("post"), "503");
        assertErrorRef(paths.path("/api/v1/resumes/{uuid}/parsed").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/resumes/{uuid}/parsed").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/resumes/{uuid}/parsed").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/resumes/{uuid}/parsed").path("get"), "409");
        assertErrorRef(paths.path("/api/v1/resumes/master/approve").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/resumes/master/approve").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/resumes/master/approve").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/resumes/master/approve").path("post"), "409");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/generate").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/generate").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/generate").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/generate").path("post"), "409");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/generate").path("post"), "502");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/generate").path("post"), "503");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resumes").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resumes").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/{uuid}").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/{uuid}").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/{uuid}").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/{uuid}").path("put"), "400");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/{uuid}").path("put"), "401");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/{uuid}").path("put"), "404");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/{uuid}").path("delete"), "400");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/{uuid}").path("delete"), "401");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/{uuid}").path("delete"), "404");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/{uuid}/export").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/{uuid}/export").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/tailored-documents/resume/{uuid}/export").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/generate").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/generate").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/generate").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/generate").path("post"), "502");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/generate").path("post"), "503");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letters").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letters").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}").path("put"), "400");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}").path("put"), "401");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}").path("put"), "404");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}").path("delete"), "400");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}").path("delete"), "401");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}").path("delete"), "404");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}/export").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}/export").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/tailored-documents/cover-letter/{uuid}/export").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/applications").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/applications").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/applications").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/applications").path("post"), "409");
        assertErrorRef(paths.path("/api/v1/applications/{uuid}/status").path("patch"), "404");
        assertErrorRef(paths.path("/api/v1/recruiters/{id}/outreach/generate").path("post"), "502");
        assertErrorRef(paths.path("/api/v1/recruiters/{id}/outreach/generate").path("post"), "503");
        assertErrorRef(paths.path("/api/v1/recruiters/{recruiterId}/interactions/{interactionId}").path("put"), "400");
        assertErrorRef(paths.path("/api/v1/jobs").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/jobs").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/jobs/{id}").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/jobs/{id}").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/jobs/{id}").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/save").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/save").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/save").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/save").path("post"), "409");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/save").path("delete"), "404");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/interested").path("post"), "409");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/hide").path("post"), "409");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/hide").path("delete"), "404");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/apply-redirect").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/apply-redirect").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/apply-redirect").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/apply-redirect").path("post"), "409");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/match-score").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/match-score").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/match-score").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/ai-match").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/ai-match").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/ai-match").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/ai-match").path("post"), "409");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/ai-match").path("post"), "502");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/ai-match").path("post"), "503");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd").path("post"), "502");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd").path("post"), "503");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd/history").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd/history").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/jobs/{id}/intelligence/analyze-jd/history").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/matching/recalculate").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/sponsorship/countries").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/sponsorship/verify").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/sponsorship/verify").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/sponsorship/verify").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/sponsorship/history").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/sponsorship/history").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/sponsorship/registry/ind").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/sponsorship/registry/ind").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/companies").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/companies").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/companies/{id}").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/companies/{id}").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/companies/{id}").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/companies/{id}/jobs").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/companies/{id}/jobs").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/companies/{id}/jobs").path("get"), "404");
        assertErrorRef(paths.path("/api/v1/companies/{id}/save").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/companies/{id}/save").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/companies/{id}/save").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/companies/{id}/save").path("delete"), "400");
        assertErrorRef(paths.path("/api/v1/companies/{id}/save").path("delete"), "401");
        assertErrorRef(paths.path("/api/v1/companies/{id}/save").path("delete"), "404");
        assertErrorRef(paths.path("/api/v1/admin/sponsorship/ind/sync").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/admin/sponsorship/ind/sync").path("post"), "403");
        assertErrorRef(paths.path("/api/v1/admin/sponsorship/ind/sync").path("post"), "409");
        assertErrorRef(paths.path("/api/v1/admin/sponsorship/ind/status").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/admin/sponsorship/ind/status").path("get"), "403");
        assertErrorRef(paths.path("/api/v1/admin/ingestion/trigger").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/admin/ingestion/trigger").path("post"), "401");
        assertErrorRef(paths.path("/api/v1/admin/ingestion/trigger").path("post"), "403");
        assertErrorRef(paths.path("/api/v1/admin/ingestion/trigger").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/admin/ingestion/trigger").path("post"), "409");
        assertErrorRef(paths.path("/api/v1/analytics/market-insights").path("get"), "400");
        assertErrorRef(paths.path("/api/v1/analytics/market-insights").path("get"), "401");
        assertErrorRef(paths.path("/api/v1/jobs/{jobId}/assistant/respond").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/jobs/{jobId}/assistant/respond").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/jobs/{jobId}/assistant/respond").path("post"), "502");
        assertErrorRef(paths.path("/api/v1/jobs/{jobId}/assistant/respond").path("post"), "503");
        assertErrorRef(paths.path("/api/v1/notifications/{id}/read").path("patch"), "404");
        assertErrorRef(paths.path("/api/v1/interview-prep/sessions").path("post"), "400");
        assertErrorRef(paths.path("/api/v1/interview-prep/sessions").path("post"), "404");
        assertErrorRef(paths.path("/api/v1/interview-prep/sessions").path("post"), "502");
        assertErrorRef(paths.path("/api/v1/interview-prep/sessions").path("post"), "503");
        assertErrorRef(paths.path("/api/v1/interview-prep/sessions/{id}/answers").path("post"), "404");

        // Component schemas cover every active contract DTO + standardized
        // error models
        JsonNode schemas = root.path("components").path("schemas");
        for (String schemaName : List.of(
                "HealthResponse",
                "RegisterRequest",
                "LoginRequest",
                "RefreshTokenRequest",
                "LogoutRequest",
                "AuthResponse",
                "TokenRefreshResponse",
                "UserSummaryDto",
                "UpsertProfileRequest",
                "UserProfileResponse",
                "UpsertSkillRequest",
                "UserSkillDto",
                "UpsertEducationRequest",
                "EducationDto",
                "UpsertWorkExperienceRequest",
                "WorkExperienceDto",
                "JobPreferencesResponse",
                "UpdateJobPreferencesRequest",
                "ResumeResponse",
                "ResumeParseResponse",
                "ParsedResumeJsonDto",
                "ParsedResumeProfileDto",
                "ParsedResumeSkillDto",
                "ParsedResumeExperienceDto",
                "ParsedResumeEducationDto",
                "ParsedResumeProjectDto",
                "ApproveMasterResumeRequest",
                "MasterResumeSyncSummaryDto",
                "GenerateTailoredResumeRequest",
                "UpdateTailoredResumeRequest",
                "TailoredResumeSummaryDto",
                "TailoredResumeResponse",
                "TailoredResumeDataDto",
                "TailoredResumeHeaderDto",
                "TailoredResumeExperienceDto",
                "TailoredResumeProjectDto",
                "TailoredResumeEducationDto",
                "GenerateCoverLetterRequest",
                "UpdateCoverLetterRequest",
                "CoverLetterMetadataDto",
                "CoverLetterSummaryDto",
                "CoverLetterResponse",
                "CreateApplicationRequest",
                "UpdateApplicationRequest",
                "UpdateApplicationStatusRequest",
                "ApplicationStatusHistoryDto",
                "ApplicationDocumentSummaryDto",
                "JobApplicationDetailResponse",
                "CreateRecruiterRequest",
                "UpdateRecruiterRequest",
                "RecruiterInteractionDto",
                "RecruiterResponse",
                "PagedRecruitersResponse",
                "GenerateOutreachRequest",
                "OutreachDraftResponse",
                "CreateRecruiterInteractionRequest",
                "UpdateRecruiterInteractionRequest",
                "FollowUpReminderItemDto",
                "FollowUpRemindersResponse",
                "DashboardSummaryResponse",
                "DashboardReadinessDto",
                "PipelineStageCountDto",
                "DashboardActivityItemDto",
                "MarketTotalsDto",
                "CountryMarketInsightDto",
                "DiscoveryTrendPointDto",
                "SourceCoverageDto",
                "MarketInsightsResponse",
                "JobAssistantRespondRequest",
                "UpdateJobAssistantMessageRequest",
                "JobAssistantMessageResponse",
                "RunAutopilotRequest",
                "AutopilotRunLogDto",
                "AutopilotStatusResponse",
                "RecommendedJobItemDto",
                "PagedRecommendedJobsResponse",
                "PagedAutopilotHistoryResponse",
                "NotificationDto",
                "PagedNotificationsResponse",
                "UnreadCountResponse",
                "MarkAllReadResponse",
                "CompanyPrepTopicDto",
                "InterviewQuestionDto",
                "InterviewPrepSessionSummaryDto",
                "InterviewPrepSessionDetailResponse",
                "CreateInterviewPrepSessionRequest",
                "SubmitInterviewAnswerRequest",
                "CategoryPerformanceStatDto",
                "DifficultyPerformanceStatDto",
                "InterviewSessionScorePointDto",
                "InterviewPerformanceHistoryResponse",
                "PagedJobsResponse",
                "JobSummaryDto",
                "JobDetailDto",
                "CompanySponsorshipSummaryDto",
                "SponsorshipBreakdownDto",
                "SponsorshipRegistryRecordDto",
                "JobMatchBreakdownDto",
                "EvaluateAiMatchRequest",
                "BatchMatchRecalculationResponse",
                "MatchingWeightsDto",
                "JobMatchSummaryItemDto",
                "AnalyzeJobDescriptionRequest",
                "JobAiAnalysisDto",
                "JobApplicationSummaryDto",
                "CountrySponsorshipProfileDto",
                "SponsorshipVerificationResponseDto",
                "SponsorshipVerificationHistoryItemDto",
                "PagedSponsorshipHistoryResponse",
                "OfficialSponsorRegistryEntryDto",
                "RegistrySyncStatusDto",
                "PagedIndRegistryResponse",
                "IndSyncResultDto",
                "PagedCompaniesResponse",
                "CompanySummaryDto",
                "CompanyDetailDto",
                "SaveCompanyRequest",
                "CompanySaveResponse",
                "JobInteractionResponse",
                "ApplyRedirectRequest",
                "ApplyRedirectResponse",
                "TriggerIngestionRequest",
                "AdminIngestionTriggerResponse",
                "ProviderIngestionResult",
                "IngestionStatusResponse",
                "ProviderHealthStatusDto",
                "PagedFetchLogsResponse",
                "JobFetchLogDto",
                "StaleJobCleanupResponse",
                "ApiErrorResponse",
                "ValidationErrorItem")) {
            assertThat(schemas.has(schemaName)).as("Missing schema %s", schemaName).isTrue();
        }

        // ResumeResponse schema uses explicit JSON property name isMasterApproved
        JsonNode resumeProps = schemas.path("ResumeResponse").path("properties");
        List<String> resumeFieldNames = new ArrayList<>();
        Iterator<String> fieldIt = resumeProps.fieldNames();
        while (fieldIt.hasNext()) {
            resumeFieldNames.add(fieldIt.next());
        }
        assertThat(resumeFieldNames).contains("isMasterApproved", "parseStatus", "hasParsedContent", "profileSyncSummary");

        JsonNode parseProps = schemas.path("ResumeParseResponse").path("properties");
        assertThat(parseProps.has("isMasterApproved")).isTrue();
        assertThat(parseProps.has("parseStatus")).isTrue();
        assertThat(parseProps.has("parsedData")).isTrue();
        assertThat(parseProps.has("warnings")).isTrue();

        JsonNode pagedJobsProps = schemas.path("PagedJobsResponse").path("properties");
        assertThat(pagedJobsProps.has("isFirst")).isTrue();
        assertThat(pagedJobsProps.has("isLast")).isTrue();
        assertThat(pagedJobsProps.has("hasNext")).isTrue();
        assertThat(pagedJobsProps.has("hasPrevious")).isTrue();

        JsonNode jobSummaryProps = schemas.path("JobSummaryDto").path("properties");
        assertThat(jobSummaryProps.has("isSaved")).isTrue();
        assertThat(jobSummaryProps.has("isInterested")).isTrue();
        assertThat(jobSummaryProps.has("isHidden")).isTrue();
        assertThat(jobSummaryProps.has("matchPercentage")).isTrue();

        JsonNode jobDetailProps = schemas.path("JobDetailDto").path("properties");
        assertThat(jobDetailProps.has("isActive")).isTrue();
        assertThat(jobDetailProps.has("isSaved")).isTrue();
        assertThat(jobDetailProps.has("isInterested")).isTrue();
        assertThat(jobDetailProps.has("isHidden")).isTrue();
        assertThat(jobDetailProps.has("hasApplied")).isTrue();
        assertThat(jobDetailProps.has("sponsorshipBreakdown")).isTrue();
        assertThat(jobDetailProps.has("matchBreakdown")).isTrue();
        assertThat(schemas.path("SponsorshipBreakdownDto").path("properties").has("isVerifiedSponsor")).isTrue();

        JsonNode interactionProps = schemas.path("JobInteractionResponse").path("properties");
        assertThat(interactionProps.has("isSaved")).isTrue();
        assertThat(interactionProps.has("isInterested")).isTrue();
        assertThat(interactionProps.has("isHidden")).isTrue();
        assertThat(interactionProps.has("hasApplied")).isTrue();
        assertThat(interactionProps.has("saved")).isFalse();
        assertThat(interactionProps.has("interested")).isFalse();
        assertThat(interactionProps.has("hidden")).isFalse();

        JsonNode applyRedirectProps = schemas.path("ApplyRedirectResponse").path("properties");
        assertThat(applyRedirectProps.has("targetUrl")).isTrue();
        assertThat(applyRedirectProps.has("redirectClickedAt")).isTrue();
        assertThat(applyRedirectProps.has("hasApplied")).isTrue();
        assertThat(applyRedirectProps.has("interaction")).isTrue();

        JsonNode matchBreakdownProps = schemas.path("JobMatchBreakdownDto").path("properties");
        assertThat(matchBreakdownProps.has("matchPercentage")).isTrue();
        assertThat(matchBreakdownProps.has("matchingSkills")).isTrue();
        assertThat(matchBreakdownProps.has("missingSkills")).isTrue();
        assertThat(matchBreakdownProps.has("experienceCompatibility")).isTrue();
        assertThat(matchBreakdownProps.has("recommendationReasons")).isTrue();
        assertThat(matchBreakdownProps.has("aiStrongMatches")).isTrue();
        assertThat(matchBreakdownProps.has("aiMissingSkills")).isTrue();
        assertThat(matchBreakdownProps.has("aiExperienceGaps")).isTrue();
        assertThat(matchBreakdownProps.has("aiPotentialConcerns")).isTrue();
        assertThat(matchBreakdownProps.has("aiImprovementSuggestions")).isTrue();
        assertThat(matchBreakdownProps.has("aiProvider")).isTrue();
        assertThat(matchBreakdownProps.has("aiModelUsed")).isTrue();
        assertThat(matchBreakdownProps.has("aiEvaluatedAt")).isTrue();

        JsonNode aiAnalysisProps = schemas.path("JobAiAnalysisDto").path("properties");
        assertThat(aiAnalysisProps.has("analysisVersion")).isTrue();
        assertThat(aiAnalysisProps.has("extractedTitle")).isTrue();
        assertThat(aiAnalysisProps.has("extractedCompany")).isTrue();
        assertThat(aiAnalysisProps.has("extractedCountry")).isTrue();
        assertThat(aiAnalysisProps.has("extractedLocation")).isTrue();
        assertThat(aiAnalysisProps.has("requiredExperienceYears")).isTrue();
        assertThat(aiAnalysisProps.has("salaryMin")).isTrue();
        assertThat(aiAnalysisProps.has("workArrangement")).isTrue();
        assertThat(aiAnalysisProps.has("employmentType")).isTrue();
        assertThat(aiAnalysisProps.has("sponsorshipOffered")).isTrue();
        assertThat(aiAnalysisProps.has("relocationAssistanceOffered")).isTrue();

        JsonNode pagedCompaniesProps = schemas.path("PagedCompaniesResponse").path("properties");
        assertThat(pagedCompaniesProps.has("isFirst")).isTrue();
        assertThat(pagedCompaniesProps.has("isLast")).isTrue();
        assertThat(pagedCompaniesProps.has("hasNext")).isTrue();
        assertThat(pagedCompaniesProps.has("hasPrevious")).isTrue();
        assertThat(pagedCompaniesProps.has("verifiedSponsorsTotal")).isTrue();
        assertThat(pagedCompaniesProps.has("savedCompaniesTotal")).isTrue();

        JsonNode companySummaryProps = schemas.path("CompanySummaryDto").path("properties");
        assertThat(companySummaryProps.has("isVerifiedSponsor")).isTrue();
        assertThat(companySummaryProps.has("isSaved")).isTrue();
        assertThat(companySummaryProps.has("activeJobsCount")).isTrue();
        assertThat(companySummaryProps.has("techStack")).isTrue();

        JsonNode companyDetailProps = schemas.path("CompanyDetailDto").path("properties");
        assertThat(companyDetailProps.has("isVerifiedSponsor")).isTrue();
        assertThat(companyDetailProps.has("isSaved")).isTrue();
        assertThat(companyDetailProps.has("vacancyCaveat")).isTrue();
        assertThat(companyDetailProps.has("registryRecords")).isTrue();

        JsonNode companySaveProps = schemas.path("CompanySaveResponse").path("properties");
        assertThat(companySaveProps.has("isSaved")).isTrue();
        assertThat(companySaveProps.has("priorityTier")).isTrue();

        JsonNode tailoredResponseProps = schemas.path("TailoredResumeResponse").path("properties");
        assertThat(tailoredResponseProps.has("uuid")).isTrue();
        assertThat(tailoredResponseProps.has("versionNumber")).isTrue();
        assertThat(tailoredResponseProps.has("atsKeywordCoverage")).isTrue();
        assertThat(tailoredResponseProps.has("highlightedSkills")).isTrue();
        assertThat(tailoredResponseProps.has("missingKeywords")).isTrue();
        assertThat(tailoredResponseProps.has("optimizedExperiences")).isTrue();
        assertThat(tailoredResponseProps.has("editedByUser")).isTrue();
        assertThat(tailoredResponseProps.has("hasPdfExport")).isTrue();
        assertThat(tailoredResponseProps.has("hasDocxExport")).isTrue();
        assertThat(tailoredResponseProps.has("resumeData")).isTrue();

        JsonNode coverLetterResponseProps = schemas.path("CoverLetterResponse").path("properties");
        assertThat(coverLetterResponseProps.has("uuid")).isTrue();
        assertThat(coverLetterResponseProps.has("jobId")).isTrue();
        assertThat(coverLetterResponseProps.has("title")).isTrue();
        assertThat(coverLetterResponseProps.has("content")).isTrue();
        assertThat(coverLetterResponseProps.has("tone")).isTrue();
        assertThat(coverLetterResponseProps.has("versionNumber")).isTrue();
        assertThat(coverLetterResponseProps.has("wordCount")).isTrue();
        assertThat(coverLetterResponseProps.has("metadata")).isTrue();
        assertThat(coverLetterResponseProps.has("editedByUser")).isTrue();
    }

    @Test
    void swaggerUiEndpoints_arePubliclyAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/swagger-ui/index.html")));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/html")));

        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));

        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk());
    }

    private void assertPublicSecurity(JsonNode operation) {
        JsonNode security = operation.path("security");
        assertThat(security.isArray()).isTrue();
        assertThat(security).isEmpty();
    }

    private void assertProtectedSecurity(JsonNode operation) {
        JsonNode security = operation.path("security");
        assertThat(security.isArray()).isTrue();
        assertThat(security).isNotEmpty();
        assertThat(security.get(0).has("BearerAuth")).isTrue();
    }

    private void assertErrorRef(JsonNode operation, String statusCode) {
        JsonNode schema = operation.path("responses")
                .path(statusCode)
                .path("content")
                .path("application/json")
                .path("schema");
        assertThat(schema.path("$ref").asText()).isEqualTo("#/components/schemas/ApiErrorResponse");
    }
}
