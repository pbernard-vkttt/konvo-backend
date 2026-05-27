package com.vulkantechtt.konvo.notifications;

import com.vulkantechtt.konvo.notifications.dto.NotificationFeed;
import com.vulkantechtt.konvo.notifications.dto.NotificationResponse;
import com.vulkantechtt.konvo.realtime.SseHub;
import com.vulkantechtt.konvo.users.MembershipStatus;
import com.vulkantechtt.konvo.users.Role;
import com.vulkantechtt.konvo.users.TenantMembership;
import com.vulkantechtt.konvo.users.TenantMembershipRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user in-app bell. The hub broadcast is tenant-scoped (the M4 SSE hub
 * doesn't fan out per-user), so every emitter on the tenant receives the
 * payload and the frontend filters by user id locally. That's the cheapest
 * shape that doesn't require a second per-user hub — agents are dozens, not
 * thousands.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final TenantMembershipRepository memberships;
    private final SseHub sseHub;

    public NotificationService(NotificationRepository notifications,
                               TenantMembershipRepository memberships,
                               SseHub sseHub) {
        this.notifications = notifications;
        this.memberships = memberships;
        this.sseHub = sseHub;
    }

    @Transactional
    public Notification notifyUser(UUID tenantId, UUID userId,
                                   NotificationType type, String title, String body, String link) {
        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setUserId(userId);
        n.setType(type.code());
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        Notification saved = notifications.save(n);
        sseHub.broadcast(tenantId, "notification", Map.of(
                "userId", userId.toString(),
                "id", saved.getId().toString(),
                "type", saved.getType(),
                "title", saved.getTitle()));
        return saved;
    }

    @Transactional
    public void broadcastToOwnersAndAdmins(UUID tenantId, NotificationType type,
                                            String title, String body, String link) {
        List<TenantMembership> targets = memberships
                .findByTenantIdAndRoleInAndStatus(tenantId,
                        List.of(Role.OWNER, Role.ADMIN),
                        MembershipStatus.active);
        for (TenantMembership m : targets) {
            notifyUser(tenantId, m.getUser().getId(), type, title, body, link);
        }
    }

    @Transactional(readOnly = true)
    public NotificationFeed feed(UUID userId) {
        List<NotificationResponse> items = notifications
                .findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationService::toResponse)
                .toList();
        long unread = notifications.countByUserIdAndReadAtIsNull(userId);
        return new NotificationFeed(items, unread);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId);
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getBody(),
                n.getLink(), n.getReadAt(), n.getCreatedAt());
    }
}
