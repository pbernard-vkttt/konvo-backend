package com.vulkantechtt.konvo.users;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.auth.UserInvitationRepository;
import com.vulkantechtt.konvo.common.KonvoException;
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

    @InjectMocks MemberService service;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void cannotDemoteLastOwner() {
        UUID membershipId = UUID.randomUUID();
        TenantMembership lonelyOwner = membership(tenantId, UUID.randomUUID(), Role.OWNER);

        when(memberships.findById(membershipId)).thenReturn(Optional.of(lonelyOwner));
        when(memberships.countByTenantIdAndRoleAndStatus(tenantId, Role.OWNER, MembershipStatus.active))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(tenantId, membershipId, Role.ADMIN, UUID.randomUUID()))
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

        assertThatThrownBy(() -> service.remove(tenantId, membershipId, UUID.randomUUID()))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("at least one owner");
    }

    @Test
    void cannotRemoveYourself() {
        UUID membershipId = UUID.randomUUID();
        UUID actingId = UUID.randomUUID();
        TenantMembership me = membership(tenantId, actingId, Role.ADMIN);

        when(memberships.findById(membershipId)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.remove(tenantId, membershipId, actingId))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("can't remove yourself");
    }

    @Test
    void rejectsMembershipFromAnotherTenant() {
        UUID membershipId = UUID.randomUUID();
        TenantMembership foreign = membership(UUID.randomUUID(), UUID.randomUUID(), Role.AGENT);

        when(memberships.findById(membershipId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.changeRole(tenantId, membershipId, Role.ADMIN, UUID.randomUUID()))
                .isInstanceOf(KonvoException.class);
        verify(memberships, never()).countByTenantIdAndRoleAndStatus(eq(tenantId), any(), any());
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
