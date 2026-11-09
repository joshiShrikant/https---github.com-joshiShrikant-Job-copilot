package com.eurojobcopilot.modules.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Current unread notification count for header badge synchronization")
public class UnreadCountResponse {

    private long unreadCount;
}

