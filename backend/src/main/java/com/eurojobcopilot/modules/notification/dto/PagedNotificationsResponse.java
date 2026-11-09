package com.eurojobcopilot.modules.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated user notification list accompanied by current unread counter")
public class PagedNotificationsResponse {

    private List<NotificationDto> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;

    @JsonProperty("isFirst")
    @Schema(name = "isFirst")
    private boolean first;

    @JsonProperty("isLast")
    @Schema(name = "isLast")
    private boolean last;

    private boolean hasNext;
    private boolean hasPrevious;
    private long unreadCount;
}

