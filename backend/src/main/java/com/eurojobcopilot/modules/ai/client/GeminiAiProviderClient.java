package com.eurojobcopilot.modules.ai.client;

import com.eurojobcopilot.modules.ai.config.AiProperties;
import com.eurojobcopilot.modules.ai.exception.AiProviderException;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.ai.exception.AiServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Google Gemini Developer API adapter requesting JSON MIME structured completions.
 */
@Slf4j
@Component
@Order(1)
public class GeminiAiProviderClient implements AiProviderClient {

    public static final String PROVIDER_NAME = "GEMINI";
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    private static final String FALLBACK_MODEL = "gemini-3.5-flash-lite";
    private static final String DEFAULT_THINKING_LEVEL = "LOW";

    private final AiProperties aiProperties;
    private final RestClient restClient;

    public GeminiAiProviderClient(
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
        String raw = hasText(config().getModel()) ? config().getModel().trim() : DEFAULT_MODEL;
        return raw.startsWith("models/") ? raw.substring("models/".length()) : raw;
    }

    @Override
    public AiCompletionResult generateStructuredJson(AiJsonPromptRequest request) {
        if (!isConfigured()) {
            throw new AiServiceUnavailableException(
                    "Gemini AI provider is not configured. Set GEMINI_API_KEY in environment configuration."
            );
        }

        String model = getConfiguredModel();

        try {
            return invokeModel(model, request);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                String retryModel = !FALLBACK_MODEL.equalsIgnoreCase(model) ? FALLBACK_MODEL : model;
                log.warn("Gemini model={} returned HTTP 503 (high demand); retrying once on model={}", model, retryModel);
                if (retryModel.equalsIgnoreCase(model)) {
                    pauseBriefly();
                }
                try {
                    return invokeModel(retryModel, request);
                } catch (RestClientResponseException retryEx) {
                    throw new AiProviderException(
                            "Gemini API request failed with HTTP status " + retryEx.getStatusCode().value(), retryEx
                    );
                } catch (RestClientException retryEx) {
                    throw new AiProviderException("Gemini API communication failed: " + retryEx.getMessage(), retryEx);
                }
            }
            throw new AiProviderException(
                    "Gemini API request failed with HTTP status " + ex.getStatusCode().value(), ex
            );
        } catch (RestClientException ex) {
            throw new AiProviderException("Gemini API communication failed: " + ex.getMessage(), ex);
        }
    }

    private AiCompletionResult invokeModel(String model, AiJsonPromptRequest request) {
        int maxTokens = request.maxOutputTokens() != null
                ? request.maxOutputTokens()
                : Math.max(256, config().getMaxTokens());
        double temperature = request.temperature() != null ? request.temperature() : 0.1;

        Map<String, Object> body = new LinkedHashMap<>();
        if (hasText(request.systemPrompt())) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", request.systemPrompt()))
            ));
        }
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", request.userPrompt()))
        )));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", maxTokens);
        generationConfig.put("responseMimeType", "application/json");
        if (isGemini3OrNewer(model)) {
            generationConfig.put("thinkingConfig", Map.of("thinkingLevel", DEFAULT_THINKING_LEVEL));
        } else {
            generationConfig.put("temperature", temperature);
        }
        body.put("generationConfig", generationConfig);

        JsonNode root = restClient.post()
                .uri("/models/{model}:generateContent", model)
                .header("x-goog-api-key", config().getApiKey().trim())
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (root == null) {
            throw new AiResponseValidationException("Gemini provider returned an empty HTTP body.");
        }

        String contentText = extractCandidateText(root);
        if (!hasText(contentText)) {
            throw new AiResponseValidationException("Gemini response did not contain candidate text content.");
        }

        JsonNode usage = root.path("usageMetadata");
        Integer promptTokens = usage.hasNonNull("promptTokenCount") ? usage.get("promptTokenCount").asInt() : null;
        Integer completionTokens = usage.hasNonNull("candidatesTokenCount") ? usage.get("candidatesTokenCount").asInt() : null;

        return new AiCompletionResult(
                PROVIDER_NAME,
                model,
                contentText,
                promptTokens,
                completionTokens
        );
    }

    private static void pauseBriefly() {
        try {
            Thread.sleep(750L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private AiProperties.ProviderConfig config() {
        return aiProperties.getGemini() != null
                ? aiProperties.getGemini()
                : new AiProperties.ProviderConfig("", DEFAULT_BASE_URL, DEFAULT_MODEL, 8192);
    }

    private static String extractCandidateText(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.path("thought").asBoolean(false)) {
                continue;
            }
            JsonNode textNode = part.get("text");
            if (textNode != null && !textNode.isNull()) {
                String text = textNode.asText();
                if (hasText(text)) {
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }

    private static boolean isGemini3OrNewer(String model) {
        if (!hasText(model)) {
            return false;
        }
        String normalized = model.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("models/")) {
            normalized = normalized.substring("models/".length());
        }
        if (!normalized.startsWith("gemini-")) {
            return false;
        }
        String versionPart = normalized.substring("gemini-".length());
        int endIdx = 0;
        while (endIdx < versionPart.length() && Character.isDigit(versionPart.charAt(endIdx))) {
            endIdx++;
        }
        if (endIdx == 0) {
            return false;
        }
        try {
            return Integer.parseInt(versionPart.substring(0, endIdx)) >= 3;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimTrailingSlash(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}

