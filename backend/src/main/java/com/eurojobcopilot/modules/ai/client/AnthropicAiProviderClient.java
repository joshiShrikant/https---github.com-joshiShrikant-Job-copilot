package com.eurojobcopilot.modules.ai.client;

import com.eurojobcopilot.modules.ai.config.AiProperties;
import com.eurojobcopilot.modules.ai.exception.AiProviderException;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.ai.exception.AiServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API adapter requesting pure JSON structured output.
 */
@Component
@Order(3)
public class AnthropicAiProviderClient implements AiProviderClient {

    public static final String PROVIDER_NAME = "ANTHROPIC";
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";
    private static final String DEFAULT_MODEL = "claude-3-5-sonnet-latest";
    private static final String ANTHROPIC_API_VERSION = "2023-06-01";

    private final AiProperties aiProperties;
    private final RestClient restClient;

    public AnthropicAiProviderClient(
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
                    "Anthropic provider is not configured. Set ANTHROPIC_API_KEY in environment configuration."
            );
        }

        String model = getConfiguredModel();
        int maxTokens = request.maxOutputTokens() != null
                ? request.maxOutputTokens()
                : Math.max(256, config().getMaxTokens());
        double temperature = request.temperature() != null ? request.temperature() : 0.1;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        if (hasText(request.systemPrompt())) {
            body.put("system", request.systemPrompt());
        }
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", request.userPrompt()
        )));

        try {
            JsonNode root = restClient.post()
                    .uri("/messages")
                    .header("x-api-key", config().getApiKey().trim())
                    .header("anthropic-version", ANTHROPIC_API_VERSION)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null) {
                throw new AiResponseValidationException("Anthropic provider returned an empty HTTP body.");
            }

            JsonNode textNode = root.path("content").path(0).path("text");
            if (textNode.isMissingNode() || !hasText(textNode.asText())) {
                throw new AiResponseValidationException("Anthropic response did not contain message text content.");
            }

            JsonNode usage = root.path("usage");
            Integer promptTokens = usage.hasNonNull("input_tokens") ? usage.get("input_tokens").asInt() : null;
            Integer completionTokens = usage.hasNonNull("output_tokens") ? usage.get("output_tokens").asInt() : null;

            return new AiCompletionResult(
                    PROVIDER_NAME,
                    model,
                    textNode.asText(),
                    promptTokens,
                    completionTokens
            );
        } catch (RestClientResponseException ex) {
            throw new AiProviderException(
                    "Anthropic API request failed with HTTP status " + ex.getStatusCode().value(), ex
            );
        } catch (RestClientException ex) {
            throw new AiProviderException("Anthropic API communication failed: " + ex.getMessage(), ex);
        }
    }

    private AiProperties.ProviderConfig config() {
        return aiProperties.getAnthropic() != null
                ? aiProperties.getAnthropic()
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

