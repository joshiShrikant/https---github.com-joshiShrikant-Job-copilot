package com.eurojobcopilot.modules.sponsorship.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.sponsorship")
public class SponsorshipProperties {

    private IndProperties ind = new IndProperties();

    @Getter
    @Setter
    public static class IndProperties {
        private boolean enabled = true;
        private String registerUrl =
                "https://ind.nl/en/public-register-recognised-sponsors/public-register-regular-labour-and-highly-skilled-migrants";
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 20000;
        private String userAgent = "EuroJobCopilot/1.0 (+https://eurojobcopilot.com)";
        private boolean schedulerEnabled = false;
        private String syncCron = "0 15 3 * * *";
        private String syncZone = "UTC";
    }
}

