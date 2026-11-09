package com.eurojobcopilot.core.exception;

import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerIntegrationTest.TestErrorProbeController.class)
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    @RequestMapping("/api/v1/test-errors")
    static class TestErrorProbeController {

        @PostMapping("/sensitive-validation")
        public Map<String, String> validateSensitive(@Valid @RequestBody SensitiveValidationPayload payload) {
            return Map.of("status", "ok");
        }

        @GetMapping("/method-param-validation")
        public Map<String, Object> validateMethodParam(@RequestParam("page") @Min(value = 1, message = "Page must be >= 1") int page) {
            return Map.of("page", page);
        }

        @GetMapping("/query-binding")
        public Map<String, String> queryBinding(@Valid @ModelAttribute QueryFilterParams params) {
            return Map.of("status", "ok");
        }

        @GetMapping("/missing-header")
        public Map<String, String> missingHeader(@RequestHeader("X-Custom-Tenant") String tenant) {
            return Map.of("tenant", tenant);
        }

        @GetMapping("/forbidden")
        public void forbidden() {
            throw new ForbiddenOperationException("Forbidden domain action");
        }

        @GetMapping("/max-upload")
        public void maxUpload() {
            throw new MaxUploadSizeExceededException(10_485_760L);
        }

        @GetMapping("/optimistic-lock")
        public void optimisticLock() {
            throw new ObjectOptimisticLockingFailureException("UserProfile", 42L);
        }

        @GetMapping("/data-integrity")
        public void dataIntegrity() {
            throw new DataIntegrityViolationException("Duplicate constraint uk_sensitive_sql_leak");
        }

        @GetMapping("/storage-failure")
        public void storageFailure() {
            throw new InternalStorageException("IO failure on internal path /var/private/secret/file.pdf");
        }

        @GetMapping("/unexpected")
        public void unexpected() {
            throw new IllegalStateException("jdbc:mysql://internal-secret-host:3306 password=supersecret");
        }
    }

    @Data
    static class SensitiveValidationPayload {
        @Email(message = "Must be a valid email")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "API key is required")
        private String apiKey;

        @NotBlank(message = "Token is required")
        private String refreshToken;

        private boolean agreeTerms;

        @AssertTrue(message = "Terms must be accepted")
        public boolean isTermsAccepted() {
            return agreeTerms;
        }
    }

    @Data
    static class QueryFilterParams {
        @Min(value = 1, message = "Size must be positive")
        private Integer size;
    }

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void validationAndBindingErrors_redactSensitiveFieldsAndIncludeStructuredItems() throws Exception {
        String token = registerAndGetToken("global.err1@eurojob.eu");

        String invalidPayload = """
                {
                  "email": "bad-email",
                  "apiKey": "",
                  "refreshToken": "",
                  "agreeTerms": false
                }
                """;

        mockMvc.perform(post("/api/v1/test-errors/sensitive-validation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed for one or more fields"))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.validationErrors[?(@.field=='email')].rejectedValue", hasItem("bad-email")))
                .andExpect(jsonPath("$.validationErrors[?(@.field=='apiKey')].rejectedValue", hasItem("[REDACTED]")))
                .andExpect(jsonPath("$.validationErrors[?(@.field=='refreshToken')].rejectedValue", hasItem("[REDACTED]")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("termsAccepted")));

        // HandlerMethodValidationException on @RequestParam constraint without class @Validated
        mockMvc.perform(get("/api/v1/test-errors/method-param-validation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors[0].field").value("page"))
                .andExpect(jsonPath("$.validationErrors[0].rejectedValue").value("0"));

        // BindException via query parameters on @ModelAttribute
        mockMvc.perform(get("/api/v1/test-errors/query-binding")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("size", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors[0].field").value("size"));

        // ConstraintViolationException on @PathVariable @ValidUuid in ResumeController
        mockMvc.perform(get("/api/v1/resumes/not-a-valid-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors[0].field").value("uuid"));
    }

    @Test
    void httpProtocolAndRoutingExceptions_returnStandardizedJsonApiErrors() throws Exception {
        String token = registerAndGetToken("global.err2@eurojob.eu");

        // Malformed JSON -> 400
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Malformed request payload")));

        // Unsupported Content-Type -> 415
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));

        // Unsupported HTTP method -> 405 with Allow header
        mockMvc.perform(delete("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists(HttpHeaders.ALLOW))
                .andExpect(jsonPath("$.status").value(405));

        // Missing header -> 400
        mockMvc.perform(get("/api/v1/test-errors/missing-header")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("X-Custom-Tenant")));

        // Unknown authenticated route -> 404 JSON instead of Whitelabel
        mockMvc.perform(get("/api/v1/nonexistent-endpoint")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/nonexistent-endpoint"));
    }

    @Test
    void domainSecurityConflictAnd5xxErrors_maskInternalDetailsAndReturnProperCodes() throws Exception {
        String token = registerAndGetToken("global.err3@eurojob.eu");

        mockMvc.perform(get("/api/v1/test-errors/forbidden")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Forbidden domain action"));

        mockMvc.perform(get("/api/v1/test-errors/max-upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413));

        mockMvc.perform(get("/api/v1/test-errors/optimistic-lock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(get("/api/v1/test-errors/data-integrity")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(not(containsString("uk_sensitive_sql_leak"))));

        mockMvc.perform(get("/api/v1/test-errors/storage-failure")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Failed to process file storage"))
                .andExpect(jsonPath("$.message").value(not(containsString("/var/private"))));

        mockMvc.perform(get("/api/v1/test-errors/unexpected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected internal server error occurred"))
                .andExpect(jsonPath("$.message").value(not(containsString("supersecret"))));
    }

    @Test
    void openApiSpecification_remainsHealthyWithGlobalExceptionHandlerActive() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ValidationErrorItem").exists());
    }

    private String registerAndGetToken(String email) throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email(email)
                .password("SecureP@ssw0rd2026!")
                .firstName("Lukas")
                .lastName("Mueller")
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

