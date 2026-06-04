package com.vulkantechtt.konvo.notifications;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    boolean existsByTenantIdAndTypeAndCreatedAtAfter(UUID tenantId, String type, Instant after);

    @Modifying
    @Query("update Notification n set n.readAt = current_timestamp where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId);
}
