package com.eurojobcopilot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:4200", "http://127.0.0.1:4200");

    private long maxAgeSeconds = 3600;
}

