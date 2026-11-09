package com.eurojobcopilot.modules.ai.client;

import com.eurojobcopilot.modules.ai.config.AiProperties;
import com.eurojobcopilot.modules.ai.exception.AiProviderException;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.ai.exception.AiServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions adapter enforcing JSON-object response format.
 */
@Component
@Order(2)
public class OpenAiProviderClient implements AiProviderClient {

    public static final String PROVIDER_NAME = "OPENAI";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final AiProperties aiProperties;
    private final RestClient restClient;

    public OpenAiProviderClient(
            @Qualifier("aiRestClientBuilder") RestClient.Builder restClientBuilder,
            AiProperties aiProperties
    ) {
        this.aiProperties = aiProperties;
        String baseUrl = hasText(config().getBaseUrl()) ? trimTrailingSlash(config().getBaseUrl()) : DEFAULT_BASE_URL;
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isConfigured() {
        return hasText(config().getApiKey()) && hasText(config().getBaseUrl());
    }

    @Override
    public String getConfiguredModel() {
        return hasText(config().getModel()) ? config().getModel().trim() : DEFAULT_MODEL;
    }

    @Override
    public AiCompletionResult generateStructuredJson(AiJsonPromptRequest request) {
        if (!isConfigured()) {
            throw new AiServiceUnavailableException(
                    "OpenAI provider is not configured. Set OPENAI_API_KEY in environment configuration."
            );
        }

        String model = getConfiguredModel();
        int maxTokens = request.maxOutputTokens() != null
                ? request.maxOutputTokens()
                : Math.max(256, config().getMaxTokens());
        double temperature = request.temperature() != null ? request.temperature() : 0.1;

        List<Map<String, String>> messages = new ArrayList<>();
        if (hasText(request.systemPrompt())) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", messages);

        try {
            JsonNode root = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config().getApiKey().trim())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null) {
                throw new AiResponseValidationException("OpenAI provider returned an empty HTTP body.");
            }

            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || !hasText(contentNode.asText())) {
                throw new AiResponseValidationException("OpenAI response did not contain choice message content.");
            }

            JsonNode usage = root.path("usage");
            Integer promptTokens = usage.hasNonNull("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null;
            Integer completionTokens = usage.hasNonNull("completion_tokens") ? usage.get("completion_tokens").asInt() : null;

            return new AiCompletionResult(
                    PROVIDER_NAME,
                    model,
                    contentNode.asText(),
                    promptTokens,
                    completionTokens
            );
        } catch (RestClientResponseException ex) {
            throw new AiProviderException(
                    "OpenAI API request failed with HTTP status " + ex.getStatusCode().value(), ex
            );
        } catch (RestClientException ex) {
            throw new AiProviderException("OpenAI API communication failed: " + ex.getMessage(), ex);
        }
    }

    private AiProperties.ProviderConfig config() {
        return aiProperties.getOpenai() != null
                ? aiProperties.getOpenai()
                : new AiProperties.ProviderConfig("", DEFAULT_BASE_URL, DEFAULT_MODEL, 4096);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimTrailingSlash(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}

