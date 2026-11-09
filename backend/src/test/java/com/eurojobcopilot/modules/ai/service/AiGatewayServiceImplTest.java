package com.eurojobcopilot.modules.ai.service;

import com.eurojobcopilot.modules.ai.client.AiCompletionResult;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.client.AiProviderClient;
import com.eurojobcopilot.modules.ai.config.AiProperties;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.ai.exception.AiServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGatewayServiceImplTest {

    private AiProperties aiProperties;

    @Mock
    private AiProviderClient geminiClient;

    @Mock
    private AiProviderClient openAiClient;

    private AiGatewayServiceImpl gateway;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setEnabled(true);
        aiProperties.setProvider("gemini");

        gateway = new AiGatewayServiceImpl(
                aiProperties,
                List.of(geminiClient, openAiClient),
                new ObjectMapper()
        );
    }

    @Test
    void executeStructuredJsonPrompt_fallsBackToConfiguredProviderAndStripsMarkdownFences() {
        when(geminiClient.getProviderName()).thenReturn("GEMINI");
        when(openAiClient.getProviderName()).thenReturn("OPENAI");
        when(geminiClient.isConfigured()).thenReturn(false);
        when(openAiClient.isConfigured()).thenReturn(true);
        when(openAiClient.generateStructuredJson(any())).thenReturn(new AiCompletionResult(
                "OPENAI",
                "gpt-4o-mini",
                """
                ```json
                {
                  "title": "Staff Java Engineer",
                  "years": 7
                }
                ```
                """,
                210,
                95
        ));

        AiStructuredResponse<SampleDto> response = gateway.executeStructuredJsonPrompt(
                new AiJsonPromptRequest("test", "sys", "user", 0.1, 1000),
                SampleDto.class
        );

        assertThat(response.provider()).isEqualTo("OPENAI");
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
        assertThat(response.payload().title()).isEqualTo("Staff Java Engineer");
        assertThat(response.payload().years()).isEqualTo(7);
        assertThat(response.normalizedJson()).contains("\"Staff Java Engineer\"");
        verify(openAiClient).generateStructuredJson(any());
    }

    @Test
    void executeStructuredJsonPrompt_throwsServiceUnavailableWhenDisabledOrUnconfigured() {
        aiProperties.setEnabled(false);

        assertThatThrownBy(() -> gateway.executeStructuredJsonPrompt(
                new AiJsonPromptRequest("test", "sys", "user", 0.1, 1000),
                SampleDto.class
        ))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("disabled");
        verifyNoInteractions(geminiClient, openAiClient);

        aiProperties.setEnabled(true);
        when(geminiClient.getProviderName()).thenReturn("GEMINI");
        when(openAiClient.getProviderName()).thenReturn("OPENAI");
        when(geminiClient.isConfigured()).thenReturn(false);
        when(openAiClient.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> gateway.executeStructuredJsonPrompt(
                new AiJsonPromptRequest("test", "sys", "user", 0.1, 1000),
                SampleDto.class
        ))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void executeStructuredJsonPrompt_rejectsMalformedOrNonObjectJson() {
        when(geminiClient.getProviderName()).thenReturn("GEMINI");
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.generateStructuredJson(any())).thenReturn(new AiCompletionResult(
                "GEMINI",
                "gemini-2.5-flash",
                "{invalid-json",
                50,
                10
        ));

        assertThatThrownBy(() -> gateway.executeStructuredJsonPrompt(
                new AiJsonPromptRequest("test", "sys", "user", 0.1, 1000),
                SampleDto.class
        ))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("malformed JSON");

        when(geminiClient.generateStructuredJson(any())).thenReturn(new AiCompletionResult(
                "GEMINI",
                "gemini-2.5-flash",
                "[\"not-an-object\"]",
                50,
                10
        ));

        assertThatThrownBy(() -> gateway.executeStructuredJsonPrompt(
                new AiJsonPromptRequest("test", "sys", "user", 0.1, 1000),
                SampleDto.class
        ))
                .isInstanceOf(AiResponseValidationException.class)
                .hasMessageContaining("top-level JSON object");
    }

    record SampleDto(String title, int years) {
    }
}
