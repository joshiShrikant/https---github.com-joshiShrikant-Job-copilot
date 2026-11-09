package com.eurojobcopilot.modules.sponsorship.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client configuration dedicated to official sponsorship register synchronization.
 */
@Configuration
public class SponsorshipHttpClientConfig {

    @Bean
    @Qualifier("sponsorshipRestClientBuilder")
    public RestClient.Builder sponsorshipRestClientBuilder(SponsorshipProperties properties) {
        SponsorshipProperties.IndProperties indProps = properties.getInd();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(500, indProps.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Math.max(1000, indProps.getReadTimeoutMs()));
        return RestClient.builder()
                .requestFactory(requestFactory);
    }
}

