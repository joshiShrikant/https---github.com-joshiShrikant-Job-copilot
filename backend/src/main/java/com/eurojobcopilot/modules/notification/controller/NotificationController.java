package com.eurojobcopilot.modules.notification.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.core.dto.ApiResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.notification.dto.MarkAllReadResponse;
import com.eurojobcopilot.modules.notification.dto.NotificationDto;
import com.eurojobcopilot.modules.notification.dto.PagedNotificationsResponse;
import com.eurojobcopilot.modules.notification.dto.UnreadCountResponse;
import com.eurojobcopilot.modules.notification.entity.NotificationType;
import com.eurojobcopilot.modules.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(
            summary = "List user notifications",
            operationId = "listNotifications",
            description = "Retrieves paginated in-app notifications for high-match jobs, sponsorship updates, follow-up reminders, and autopilot digests."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Paginated user notifications returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination or notification filter parameter",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<PagedNotificationsResponse> listNotifications(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "When true, returns only unread notifications")
            @RequestParam(value = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @Parameter(description = "Optional notification category filter")
            @RequestParam(value = "type", required = false) NotificationType type,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1..100)", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(notificationService.listNotifications(currentUser, unreadOnly, type, page, size));
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "Get unread notification count",
            operationId = "getUnreadNotificationCount",
            description = "Returns the current number of unread notifications for the authenticated user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Unread count returned",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(notificationService.getUnreadCount(currentUser));
    }

    @PatchMapping("/{id}/read")
    @Operation(
            summary = "Mark notification as read",
            operationId = "markNotificationRead",
            description = "Marks a single notification owned by the authenticated user as read."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Notification marked as read",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid notification ID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Notification not found for user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<NotificationDto> markRead(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @PathVariable("id") @Positive Long id
    ) {
        return ResponseEntity.ok(notificationService.markRead(currentUser, id));
    }

    @PostMapping("/mark-all-read")
    @Operation(
            summary = "Mark all user notifications as read",
            operationId = "markAllNotificationsRead",
            description = "Marks all unread notifications owned by the authenticated user as read."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "All notifications marked read",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<MarkAllReadResponse> markAllRead(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(notificationService.markAllRead(currentUser));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a notification",
            operationId = "deleteNotification",
            description = "Deletes an in-app notification owned by the authenticated user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Notification deleted",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid notification ID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT Bearer access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Notification not found for user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @PathVariable("id") @Positive Long id
    ) {
        notificationService.deleteNotification(currentUser, id);
        return ResponseEntity.ok(ApiResponse.ok("Notification deleted successfully"));
    }
}

