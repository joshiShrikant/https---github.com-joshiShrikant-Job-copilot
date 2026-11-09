package com.eurojobcopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * EuroJob Copilot - Core Spring Boot Application Entry Point.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EuroJobCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(EuroJobCopilotApplication.class, args);
    }
}

