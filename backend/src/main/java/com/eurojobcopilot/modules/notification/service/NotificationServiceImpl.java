package com.eurojobcopilot.modules.notification.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.notification.dto.MarkAllReadResponse;
import com.eurojobcopilot.modules.notification.dto.NotificationDto;
import com.eurojobcopilot.modules.notification.dto.PagedNotificationsResponse;
import com.eurojobcopilot.modules.notification.dto.UnreadCountResponse;
import com.eurojobcopilot.modules.notification.entity.Notification;
import com.eurojobcopilot.modules.notification.entity.NotificationReferenceType;
import com.eurojobcopilot.modules.notification.entity.NotificationType;
import com.eurojobcopilot.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedNotificationsResponse listNotifications(
            AuthenticatedUserPrincipal currentUser,
            boolean unreadOnly,
            NotificationType type,
            int page,
            int size
    ) {
        Long userId = requireUserId(currentUser);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, size > 0 ? size : DEFAULT_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize);

        Page<Notification> notificationsPage;
        if (type != null && unreadOnly) {
            notificationsPage = notificationRepository.findAllByUserIdAndTypeAndReadFalseOrderByCreatedAtDesc(
                    userId, type, pageRequest);
        } else if (type != null) {
            notificationsPage = notificationRepository.findAllByUserIdAndTypeOrderByCreatedAtDesc(
                    userId, type, pageRequest);
        } else if (unreadOnly) {
            notificationsPage = notificationRepository.findAllByUserIdAndReadFalseOrderByCreatedAtDesc(
                    userId, pageRequest);
        } else {
            notificationsPage = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageRequest);
        }

        long unreadCount = notificationRepository.countByUserIdAndReadFalse(userId);
        List<NotificationDto> content = notificationsPage.getContent().stream()
                .map(this::toDto)
                .toList();

        return PagedNotificationsResponse.builder()
                .content(content)
                .pageNumber(notificationsPage.getNumber())
                .pageSize(notificationsPage.getSize())
                .totalElements(notificationsPage.getTotalElements())
                .totalPages(notificationsPage.getTotalPages())
                .first(notificationsPage.isFirst())
                .last(notificationsPage.isLast())
                .hasNext(notificationsPage.hasNext())
                .hasPrevious(notificationsPage.hasPrevious())
                .unreadCount(unreadCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(AuthenticatedUserPrincipal currentUser) {
        Long userId = requireUserId(currentUser);
        return UnreadCountResponse.builder()
                .unreadCount(notificationRepository.countByUserIdAndReadFalse(userId))
                .build();
    }

    @Override
    @Transactional
    public NotificationDto markRead(AuthenticatedUserPrincipal currentUser, Long id) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(id);

        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.saveAndFlush(notification);
        }
        return toDto(notification);
    }

    @Override
    @Transactional
    public MarkAllReadResponse markAllRead(AuthenticatedUserPrincipal currentUser) {
        Long userId = requireUserId(currentUser);
        int marked = notificationRepository.markAllAsRead(userId, Instant.now());
        long remainingUnread = notificationRepository.countByUserIdAndReadFalse(userId);
        return MarkAllReadResponse.builder()
                .markedCount(marked)
                .unreadCount(remainingUnread)
                .build();
    }

    @Override
    @Transactional
    public void deleteNotification(AuthenticatedUserPrincipal currentUser, Long id) {
        Long userId = requireUserId(currentUser);
        validatePositiveId(id);

        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        notificationRepository.delete(notification);
    }

    @Override
    @Transactional
    public boolean createNotificationIfNotDuplicate(
            User user,
            NotificationType type,
            String title,
            String message,
            NotificationReferenceType referenceType,
            Long referenceId,
            Duration dedupeWindow
    ) {
        if (user == null || user.getId() == null || type == null || title == null || title.isBlank()
                || message == null || message.isBlank()) {
            return false;
        }

        if (referenceType != null && referenceId != null && dedupeWindow != null && !dedupeWindow.isNegative()) {
            Instant cutoff = Instant.now().minus(dedupeWindow);
            boolean duplicate = notificationRepository
                    .existsByUserIdAndTypeAndReferenceTypeAndReferenceIdAndCreatedAtAfter(
                            user.getId(), type, referenceType, referenceId, cutoff
                    );
            if (duplicate) {
                log.debug("Skipped duplicate notification userId={} type={} refType={} refId={}",
                        user.getId(), type, referenceType, referenceId);
                return false;
            }
        }

        String safeTitle = title.trim().length() > 200 ? title.trim().substring(0, 197) + "..." : title.trim();
        Notification saved = notificationRepository.saveAndFlush(Notification.builder()
                .user(user)
                .type(type)
                .title(safeTitle)
                .message(message.trim())
                .referenceType(referenceType)
                .referenceId(referenceId)
                .read(false)
                .build());
        log.debug("Created notification id={} userId={} type={}", saved.getId(), user.getId(), type);
        return true;
    }

    private NotificationDto toDto(Notification entity) {
        return NotificationDto.builder()
                .id(entity.getId())
                .type(entity.getType())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .referenceType(entity.getReferenceType())
                .referenceId(entity.getReferenceId())
                .read(entity.isRead())
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .targetRoute(resolveTargetRoute(entity))
                .build();
    }

    private String resolveTargetRoute(Notification entity) {
        if (entity.getReferenceType() != null) {
            return switch (entity.getReferenceType()) {
                case JOB -> "/jobs";
                case JOB_APPLICATION -> "/applications";
                case RECRUITER_INTERACTION -> "/recruiters";
                case RESUME -> "/resumes";
                case INTERVIEW_SESSION -> "/interview-prep";
                case COMPANY -> "/companies";
            };
        }
        if (entity.getType() == NotificationType.AUTOPILOT_DIGEST || entity.getType() == NotificationType.HIGH_MATCH_JOB) {
            return "/recommended-jobs";
        }
        if (entity.getType() == NotificationType.SPONSORSHIP_VERIFIED) {
            return "/sponsorship";
        }
        return "/notifications";
    }

    private Long requireUserId(AuthenticatedUserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return currentUser.getId();
    }

    private void validatePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessValidationException("Notification ID must be a positive integer");
        }
    }
}

