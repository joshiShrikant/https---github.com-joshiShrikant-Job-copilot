package com.eurojobcopilot.modules.ai.service;

import com.eurojobcopilot.modules.ai.client.AiCompletionResult;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.client.AiProviderClient;
import com.eurojobcopilot.modules.ai.config.AiProperties;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.ai.exception.AiServiceUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Provider-agnostic AI orchestration gateway selecting configured LLM backends and enforcing
 * strict JSON extraction & schema deserialization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGatewayServiceImpl implements AiGatewayService {

    private final AiProperties aiProperties;
    private final List<AiProviderClient> providerClients;
    private final ObjectMapper objectMapper;

    @Override
    public <T> AiStructuredResponse<T> executeStructuredJsonPrompt(AiJsonPromptRequest request, Class<T> targetType) {
        Objects.requireNonNull(request, "AI prompt request must not be null");
        Objects.requireNonNull(targetType, "Target deserialization class must not be null");

        if (!aiProperties.isEnabled()) {
            throw new AiServiceUnavailableException(
                    "AI intelligence features are currently disabled (app.ai.enabled=false)."
            );
        }

        AiProviderClient client = selectConfiguredClient();
        AiCompletionResult completion = client.generateStructuredJson(request);

        String cleanedJson = stripMarkdownCodeFences(completion.rawContent());
        if (cleanedJson.isEmpty()) {
            throw new AiResponseValidationException(
                    "AI provider " + completion.provider() + " returned blank output instead of JSON."
            );
        }

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(cleanedJson);
        } catch (JsonProcessingException ex) {
            throw new AiResponseValidationException(
                    "AI provider " + completion.provider() + " returned malformed JSON: " + ex.getOriginalMessage(),
                    ex
            );
        }

        if (rootNode == null || !rootNode.isObject()) {
            throw new AiResponseValidationException(
                    "AI provider " + completion.provider() + " must return a top-level JSON object."
            );
        }

        T deserialized;
        String canonicalJson;
        try {
            deserialized = objectMapper.treeToValue(rootNode, targetType);
            canonicalJson = objectMapper.writeValueAsString(rootNode);
        } catch (JsonProcessingException ex) {
            throw new AiResponseValidationException(
                    "AI JSON response failed schema mapping for " + targetType.getSimpleName() + ": "
                            + ex.getOriginalMessage(),
                    ex
            );
        }

        log.info("AI structured completion task={} provider={} model={} promptTokens={} completionTokens={}",
                request.taskName(), completion.provider(), completion.model(),
                completion.promptTokens(), completion.completionTokens());

        return new AiStructuredResponse<>(
                completion.provider(),
                completion.model(),
                deserialized,
                canonicalJson,
                completion.promptTokens(),
                completion.completionTokens()
        );
    }

    private AiProviderClient selectConfiguredClient() {
        String preferred = aiProperties.getProvider() != null
                ? aiProperties.getProvider().trim().toUpperCase(Locale.ROOT)
                : "GEMINI";

        for (AiProviderClient client : providerClients) {
            if (preferred.equalsIgnoreCase(client.getProviderName()) && client.isConfigured()) {
                return client;
            }
        }

        for (AiProviderClient client : providerClients) {
            if (client.isConfigured()) {
                log.debug("Preferred AI provider {} lacks API key; using configured provider {}",
                        preferred, client.getProviderName());
                return client;
            }
        }

        throw new AiServiceUnavailableException(
                "No AI provider API key is configured. Set GEMINI_API_KEY, OPENAI_API_KEY, or ANTHROPIC_API_KEY to enable AI analysis."
        );
    }

    static String stripMarkdownCodeFences(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            } else {
                trimmed = trimmed.substring(3);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }
        return trimmed;
    }
}

