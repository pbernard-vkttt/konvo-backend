package com.vulkantechtt.konvo.users;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.auth.UserInvitationRepository;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock TenantMembershipRepository memberships;
    @Mock UserInvitationRepository invitations;
    @Mock UserRepository users;
    @Mock AuditService audit;

    @InjectMocks MemberService service;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void cannotDemoteLastOwner() {
        UUID membershipId = UUID.randomUUID();
        TenantMembership lonelyOwner = membership(tenantId, UUID.randomUUID(), Role.OWNER);

        when(memberships.findById(membershipId)).thenReturn(Optional.of(lonelyOwner));
        when(memberships.countByTenantIdAndRoleAndStatus(tenantId, Role.OWNER, MembershipStatus.active))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(principal(UUID.randomUUID(), Role.OWNER), membershipId, Role.ADMIN))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("at least one owner");
        verify(memberships, never()).save(any());
    }

    @Test
    void cannotRemoveLastOwner() {
        UUID membershipId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        TenantMembership lonelyOwner = membership(tenantId, otherUserId, Role.OWNER);

        when(memberships.findById(membershipId)).thenReturn(Optional.of(lonelyOwner));
        when(memberships.countByTenantIdAndRoleAndStatus(tenantId, Role.OWNER, MembershipStatus.active))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.remove(principal(UUID.randomUUID(), Role.OWNER), membershipId))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("at least one owner");
    }

    @Test
    void cannotRemoveYourself() {
        UUID membershipId = UUID.randomUUID();
        UUID actingId = UUID.randomUUID();
        TenantMembership me = membership(tenantId, actingId, Role.ADMIN);

        when(memberships.findById(membershipId)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.remove(principal(actingId, Role.ADMIN), membershipId))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("can't remove yourself");
    }

    @Test
    void rejectsMembershipFromAnotherTenant() {
        UUID membershipId = UUID.randomUUID();
        TenantMembership foreign = membership(UUID.randomUUID(), UUID.randomUUID(), Role.AGENT);

        when(memberships.findById(membershipId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.changeRole(principal(UUID.randomUUID(), Role.OWNER), membershipId, Role.ADMIN))
                .isInstanceOf(KonvoException.class);
        verify(memberships, never()).countByTenantIdAndRoleAndStatus(eq(tenantId), any(), any());
    }

    private KonvoPrincipal principal(UUID userId, Role role) {
        return new KonvoPrincipal(userId, "actor@x.tt", "Actor", tenantId, role);
    }

    private static TenantMembership membership(UUID tenantId, UUID userId, Role role) {
        TenantMembership m = new TenantMembership();
        Tenant t = new Tenant();
        t.setId(tenantId);
        m.setTenant(t);
        User u = new User();
        u.setId(userId);
        u.setEmail("u@x.tt");
        u.setFullName("U");
        m.setUser(u);
        m.setRole(role);
        m.setStatus(MembershipStatus.active);
        return m;
    }
}
