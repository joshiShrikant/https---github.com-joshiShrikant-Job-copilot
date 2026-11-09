package com.eurojobcopilot.modules.autopilot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.autopilot")
public class AutopilotProperties {

    private Scheduler scheduler = new Scheduler();
    private long manualRefreshCooldownSeconds = 120;
    private int maxNotificationsPerRun = 10;
    private int maxAiAnalysesPerRun = 3;

    @Getter
    @Setter
    public static class Scheduler {
        private boolean enabled = true;
        private String cron = "0 15 */6 * * *";
        private String zone = "UTC";
    }
}

