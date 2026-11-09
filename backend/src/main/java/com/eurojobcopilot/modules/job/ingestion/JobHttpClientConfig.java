package com.eurojobcopilot.modules.job.ingestion;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client configuration dedicated to external job provider polling connectors.
 */
@Configuration
public class JobHttpClientConfig {

    @Bean
    @Qualifier("jobIngestionRestClientBuilder")
    public RestClient.Builder jobIngestionRestClientBuilder(JobIngestionProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(500, properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Math.max(1000, properties.getReadTimeoutMs()));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "EuroJobCopilot-Ingestion/1.0");
    }
}

