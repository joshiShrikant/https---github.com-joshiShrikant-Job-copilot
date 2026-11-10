package com.eurojobcopilot.modules.job.ingestion;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Externalized configuration properties for job ingestion providers and HTTP timeouts.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ingestion")
public class JobIngestionProperties {

    private boolean enabled = true;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
    private int defaultMaxPages = 3;
    private int maxResultsPerProvider = 100;
    private List<String> targetCountries = List.of(
            "Germany",
            "Netherlands",
            "Ireland",
            "Sweden",
            "Switzerland",
            "United Kingdom",
            "France",
            "Spain"
    );

    private ArbeitnowProperties arbeitnow = new ArbeitnowProperties();
    private AdzunaProperties adzuna = new AdzunaProperties();
    private JoobleProperties jooble = new JoobleProperties();
    private SchedulerProperties scheduler = new SchedulerProperties();

    @Getter
    @Setter
    public static class ArbeitnowProperties {
        private boolean enabled = true;
        private String baseUrl = "https://www.arbeitnow.com/api/job-board-api";
        private int maxPages = 3;
    }

    @Getter
    @Setter
    public static class AdzunaProperties {
        private boolean enabled = true;
        private String baseUrl = "https://api.adzuna.com/v1/api/jobs";
        private String appId = "";
        private String appKey = "";
        private List<String> countryCodes = List.of("de", "nl", "gb", "fr", "ch", "at");
        private int resultsPerPage = 50;
        private int maxPagesPerCountry = 2;
    }

    @Getter
    @Setter
    public static class JoobleProperties {
        private boolean enabled = true;
        private String baseUrl = "https://jooble.org/api";
        private String apiKey = "";
        private List<String> countryCodes = List.of("de", "nl", "gb", "ch");
        private List<String> locations = List.of();
        private String defaultKeywords = "Java OR Software Engineer";
        private int resultsPerPage = 50;
        private int maxPagesPerLocation = 1;
    }

    @Getter
    @Setter
    public static class SchedulerProperties {
        private boolean enabled = true;
        private String cron = "0 0 */6 * * *";
        private String expirationCron = "0 30 2 * * *";
        private String zone = "UTC";
        private int staleExpirationDays = 45;
        private int lookbackHours = 72;
    }
}

