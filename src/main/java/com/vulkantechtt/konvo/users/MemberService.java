package com.vulkantechtt.konvo.users;

import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.auth.TokenHasher;
import com.vulkantechtt.konvo.auth.UserInvitation;
import com.vulkantechtt.konvo.auth.UserInvitationRepository;
import com.vulkantechtt.konvo.auth.AuthService;
import com.vulkantechtt.konvo.auth.EmailVerificationGuard;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.common.SafeText;
import com.vulkantechtt.konvo.email.EmailSender;
import com.vulkantechtt.konvo.email.EmailTemplateRenderer;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.Tenant;
import com.vulkantechtt.konvo.tenants.TenantRepository;
import com.vulkantechtt.konvo.users.dto.InvitationResponse;
import com.vulkantechtt.konvo.users.dto.InviteMemberRequest;
import com.vulkantechtt.konvo.users.dto.MemberResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

    private static final DateTimeFormatter INVITE_EXPIRY_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
                    .withZone(ZoneId.of("America/Port_of_Spain"));

    private final TenantMembershipRepository memberships;
    private final UserInvitationRepository invitations;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final AuditService audit;
    private final EmailSender email;
    private final EmailTemplateRenderer templates;
    private final EmailVerificationGuard emailVerification;
    private final String appBaseUrl;
    private final boolean devTokenInResponse;

    public MemberService(
            TenantMembershipRepository memberships,
            UserInvitationRepository invitations,
            UserRepository users,
            TenantRepository tenants,
            AuditService audit,
            EmailSender email,
            EmailTemplateRenderer templates,
            EmailVerificationGuard emailVerification,
            @org.springframework.beans.factory.annotation.Value("${konvo.public-base-url.app}") String appBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${konvo.auth.dev-token-in-response:false}") boolean devTokenInResponse) {
        this.memberships = memberships;
        this.invitations = invitations;
        this.users = users;
        this.tenants = tenants;
        this.audit = audit;
        this.email = email;
        this.templates = templates;
        this.emailVerification = emailVerification;
        this.devTokenInResponse = devTokenInResponse;
        this.appBaseUrl = appBaseUrl == null || appBaseUrl.isBlank()
                ? "http://localhost:4200"
                : appBaseUrl.replaceAll("/$", "");
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(KonvoPrincipal actor) {
        TenantMembership actorMembership = memberships
                .findByTenantIdAndUserId(actor.tenantId(), actor.userId())
                .filter(m -> m.getStatus() == MembershipStatus.active)
                .orElseThrow(MemberService::sessionWorkspaceMismatch);

        List<MemberResponse> rows = memberships.findByTenantIdAndStatus(actor.tenantId(), MembershipStatus.active).stream()
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
        boolean actorIsPresent = rows.stream()
                .anyMatch(row -> row.membershipId().equals(actorMembership.getId())
                        && row.userId().equals(actor.userId()));
        if (!actorIsPresent) {
            throw sessionWorkspaceMismatch();
        }
        return rows;
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
    public InvitationResponse invite(KonvoPrincipal actor, InviteMemberRequest req) {
        emailVerification.requireVerified(actor);
        guardCanManageTeam(actor);
        guardCanAssignRole(actor, req.role());
        UUID tenantId = actor.tenantId();
        UUID invitedByUserId = actor.userId();
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
        audit.record(actor, AuditAction.MEMBER_INVITED, saved.getId(),
                "Invited " + email + " as " + req.role().name().toLowerCase(),
                java.util.Map.of("email", email, "role", req.role().name()));
        sendInvitationEmail(actor, email, req.role().name().toLowerCase(), raw, tenantId);
        return new InvitationResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getRole(),
                saved.getExpiresAt(),
                saved.getCreatedAt(),
                devTokenInResponse ? raw : null);
    }

    @Transactional
    public void revokeInvitation(KonvoPrincipal actor, UUID invitationId) {
        emailVerification.requireVerified(actor);
        guardCanManageTeam(actor);
        UUID tenantId = actor.tenantId();
        UserInvitation inv = invitations.findById(invitationId)
                .orElseThrow(() -> KonvoException.notFound("Invitation", invitationId));
        if (!inv.getTenantId().equals(tenantId)) {
            throw KonvoException.notFound("Invitation", invitationId);
        }
        inv.setRevokedAt(Instant.now());
        invitations.save(inv);
        audit.record(actor, AuditAction.MEMBER_INVITATION_REVOKED, inv.getId(),
                "Revoked invite for " + inv.getEmail(),
                java.util.Map.of("email", inv.getEmail()));
    }

    @Transactional
    public MemberResponse changeRole(KonvoPrincipal actor, UUID membershipId, Role newRole) {
        emailVerification.requireVerified(actor);
        guardCanManageTeam(actor);
        UUID tenantId = actor.tenantId();
        UUID actingUserId = actor.userId();
        TenantMembership target = memberships.findById(membershipId)
                .orElseThrow(() -> KonvoException.notFound("Member", membershipId));
        if (!target.getTenant().getId().equals(tenantId)) {
            throw KonvoException.notFound("Member", membershipId);
        }
        guardCanManageRole(actor, target.getRole());
        guardCanAssignRole(actor, newRole);
        guardLastOwnerInvariant(tenantId, target, newRole, /*removal*/ false);
        if (target.getUser().getId().equals(actingUserId) && target.getRole() == Role.OWNER && newRole != Role.OWNER) {
            // Same guard, friendlier message for the "demoting yourself" case.
            throw KonvoException.badRequest("You can't change your own owner role — promote another owner first");
        }
        Role oldRole = target.getRole();
        target.setRole(newRole);
        TenantMembership saved = memberships.save(target);
        audit.record(actor, AuditAction.MEMBER_ROLE_CHANGED, saved.getId(),
                saved.getUser().getEmail() + " role changed from "
                        + oldRole.name().toLowerCase() + " to " + newRole.name().toLowerCase(),
                java.util.Map.of("email", saved.getUser().getEmail(),
                        "from", oldRole.name(), "to", newRole.name()));
        return toResponse(saved);
    }

    @Transactional
    public void remove(KonvoPrincipal actor, UUID membershipId) {
        emailVerification.requireVerified(actor);
        guardCanManageTeam(actor);
        UUID tenantId = actor.tenantId();
        UUID actingUserId = actor.userId();
        TenantMembership target = memberships.findById(membershipId)
                .orElseThrow(() -> KonvoException.notFound("Member", membershipId));
        if (!target.getTenant().getId().equals(tenantId)) {
            throw KonvoException.notFound("Member", membershipId);
        }
        if (target.getUser().getId().equals(actingUserId)) {
            throw KonvoException.badRequest("You can't remove yourself — ask another owner");
        }
        guardCanManageRole(actor, target.getRole());
        guardLastOwnerInvariant(tenantId, target, target.getRole(), /*removal*/ true);
        target.setStatus(MembershipStatus.disabled);
        memberships.save(target);
        audit.record(actor, AuditAction.MEMBER_REMOVED, target.getId(),
                "Removed " + target.getUser().getEmail() + " from workspace",
                java.util.Map.of("email", target.getUser().getEmail(),
                        "role", target.getRole().name()));
    }

    private static void guardCanManageTeam(KonvoPrincipal actor) {
        if (actor.role() != Role.OWNER && actor.role() != Role.ADMIN) {
            throw KonvoException.forbidden("Only owners and admins can manage team members");
        }
    }

    private static void guardCanManageRole(KonvoPrincipal actor, Role targetRole) {
        if (targetRole.isAbove(actor.role())) {
            throw KonvoException.forbidden("You can't manage a role above yours");
        }
    }

    private static void guardCanAssignRole(KonvoPrincipal actor, Role newRole) {
        if (newRole.isAbove(actor.role())) {
            throw KonvoException.forbidden("You can't assign a role above yours");
        }
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

    private void sendInvitationEmail(KonvoPrincipal actor, String toEmail, String role,
                                     String rawToken, UUID tenantId) {
        String tenantName = tenants.findById(tenantId)
                .map(Tenant::getName)
                .orElse("your workspace");
        tenantName = SafeText.singleLine(tenantName, "your workspace", 160);
        String inviterName = SafeText.singleLine(actor.fullName(), "A teammate", 160);
        String inviterEmail = SafeText.singleLine(actor.email(), "someone on your team", 160);
        String inviterInitials = initialsOf(inviterName);
        String inviteUrl = appBaseUrl + "/invitations/" + rawToken;
        String declineUrl = appBaseUrl + "/invitations/decline/" + rawToken;
        String expiresAt = INVITE_EXPIRY_FMT.format(Instant.now().plus(INVITATION_TTL));

        String html = templates.render("team-invitation", Map.of(
                "inviterName", inviterName,
                "inviterInitials", inviterInitials,
                "inviterEmail", inviterEmail,
                "tenantName", tenantName,
                "role", role,
                "inviteUrl", inviteUrl,
                "declineUrl", declineUrl,
                "expiresAt", expiresAt,
                "appUrl", appBaseUrl));

        this.email.send(EmailSender.EmailMessage.html(
                toEmail,
                toEmail,
                inviterName + " invited you to join " + tenantName,
                html));
    }

    private static String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
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

    private static KonvoException sessionWorkspaceMismatch() {
        return new KonvoException(
                HttpStatus.UNAUTHORIZED,
                "session_workspace_mismatch",
                "Your session no longer matches this workspace. Sign in again.");
    }
}
