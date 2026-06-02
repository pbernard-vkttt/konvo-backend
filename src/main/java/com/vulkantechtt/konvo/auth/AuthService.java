package com.vulkantechtt.konvo.auth;

import com.vulkantechtt.konvo.auth.dto.AcceptInvitationRequest;
import com.vulkantechtt.konvo.auth.dto.AuthSessionResponse;
import com.vulkantechtt.konvo.auth.dto.AuthSessionResponse.TenantSummary;
import com.vulkantechtt.konvo.auth.dto.AuthSessionResponse.UserSummary;
import com.vulkantechtt.konvo.auth.dto.InvitationPreviewResponse;
import com.vulkantechtt.konvo.auth.dto.LoginRequest;
import com.vulkantechtt.konvo.auth.dto.RegisterOwnerRequest;
import com.vulkantechtt.konvo.auth.dto.ResetPasswordRequest;
import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.common.SafeText;
import com.vulkantechtt.konvo.email.EmailSender;
import com.vulkantechtt.konvo.notifications.NotificationService;
import com.vulkantechtt.konvo.notifications.NotificationType;
import com.vulkantechtt.konvo.tenants.Tenant;
import com.vulkantechtt.konvo.tenants.TenantRepository;
import com.vulkantechtt.konvo.users.MembershipStatus;
import com.vulkantechtt.konvo.users.Role;
import com.vulkantechtt.konvo.users.TenantMembership;
import com.vulkantechtt.konvo.users.TenantMembershipRepository;
import com.vulkantechtt.konvo.users.User;
import com.vulkantechtt.konvo.users.UserRepository;
import com.vulkantechtt.konvo.users.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the auth use cases (login, refresh, register owner, password
 * reset, invitation accept). Returns the new access token + session summary;
 * the controller is responsible for writing the refresh cookie. Refresh-token
 * lifecycle lives in {@link RefreshTokenService}; JWT issuance in {@link JwtService}.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$");
    private static final Duration INVITATION_TTL = Duration.ofDays(7);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final UserInvitationRepository invitationRepository;
    private final PasswordResetTokenRepository passwordResetRepository;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final com.vulkantechtt.konvo.billing.SubscriptionService subscriptions;
    private final AuditService audit;
    private final NotificationService notifications;
    private final EmailSender email;
    private final String appBaseUrl;

    public AuthService(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            TenantMembershipRepository membershipRepository,
            UserInvitationRepository invitationRepository,
            PasswordResetTokenRepository passwordResetRepository,
            RefreshTokenService refreshTokens,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            com.vulkantechtt.konvo.billing.SubscriptionService subscriptions,
            AuditService audit,
            NotificationService notifications,
            EmailSender email,
            @org.springframework.beans.factory.annotation.Value("${konvo.public-base-url.app}") String appBaseUrl) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.subscriptions = subscriptions;
        this.audit = audit;
        this.notifications = notifications;
        this.email = email;
        this.appBaseUrl = appBaseUrl == null || appBaseUrl.isBlank()
                ? "http://localhost:4200"
                : appBaseUrl.replaceAll("/$", "");
    }

    @Transactional
    public Session login(LoginRequest req, HttpServletRequest http) {
        User user = userRepository.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new KonvoException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "invalid_credentials", "Email or password is incorrect"));

        if (user.getStatus() != UserStatus.active
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new KonvoException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "invalid_credentials", "Email or password is incorrect");
        }

        TenantMembership membership = resolveMembership(user, req.tenantSlug());

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return buildSession(user, membership, http);
    }

    @Transactional
    public Session refresh(String presentedRawCookie, HttpServletRequest http) {
        if (presentedRawCookie == null || presentedRawCookie.isBlank()) {
            throw new KonvoException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "invalid_refresh", "No refresh token");
        }
        RefreshTokenService.Issued issued;
        try {
            issued = refreshTokens.rotate(presentedRawCookie, http);
        } catch (RefreshTokenService.InvalidRefreshTokenException e) {
            throw new KonvoException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "invalid_refresh", e.getMessage());
        }

        User user = userRepository.findById(issued.entity().getUserId())
                .orElseThrow(() -> new KonvoException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "invalid_refresh", "User no longer exists"));

        TenantMembership membership = membershipRepository
                .findByTenantIdAndUserId(issued.entity().getTenantId(), user.getId())
                .filter(m -> m.getStatus() == MembershipStatus.active)
                .orElseThrow(() -> new KonvoException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "invalid_refresh", "Membership no longer active"));

        String access = jwtService.issueAccessToken(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                membership.getTenant().getId(),
                membership.getRole());

        return new Session(
                access,
                jwtService.accessTokenTtlSeconds(),
                issued.rawToken(),
                AuthService.toResponse(access, jwtService.accessTokenTtlSeconds(), user, membership));
    }

    @Transactional
    public void logout(String presentedRawCookie) {
        refreshTokens.revoke(presentedRawCookie);
    }

    @Transactional
    public Session registerOwner(RegisterOwnerRequest req, HttpServletRequest http) {
        String slug = req.workspaceSlug().toLowerCase();
        if (!SLUG.matcher(slug).matches()) {
            throw KonvoException.badRequest("Workspace slug must be lower-case letters, numbers, or dashes (3–80 chars)");
        }
        if (tenantRepository.existsBySlug(slug)) {
            throw KonvoException.conflict("That workspace name is taken");
        }
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw KonvoException.conflict("That email already has a Konvo account");
        }

        Tenant tenant = new Tenant();
        tenant.setName(req.workspaceName());
        tenant.setSlug(slug);
        tenant = tenantRepository.save(tenant);
        var sub = subscriptions.provisionFreePlan(tenant.getId());
        audit.recordSystem(tenant.getId(), AuditAction.SUBSCRIPTION_PROVISIONED, sub.getId(),
                "Workspace created on the Free plan", java.util.Map.of(
                        "plan", sub.getPlan().getId(),
                        "workspaceSlug", slug));

        User user = new User();
        user.setEmail(req.email().toLowerCase());
        user.setEmailVerified(false);
        user.setFullName(req.fullName());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);

        TenantMembership membership = new TenantMembership();
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setRole(Role.OWNER);
        membership.setStatus(MembershipStatus.active);
        membership = membershipRepository.save(membership);

        return buildSession(user, membership, http);
    }

    @Transactional
    public String beginPasswordReset(String email) {
        Optional<User> user = userRepository.findByEmailIgnoreCase(email);
        if (user.isEmpty() || user.get().getStatus() != UserStatus.active) {
            // Don't leak account existence; pretend success.
            return null;
        }
        String raw = TokenHasher.randomToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.get().getId());
        token.setTokenHash(TokenHasher.hash(raw));
        token.setExpiresAt(Instant.now().plus(PASSWORD_RESET_TTL));
        passwordResetRepository.save(token);
        log.info("Password reset token issued for user {}", user.get().getId());
        String resetLink = appBaseUrl + "/reset-password?token=" + raw;
        String fullName = SafeText.singleLine(user.get().getFullName(), "there", 160);
        this.email.send(new EmailSender.EmailMessage(
                user.get().getEmail(),
                fullName,
                "Reset your Konvo password",
                """
                Hi %s,

                Someone (hopefully you) asked to reset the password on your
                Konvo account. Click the link below to set a new one — the
                link is good for one hour.

                %s

                If you didn't ask for this, you can ignore this message.
                — The Konvo team
                """.formatted(fullName, resetLink)));
        return raw;
    }

    @Transactional
    public void completePasswordReset(ResetPasswordRequest req) {
        PasswordResetToken token = passwordResetRepository.findByTokenHash(TokenHasher.hash(req.token()))
                .orElseThrow(() -> KonvoException.badRequest("Invalid or expired reset link"));
        Instant now = Instant.now();
        if (!token.isUsable(now)) {
            throw KonvoException.badRequest("Invalid or expired reset link");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> KonvoException.badRequest("Invalid or expired reset link"));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        userRepository.save(user);
        token.setConsumedAt(now);
        passwordResetRepository.save(token);
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResponse previewInvitation(String rawToken) {
        UserInvitation invitation = invitationRepository.findByTokenHash(TokenHasher.hash(rawToken))
                .orElseThrow(() -> KonvoException.badRequest("Invitation not found"));
        if (!invitation.isPending(Instant.now())) {
            throw KonvoException.badRequest("Invitation expired or already accepted");
        }
        Tenant tenant = tenantRepository.findById(invitation.getTenantId())
                .orElseThrow(() -> KonvoException.badRequest("Workspace no longer exists"));
        return new InvitationPreviewResponse(invitation.getEmail(), tenant.getName(), invitation.getRole());
    }

    @Transactional
    public Session acceptInvitation(AcceptInvitationRequest req, HttpServletRequest http) {
        UserInvitation invitation = invitationRepository.findByTokenHash(TokenHasher.hash(req.token()))
                .orElseThrow(() -> KonvoException.badRequest("Invitation not found"));
        Instant now = Instant.now();
        if (!invitation.isPending(now)) {
            throw KonvoException.badRequest("Invitation expired or already accepted");
        }
        Tenant tenant = tenantRepository.findById(invitation.getTenantId())
                .orElseThrow(() -> KonvoException.badRequest("Workspace no longer exists"));

        User user = userRepository.findByEmailIgnoreCase(invitation.getEmail())
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail(invitation.getEmail().toLowerCase());
                    u.setFullName(req.fullName());
                    u.setPasswordHash(passwordEncoder.encode(req.password()));
                    u.setEmailVerified(true);
                    u.setLastLoginAt(now);
                    return userRepository.save(u);
                });

        TenantMembership membership = membershipRepository
                .findByTenantIdAndUserId(tenant.getId(), user.getId())
                .orElseGet(TenantMembership::new);
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setRole(invitation.getRole());
        membership.setStatus(MembershipStatus.active);
        membership = membershipRepository.save(membership);

        invitation.setAcceptedAt(now);
        invitationRepository.save(invitation);

        audit.recordSystem(tenant.getId(), AuditAction.MEMBER_JOINED, membership.getId(),
                user.getEmail() + " joined as " + membership.getRole().name().toLowerCase(),
                java.util.Map.of("email", user.getEmail(), "role", membership.getRole().name()));
        notifications.broadcastToOwnersAndAdmins(tenant.getId(),
                NotificationType.MEMBER_JOINED,
                "New teammate joined",
                user.getFullName() + " accepted the invite (" + membership.getRole().name().toLowerCase() + ")",
                "/app/settings/team");

        return buildSession(user, membership, http);
    }

    // -- helpers -------------------------------------------------------------

    private TenantMembership resolveMembership(User user, String tenantSlug) {
        List<TenantMembership> memberships = membershipRepository
                .findByUserIdAndStatus(user.getId(), MembershipStatus.active);
        if (memberships.isEmpty()) {
            throw KonvoException.forbidden("This account isn't a member of any active workspace");
        }
        if (tenantSlug == null || tenantSlug.isBlank()) {
            return memberships.get(0);
        }
        return memberships.stream()
                .filter(m -> tenantSlug.equalsIgnoreCase(m.getTenant().getSlug()))
                .findFirst()
                .orElseThrow(() -> KonvoException.forbidden("No membership for workspace " + tenantSlug));
    }

    private Session buildSession(User user, TenantMembership membership, HttpServletRequest http) {
        String access = jwtService.issueAccessToken(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                membership.getTenant().getId(),
                membership.getRole());
        RefreshTokenService.Issued refresh = refreshTokens.issue(user.getId(), membership.getTenant().getId(), http);
        return new Session(
                access,
                jwtService.accessTokenTtlSeconds(),
                refresh.rawToken(),
                toResponse(access, jwtService.accessTokenTtlSeconds(), user, membership));
    }

    private static AuthSessionResponse toResponse(String access, int ttlSeconds, User user, TenantMembership m) {
        return new AuthSessionResponse(
                access,
                ttlSeconds,
                new UserSummary(user.getId(), user.getEmail(), user.getFullName()),
                new TenantSummary(m.getTenant().getId(), m.getTenant().getName(), m.getTenant().getSlug(), m.getRole()));
    }

    public record Session(String accessToken, int accessTtlSeconds, String refreshTokenRaw, AuthSessionResponse body) {}
}
