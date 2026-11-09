package com.eurojobcopilot.modules.notification.service;

import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.notification.dto.MarkAllReadResponse;
import com.eurojobcopilot.modules.notification.dto.NotificationDto;
import com.eurojobcopilot.modules.notification.dto.PagedNotificationsResponse;
import com.eurojobcopilot.modules.notification.dto.UnreadCountResponse;
import com.eurojobcopilot.modules.notification.entity.NotificationReferenceType;
import com.eurojobcopilot.modules.notification.entity.NotificationType;

import java.time.Duration;

public interface NotificationService {

    PagedNotificationsResponse listNotifications(
            AuthenticatedUserPrincipal currentUser,
            boolean unreadOnly,
            NotificationType type,
            int page,
            int size
    );

    UnreadCountResponse getUnreadCount(AuthenticatedUserPrincipal currentUser);

    NotificationDto markRead(AuthenticatedUserPrincipal currentUser, Long id);

    MarkAllReadResponse markAllRead(AuthenticatedUserPrincipal currentUser);

    void deleteNotification(AuthenticatedUserPrincipal currentUser, Long id);

    boolean createNotificationIfNotDuplicate(
            User user,
            NotificationType type,
            String title,
            String message,
            NotificationReferenceType referenceType,
            Long referenceId,
            Duration dedupeWindow
    );
}

