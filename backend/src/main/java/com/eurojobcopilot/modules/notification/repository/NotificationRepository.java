package com.eurojobcopilot.modules.notification.repository;

import com.eurojobcopilot.modules.notification.entity.Notification;
import com.eurojobcopilot.modules.notification.entity.NotificationReferenceType;
import com.eurojobcopilot.modules.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findAllByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findAllByUserIdAndTypeOrderByCreatedAtDesc(
            Long userId, NotificationType type, Pageable pageable
    );

    Page<Notification> findAllByUserIdAndTypeAndReadFalseOrderByCreatedAtDesc(
            Long userId, NotificationType type, Pageable pageable
    );

    long countByUserIdAndReadFalse(Long userId);

    boolean existsByUserIdAndTypeAndReferenceTypeAndReferenceIdAndCreatedAtAfter(
            Long userId,
            NotificationType type,
            NotificationReferenceType referenceType,
            Long referenceId,
            Instant after
    );

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.user.id = :userId AND n.read = false")
    int markAllAsRead(@Param("userId") Long userId, @Param("readAt") Instant readAt);
}
