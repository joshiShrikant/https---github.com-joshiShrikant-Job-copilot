package com.eurojobcopilot.modules.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {

    private Email email = new Email();

    @Getter
    @Setter
    public static class Email {
        private boolean enabled = false;
        private String fromAddress = "no-reply@eurojobcopilot.com";
    }
}

