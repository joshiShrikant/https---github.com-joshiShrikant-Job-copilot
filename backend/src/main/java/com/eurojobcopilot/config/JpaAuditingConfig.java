package com.eurojobcopilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA Auditing on a dedicated configuration class so web slice tests (@WebMvcTest)
 * can initialize independently without requiring JPA metamodel beans.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}

