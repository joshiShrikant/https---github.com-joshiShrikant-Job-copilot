package com.eurojobcopilot.modules.ai.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Dedicated HTTP client configuration for AI LLM provider adapters.
 */
@Configuration
public class AiHttpClientConfig {

    @Bean
    @Qualifier("aiRestClientBuilder")
    public RestClient.Builder aiRestClientBuilder(AiProperties aiProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(500, aiProperties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Math.max(1000, aiProperties.getReadTimeoutMs()));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "EuroJobCopilot-AI/1.0");
    }
}

