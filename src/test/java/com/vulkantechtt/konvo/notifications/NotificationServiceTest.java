package com.vulkantechtt.konvo.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.realtime.SseHub;
import com.vulkantechtt.konvo.tenants.Tenant;
import com.vulkantechtt.konvo.users.MembershipStatus;
import com.vulkantechtt.konvo.users.Role;
import com.vulkantechtt.konvo.users.TenantMembership;
import com.vulkantechtt.konvo.users.TenantMembershipRepository;
import com.vulkantechtt.konvo.users.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notifications;
    @Mock TenantMembershipRepository memberships;
    @Mock SseHub sseHub;

    @Test
    void notifyUserPersistsAndBroadcasts() {
        NotificationService service = new NotificationService(notifications, memberships, sseHub);
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.onPersist();
            return n;
        });

        Notification saved = service.notifyUser(tenant, user, NotificationType.MEMBER_JOINED,
                "Hi", "Body", "/app/x");

        assertThat(saved.getTenantId()).isEqualTo(tenant);
        assertThat(saved.getUserId()).isEqualTo(user);
        assertThat(saved.getType()).isEqualTo("member.joined");
        verify(sseHub).broadcast(eq(tenant), eq("notification"), any());
    }

    @Test
    void broadcastToOwnersAndAdminsFansOutOnePerMember() {
        NotificationService service = new NotificationService(notifications, memberships, sseHub);
        UUID tenant = UUID.randomUUID();
        when(memberships.findByTenantIdAndRoleInAndStatus(eq(tenant),
                any(), eq(MembershipStatus.active)))
                .thenReturn(List.of(membership(tenant, Role.OWNER), membership(tenant, Role.ADMIN)));
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.onPersist();
            return n;
        });

        service.broadcastToOwnersAndAdmins(tenant, NotificationType.AI_QUOTA_PAUSED,
                "Vee paused", "Quota reached", "/app/settings/billing");

        ArgumentCaptor<Notification> saves = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(saves.capture());
        assertThat(saves.getAllValues())
                .extracting(Notification::getType)
                .containsOnly("ai.quota.paused");
    }

    private static TenantMembership membership(UUID tenantId, Role role) {
        TenantMembership m = new TenantMembership();
        Tenant t = new Tenant();
        t.setId(tenantId);
        m.setTenant(t);
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("u@x.tt");
        u.setFullName("U");
        m.setUser(u);
        m.setRole(role);
        m.setStatus(MembershipStatus.active);
        return m;
    }
}
