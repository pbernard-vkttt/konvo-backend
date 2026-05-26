package com.vulkantechtt.konvo.users;

import com.vulkantechtt.konvo.auth.TokenHasher;
import com.vulkantechtt.konvo.auth.UserInvitation;
import com.vulkantechtt.konvo.auth.UserInvitationRepository;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.users.dto.InvitationResponse;
import com.vulkantechtt.konvo.users.dto.InviteMemberRequest;
import com.vulkantechtt.konvo.users.dto.MemberResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped team management: listing members, inviting new members,
 * changing roles, and removing members. The acting user's tenant scope is
 * taken from the {@link com.vulkantechtt.konvo.security.KonvoPrincipal}
 * supplied by the controller (after RBAC checks).
 */
@Service
public class MemberService {

    private static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final TenantMembershipRepository memberships;
    private final UserInvitationRepository invitations;
    private final UserRepository users;

    public MemberService(
            TenantMembershipRepository memberships,
            UserInvitationRepository invitations,
            UserRepository users) {
        this.memberships = memberships;
        this.invitations = invitations;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID tenantId) {
        return memberships.findByTenantIdAndStatus(tenantId, MembershipStatus.active).stream()
                .map(m -> new MemberResponse(
                        m.getId(),
                        m.getUser().getId(),
                        m.getUser().getEmail(),
                        m.getUser().getFullName(),
                        m.getRole(),
                        m.getStatus().name(),
                        m.getCreatedAt(),
                        m.getUser().getLastLoginAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> listPendingInvitations(UUID tenantId) {
        return invitations.findByTenantIdAndAcceptedAtIsNullAndRevokedAtIsNull(tenantId).stream()
                .map(inv -> new InvitationResponse(
                        inv.getId(),
                        inv.getEmail(),
                        inv.getRole(),
                        inv.getExpiresAt(),
                        inv.getCreatedAt(),
                        null))
                .toList();
    }

    @Transactional
    public InvitationResponse invite(UUID tenantId, UUID invitedByUserId, InviteMemberRequest req) {
        String email = req.email().toLowerCase();
        users.findByEmailIgnoreCase(email)
                .flatMap(u -> memberships.findByTenantIdAndUserId(tenantId, u.getId()))
                .filter(m -> m.getStatus() == MembershipStatus.active)
                .ifPresent(m -> {
                    throw KonvoException.conflict("That email is already a member of this workspace");
                });
        invitations.findByTenantIdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(tenantId, email)
                .ifPresent(existing -> {
                    throw KonvoException.conflict("An invitation is already pending for that email");
                });

        String raw = TokenHasher.randomToken();
        UserInvitation inv = new UserInvitation();
        inv.setTenantId(tenantId);
        inv.setEmail(email);
        inv.setRole(req.role());
        inv.setTokenHash(TokenHasher.hash(raw));
        inv.setInvitedByUserId(invitedByUserId);
        inv.setExpiresAt(Instant.now().plus(INVITATION_TTL));
        UserInvitation saved = invitations.save(inv);
        return new InvitationResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getRole(),
                saved.getExpiresAt(),
                saved.getCreatedAt(),
                raw);
    }

    @Transactional
    public void revokeInvitation(UUID tenantId, UUID invitationId) {
        UserInvitation inv = invitations.findById(invitationId)
                .orElseThrow(() -> KonvoException.notFound("Invitation", invitationId));
        if (!inv.getTenantId().equals(tenantId)) {
            throw KonvoException.notFound("Invitation", invitationId);
        }
        inv.setRevokedAt(Instant.now());
        invitations.save(inv);
    }

    @Transactional
    public MemberResponse changeRole(UUID tenantId, UUID membershipId, Role newRole, UUID actingUserId) {
        TenantMembership target = memberships.findById(membershipId)
                .orElseThrow(() -> KonvoException.notFound("Member", membershipId));
        if (!target.getTenant().getId().equals(tenantId)) {
            throw KonvoException.notFound("Member", membershipId);
        }
        guardLastOwnerInvariant(tenantId, target, newRole, /*removal*/ false);
        if (target.getUser().getId().equals(actingUserId) && target.getRole() == Role.OWNER && newRole != Role.OWNER) {
            // Same guard, friendlier message for the "demoting yourself" case.
            throw KonvoException.badRequest("You can't change your own owner role — promote another owner first");
        }
        target.setRole(newRole);
        TenantMembership saved = memberships.save(target);
        return toResponse(saved);
    }

    @Transactional
    public void remove(UUID tenantId, UUID membershipId, UUID actingUserId) {
        TenantMembership target = memberships.findById(membershipId)
                .orElseThrow(() -> KonvoException.notFound("Member", membershipId));
        if (!target.getTenant().getId().equals(tenantId)) {
            throw KonvoException.notFound("Member", membershipId);
        }
        if (target.getUser().getId().equals(actingUserId)) {
            throw KonvoException.badRequest("You can't remove yourself — ask another owner");
        }
        guardLastOwnerInvariant(tenantId, target, target.getRole(), /*removal*/ true);
        target.setStatus(MembershipStatus.disabled);
        memberships.save(target);
    }

    private void guardLastOwnerInvariant(UUID tenantId, TenantMembership target, Role newRole, boolean removal) {
        if (target.getRole() != Role.OWNER) {
            return;
        }
        boolean losingOwner = removal || newRole != Role.OWNER;
        if (!losingOwner) {
            return;
        }
        long activeOwners = memberships.countByTenantIdAndRoleAndStatus(tenantId, Role.OWNER, MembershipStatus.active);
        if (activeOwners <= 1) {
            throw KonvoException.badRequest("A workspace must keep at least one owner");
        }
    }

    private MemberResponse toResponse(TenantMembership m) {
        return new MemberResponse(
                m.getId(),
                m.getUser().getId(),
                m.getUser().getEmail(),
                m.getUser().getFullName(),
                m.getRole(),
                m.getStatus().name(),
                m.getCreatedAt(),
                m.getUser().getLastLoginAt());
    }
}
