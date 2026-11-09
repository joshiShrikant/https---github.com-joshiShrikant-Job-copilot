package com.eurojobcopilot.modules.ai.client;

import com.eurojobcopilot.modules.ai.config.AiProperties;
import com.eurojobcopilot.modules.ai.exception.AiProviderException;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.ai.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiAiProviderClientTest {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private AiProperties aiProperties;
    private MockRestServiceServer mockServer;
    private GeminiAiProviderClient client;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setEnabled(true);
        aiProperties.setProvider("gemini");
        aiProperties.getGemini().setApiKey("test-gemini-secret-key");
        aiProperties.getGemini().setBaseUrl(BASE_URL);
        aiProperties.getGemini().setModel("gemini-3.5-flash-lite");
        aiProperties.getGemini().setMaxTokens(4096);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiAiProviderClient(builder, aiProperties);
    }

    @Test
    void generateStructuredJson_sendsHeaderKeyAndParsesCandidateAndUsage() {
        mockServer.expect(once(), requestTo(BASE_URL + "/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-gemini-secret-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text", notNullValue()))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Analyze JD"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("LOW"))
                .andExpect(jsonPath("$.generationConfig.temperature").doesNotExist())
                .andRespond(withSuccess("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {
                                    "thought": true,
                                    "text": "Internal reasoning trace"
                                  },
                                  {
                                    "text": "{\\"jobTitle\\":\\"Senior Java Engineer\\"}"
                                  }
                                ]
                              }
                            }
                          ],
                          "usageMetadata": {
                            "promptTokenCount": 420,
                            "candidatesTokenCount": 180
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiCompletionResult result = client.generateStructuredJson(new AiJsonPromptRequest(
                "job-description-analysis",
                "Return JSON",
                "Analyze JD",
                0.1,
                null
        ));

        mockServer.verify();
        assertThat(result.provider()).isEqualTo("GEMINI");
        assertThat(result.model()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(result.rawContent()).isEqualTo("{\"jobTitle\":\"Senior Java Engineer\"}");
        assertThat(result.promptTokens()).isEqualTo(420);
        assertThat(result.completionTokens()).isEqualTo(180);
    }

    @Test
    void generateStructuredJson_fallsBackToLiteModelOnServiceUnavailable503() {
        aiProperties.getGemini().setModel("gemini-3.8-flash");

        mockServer.expect(once(), requestTo(BASE_URL + "/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        mockServer.expect(once(), requestTo(BASE_URL + "/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {
                                    "text": "{\\"fallback\\":true}"
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        AiCompletionResult result = client.generateStructuredJson(new AiJsonPromptRequest(
                "fallback-test",
                "sys",
                "user",
                0.1,
                null
        ));

        mockServer.verify();
        assertThat(result.model()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(result.rawContent()).isEqualTo("{\"fallback\":true}");
    }

    @Test
    void generateStructuredJson_sendsTemperatureForLegacyGemini2Models() {
        aiProperties.getGemini().setModel("gemini-2.0-flash");

        mockServer.expect(once(), requestTo(BASE_URL + "/models/gemini-2.0-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.generationConfig.temperature").value(0.2))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig").doesNotExist())
                .andRespond(withSuccess("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {
                                    "text": "{\\"ok\\":true}"
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        AiCompletionResult result = client.generateStructuredJson(new AiJsonPromptRequest(
                "legacy-test",
                "sys",
                "user",
                0.2,
                null
        ));

        mockServer.verify();
        assertThat(result.model()).isEqualTo("gemini-2.0-flash");
        assertThat(result.rawContent()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void generateStructuredJson_throwsServiceUnavailableWhenApiKeyMissing() {
        aiProperties.getGemini().setApiKey("  ");

        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.generateStructuredJson(new AiJsonPromptRequest(
                "job-description-analysis",
                "sys",
                "user",
                0.1,
                null
        )))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void generateStructuredJson_wrapsUpstreamFailureWithoutExposingSecret() {
        mockServer.expect(once(), requestTo(BASE_URL + "/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.generateStructuredJson(new AiJsonPromptRequest(
                "job-description-analysis",
                "sys",
                "user",
                0.1,
                null
        )))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("HTTP status 429")
                .hasMessageNotContaining("test-gemini-secret-key");

        mockServer.verify();
    }

    @Test
    void generateStructuredJson_throwsValidationExceptionWhenCandidateTextMissing() {
        mockServer.expect(once(), requestTo(BASE_URL + "/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "candidates": []
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateStructuredJson(new AiJsonPromptRequest(
                "job-description-analysis",
                "sys",
                "user",
                0.1,
                null
        )))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("candidate text content");

        mockServer.verify();
    }
}

