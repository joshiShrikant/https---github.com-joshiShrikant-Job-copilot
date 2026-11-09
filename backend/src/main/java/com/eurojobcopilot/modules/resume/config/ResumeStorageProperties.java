package com.eurojobcopilot.modules.resume.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for local/secure resume file storage.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.storage")
public class ResumeStorageProperties {

    /**
     * Root directory outside application classpath/webroot for storing uploaded resumes.
     */
    @NotBlank(message = "app.storage.resumes-path must not be blank")
    private String resumesPath = System.getProperty("java.io.tmpdir") + "/eurojob-copilot/resumes";

    /**
     * Maximum permitted file size in bytes (default: 10 MiB).
     */
    @Min(1)
    private long maxFileSizeBytes = 10L * 1024L * 1024L;
}

