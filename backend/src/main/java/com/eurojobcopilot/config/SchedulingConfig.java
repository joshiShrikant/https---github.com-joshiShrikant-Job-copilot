package com.eurojobcopilot.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduled tasks outside disabled test profiles while allowing
 * individual schedulers (ingestion, IND sync, autopilot) to toggle independently.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "app.scheduling",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SchedulingConfig {
}

