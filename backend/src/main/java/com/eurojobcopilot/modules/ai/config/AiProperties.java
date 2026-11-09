package com.eurojobcopilot.modules.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized configuration properties for AI LLM providers, timeouts, and input limits.
 * API keys are supplied strictly via environment variables and never hardcoded.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private boolean enabled = true;

    @NotBlank
    private String provider = "gemini";

    @Min(500)
    @Max(30000)
    private int connectTimeoutMs = 10000;

    @Min(1000)
    @Max(120000)
    private int readTimeoutMs = 75000;

    @Min(1000)
    @Max(100000)
    private int maxInputChars = 18000;

    @Valid
    private ProviderConfig gemini = new ProviderConfig(
            "",
            "https://generativelanguage.googleapis.com/v1beta",
            "gemini-3.5-flash-lite",
            8192
    );

    @Valid
    private ProviderConfig openai = new ProviderConfig(
            "",
            "https://api.openai.com/v1",
            "gpt-4o-mini",
            4096
    );

    @Valid
    private ProviderConfig anthropic = new ProviderConfig(
            "",
            "https://api.anthropic.com/v1",
            "claude-3-5-sonnet-latest",
            4096
    );

    @Getter
    @Setter
    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl;
        private String model;

        @Min(256)
        @Max(32768)
        private int maxTokens = 4096;

        public ProviderConfig() {
        }

        public ProviderConfig(String apiKey, String baseUrl, String model, int maxTokens) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
            this.maxTokens = maxTokens;
        }
    }
}

