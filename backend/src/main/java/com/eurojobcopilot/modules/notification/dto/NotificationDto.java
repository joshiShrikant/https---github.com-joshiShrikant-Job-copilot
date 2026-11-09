package com.eurojobcopilot.modules.notification.dto;

import com.eurojobcopilot.modules.notification.entity.NotificationReferenceType;
import com.eurojobcopilot.modules.notification.entity.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "In-app notification entry for high-match jobs, visa sponsorship alerts, follow-up reminders, and autopilot digests")
public class NotificationDto {

    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private NotificationReferenceType referenceType;
    private Long referenceId;

    @JsonProperty("isRead")
    @Schema(name = "isRead", description = "Whether the notification has been read by the user", example = "false")
    private boolean read;

    private Instant readAt;
    private Instant createdAt;

    @Schema(description = "Recommended client route to inspect the referenced item", example = "/jobs")
    private String targetRoute;
}

