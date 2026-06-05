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
import com.vulkantechtt.konvo.email.EmailTemplateRenderer;
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
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the auth use cases (login, refresh, register owner, password
 * reset, email verification, invitation accept). Returns the new access token
 * + session summary; the controller is responsible for writing the refresh
 * cookie. Refresh-token lifecycle lives in {@link RefreshTokenService}; JWT
 * issuance in {@link JwtService}.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Duration INVITATION_TTL = Duration.ofDays(7);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);

    private static final DateTimeFormatter RESET_EXPIRY_FMT =
            DateTimeFormatter.ofPattern("h:mm a · d MMMM yyyy", Locale.ENGLISH)
                    .withZone(ZoneId.of("America/Port_of_Spain"));

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final UserInvitationRepository invitationRepository;
    private final PasswordResetTokenRepository passwordResetRepository;
    private final EmailVerificationTokenRepository emailVerificationRepository;
    private final AuthIdentityRepository identityRepository;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final com.vulkantechtt.konvo.billing.SubscriptionService subscriptions;
    private final AuditService audit;
    private final NotificationService notifications;
    private final EmailSender email;
    private final EmailTemplateRenderer templates;
    private final String appBaseUrl;

    public AuthService(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            TenantMembershipRepository membershipRepository,
            UserInvitationRepository invitationRepository,
            PasswordResetTokenRepository passwordResetRepository,
            EmailVerificationTokenRepository emailVerificationRepository,
            AuthIdentityRepository identityRepository,
            RefreshTokenService refreshTokens,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            com.vulkantechtt.konvo.billing.SubscriptionService subscriptions,
            AuditService audit,
            NotificationService notifications,
            EmailSender email,
            EmailTemplateRenderer templates,
            @org.springframework.beans.factory.annotation.Value("${konvo.public-base-url.app}") String appBaseUrl) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.identityRepository = identityRepository;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.subscriptions = subscriptions;
        this.audit = audit;
        this.notifications = notifications;
        this.email = email;
        this.templates = templates;
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
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw KonvoException.conflict("That email already has a Konvo account");
        }

        Tenant tenant = createOwnerWorkspace(
                defaultWorkspaceName(req.fullName()),
                uniqueWorkspaceSlug(req.fullName(), req.email()));

        User user = new User();
        user.setEmail(req.email().toLowerCase());
        user.setEmailVerified(false);
        user.setFullName(req.fullName());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);

        TenantMembership membership = activateMembership(user, tenant, Role.OWNER);

        sendWelcomeEmail(user);
        sendVerificationEmail(user);

        return buildSession(user, membership, http);
    }

    @Transactional
    public Session loginWithGoogle(GoogleProfile profile, HttpServletRequest http) {
        String email = SafeText.singleLine(profile.email(), "", 254).toLowerCase();
        String subject = SafeText.singleLine(profile.subject(), "", 255);
        String fullName = SafeText.singleLine(profile.fullName(), email, 160);
        if (email.isBlank() || subject.isBlank()) {
            throw new KonvoException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "google_profile_invalid",
                    "Google didn't return the account details Konvelo needs");
        }

        Instant now = Instant.now();
        boolean createdUser = false;
        User user = identityRepository.findByProviderAndSubject(AuthIdentityProvider.GOOGLE, subject)
                .map(AuthIdentity::getUser)
                .orElseGet(() -> userRepository.findByEmailIgnoreCase(email).orElse(null));

        if (user == null) {
            createdUser = true;
            user = new User();
            user.setEmail(email);
            user.setEmailVerified(true);
            user.setFullName(fullName);
            user.setLastLoginAt(now);
            user = userRepository.save(user);
        } else {
            if (user.getStatus() != UserStatus.active) {
                throw new KonvoException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "account_disabled",
                        "This account can't access Konvelo right now");
            }
            if (user.getEmail() != null && email.equalsIgnoreCase(user.getEmail())) {
                user.setEmailVerified(true);
            }
            if (user.getFullName() == null || user.getFullName().isBlank()) {
                user.setFullName(fullName);
            }
            user.setLastLoginAt(now);
            user = userRepository.save(user);
        }

        linkGoogleIdentity(user, subject, email);

        Optional<UserInvitation> invitation = resolvePendingInvitation(email);
        boolean createdOwnerWorkspace = invitation.isEmpty()
                && membershipRepository.findByUserIdAndStatus(user.getId(), MembershipStatus.active).isEmpty();
        User sessionUser = user;
        TenantMembership membership = invitation
                .map(inv -> acceptGoogleInvitation(inv, sessionUser))
                .orElseGet(() -> provisionOwnerIfNeeded(sessionUser, fullName));

        if (createdOwnerWorkspace || createdUser) {
            sendWelcomeEmail(user);
        }

        return buildSession(user, membership, http);
    }

    @Transactional
    public String beginPasswordReset(String emailAddress) {
        Optional<User> user = userRepository.findByEmailIgnoreCase(emailAddress);
        if (user.isEmpty() || user.get().getStatus() != UserStatus.active) {
            return null;
        }
        String raw = TokenHasher.randomToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.get().getId());
        token.setTokenHash(TokenHasher.hash(raw));
        Instant expiresAt = Instant.now().plus(PASSWORD_RESET_TTL);
        token.setExpiresAt(expiresAt);
        passwordResetRepository.save(token);
        log.info("Password reset token issued for user {}", user.get().getId());

        String resetUrl = appBaseUrl + "/reset-password?token=" + raw;
        String firstName = firstNameOf(user.get().getFullName());
        String expiresAtFormatted = RESET_EXPIRY_FMT.format(expiresAt);

        String html = templates.render("password-reset", Map.of(
                "firstName", firstName,
                "resetUrl", resetUrl,
                "expiresAt", expiresAtFormatted,
                "appUrl", appBaseUrl));

        this.email.send(EmailSender.EmailMessage.html(
                user.get().getEmail(),
                SafeText.singleLine(user.get().getFullName(), "there", 160),
                "Reset your Konvelo password",
                html));
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
        refreshTokens.revokeAllForUser(user.getId());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = emailVerificationRepository
                .findByTokenHash(TokenHasher.hash(rawToken))
                .orElseThrow(() -> KonvoException.badRequest("Invalid or expired verification link"));
        Instant now = Instant.now();
        if (!token.isUsable(now)) {
            throw KonvoException.badRequest("Invalid or expired verification link");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> KonvoException.badRequest("Invalid or expired verification link"));
        user.setEmailVerified(true);
        userRepository.save(user);
        token.setConsumedAt(now);
        emailVerificationRepository.save(token);
        log.info("Email verified for user {}", user.getId());
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
                Map.of("email", user.getEmail(), "role", membership.getRole().name()));
        notifications.broadcastToOwnersAndAdmins(tenant.getId(),
                NotificationType.MEMBER_JOINED,
                "New teammate joined",
                user.getFullName() + " accepted the invite (" + membership.getRole().name().toLowerCase() + ")",
                "/app/settings/team");

        return buildSession(user, membership, http);
    }

    @Transactional(readOnly = true)
    public AuthSessionResponse sessionFromPrincipal(com.vulkantechtt.konvo.security.KonvoPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new KonvoException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "unauthenticated",
                        "Authentication required"));
        TenantMembership membership = membershipRepository
                .findByTenantIdAndUserId(principal.tenantId(), principal.userId())
                .filter(m -> m.getStatus() == MembershipStatus.active)
                .orElseThrow(() -> new KonvoException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "unauthenticated",
                        "Authentication required"));
        String access = jwtService.issueAccessToken(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                membership.getTenant().getId(),
                membership.getRole());
        return toResponse(access, jwtService.accessTokenTtlSeconds(), user, membership);
    }

    // -- private helpers -------------------------------------------------------

    private Optional<UserInvitation> resolvePendingInvitation(String email) {
        Instant now = Instant.now();
        List<UserInvitation> pending = invitationRepository
                .findByEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(email)
                .stream()
                .filter(inv -> inv.isPending(now))
                .sorted(Comparator.comparing(
                        UserInvitation::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        if (pending.size() > 1) {
            throw KonvoException.conflict(
                    "This email has more than one pending workspace invitation. Use the invitation email you were sent.");
        }
        return pending.stream().findFirst();
    }

    private TenantMembership acceptGoogleInvitation(UserInvitation invitation, User user) {
        Instant now = Instant.now();
        Tenant tenant = tenantRepository.findById(invitation.getTenantId())
                .orElseThrow(() -> KonvoException.badRequest("Workspace no longer exists"));
        TenantMembership membership = activateMembership(user, tenant, invitation.getRole());
        invitation.setAcceptedAt(now);
        invitationRepository.save(invitation);
        audit.recordSystem(tenant.getId(), AuditAction.MEMBER_JOINED, membership.getId(),
                user.getEmail() + " joined as " + membership.getRole().name().toLowerCase(),
                Map.of("email", user.getEmail(), "role", membership.getRole().name()));
        notifications.broadcastToOwnersAndAdmins(tenant.getId(),
                NotificationType.MEMBER_JOINED,
                "New teammate joined",
                user.getFullName() + " accepted the invite (" + membership.getRole().name().toLowerCase() + ")",
                "/app/settings/team");
        return membership;
    }

    private TenantMembership provisionOwnerIfNeeded(User user, String fullName) {
        List<TenantMembership> memberships = membershipRepository.findByUserIdAndStatus(user.getId(), MembershipStatus.active);
        if (!memberships.isEmpty()) {
            return choosePreferredMembership(memberships);
        }
        Tenant tenant = createOwnerWorkspace(defaultWorkspaceName(fullName), uniqueWorkspaceSlug(fullName, user.getEmail()));
        return activateMembership(user, tenant, Role.OWNER);
    }

    private void linkGoogleIdentity(User user, String subject, String email) {
        AuthIdentity identity = identityRepository.findByProviderAndSubject(AuthIdentityProvider.GOOGLE, subject)
                .orElseGet(AuthIdentity::new);
        identity.setUser(user);
        identity.setProvider(AuthIdentityProvider.GOOGLE);
        identity.setSubject(subject);
        identity.setEmail(email);
        identityRepository.save(identity);
    }

    private Tenant createOwnerWorkspace(String workspaceName, String workspaceSlug) {
        Tenant tenant = new Tenant();
        tenant.setName(SafeText.singleLine(workspaceName, "Workspace", 120));
        tenant.setSlug(workspaceSlug);
        tenant = tenantRepository.save(tenant);
        var sub = subscriptions.provisionFreePlan(tenant.getId());
        audit.recordSystem(tenant.getId(), AuditAction.SUBSCRIPTION_PROVISIONED, sub.getId(),
                "Workspace created on the Free plan", Map.of(
                        "plan", sub.getPlan().getId(),
                        "workspaceSlug", workspaceSlug));
        return tenant;
    }

    private TenantMembership activateMembership(User user, Tenant tenant, Role role) {
        TenantMembership membership = membershipRepository
                .findByTenantIdAndUserId(tenant.getId(), user.getId())
                .orElseGet(TenantMembership::new);
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setRole(role);
        membership.setStatus(MembershipStatus.active);
        return membershipRepository.save(membership);
    }

    private String defaultWorkspaceName(String fullName) {
        String owner = SafeText.singleLine(fullName, "Your", 80);
        return SafeText.singleLine(owner + "'s workspace", "Your workspace", 120);
    }

    private String uniqueWorkspaceSlug(String fullName, String email) {
        String base = slugify(fullName);
        if (base.length() < 3) {
            int at = email == null ? -1 : email.indexOf('@');
            base = slugify(at > 0 ? email.substring(0, at) : email);
        }
        if (base.length() < 3) {
            base = "workspace";
        }
        base = trimSlug(base, 80);
        String candidate = base;
        int suffix = 2;
        while (tenantRepository.existsBySlug(candidate)) {
            String suffixText = "-" + suffix++;
            candidate = trimSlug(base, 80 - suffixText.length()) + suffixText;
        }
        return candidate;
    }

    private static String slugify(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        String slug = NON_SLUG.matcher(normalized).replaceAll("-");
        return trimSlug(slug, 80);
    }

    private static String trimSlug(String value, int maxLength) {
        String slug = value == null ? "" : value;
        if (slug.length() > maxLength) {
            slug = slug.substring(0, maxLength);
        }
        return slug.replaceAll("^-+|-+$", "");
    }

    private void sendWelcomeEmail(User user) {
        try {
            String firstName = firstNameOf(user.getFullName());
            String html = templates.render("welcome", Map.of(
                    "firstName", firstName,
                    "appUrl", appBaseUrl + "/app"));
            email.send(EmailSender.EmailMessage.html(
                    user.getEmail(),
                    SafeText.singleLine(user.getFullName(), "there", 160),
                    "Welcome to Konvelo — you're all set!",
                    html));
        } catch (Exception e) {
            log.warn("Welcome email failed for user {}: {}", user.getId(), e.getMessage());
        }
    }

    private void sendVerificationEmail(User user) {
        try {
            String raw = TokenHasher.randomToken();
            EmailVerificationToken token = new EmailVerificationToken();
            token.setUserId(user.getId());
            token.setTokenHash(TokenHasher.hash(raw));
            token.setExpiresAt(Instant.now().plus(EMAIL_VERIFICATION_TTL));
            emailVerificationRepository.save(token);

            String verifyUrl = appBaseUrl + "/verify-email?token=" + raw;
            String html = templates.render("email-verification", Map.of(
                    "verifyUrl", verifyUrl,
                    "appUrl", appBaseUrl));
            email.send(EmailSender.EmailMessage.html(
                    user.getEmail(),
                    SafeText.singleLine(user.getFullName(), "there", 160),
                    "Verify your email address",
                    html));
        } catch (Exception e) {
            log.warn("Verification email failed for user {}: {}", user.getId(), e.getMessage());
        }
    }

    private TenantMembership resolveMembership(User user, String tenantSlug) {
        List<TenantMembership> memberships = membershipRepository
                .findByUserIdAndStatus(user.getId(), MembershipStatus.active);
        if (memberships.isEmpty()) {
            throw KonvoException.forbidden("This account isn't a member of any active workspace");
        }
        if (tenantSlug == null || tenantSlug.isBlank()) {
            return choosePreferredMembership(memberships);
        }
        return memberships.stream()
                .filter(m -> tenantSlug.equalsIgnoreCase(m.getTenant().getSlug()))
                .findFirst()
                .orElseThrow(() -> KonvoException.forbidden("No membership for workspace " + tenantSlug));
    }

    private static TenantMembership choosePreferredMembership(List<TenantMembership> memberships) {
        return memberships.stream()
                .max(Comparator
                        .comparing((TenantMembership m) -> m.getTenant().isOnboardingCompleted())
                        .thenComparing(TenantMembership::getCreatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(TenantMembership::getId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseThrow(() -> KonvoException.forbidden(
                        "This account isn't a member of any active workspace"));
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
                new TenantSummary(m.getTenant().getId(), m.getTenant().getName(), m.getTenant().getSlug(), m.getRole(), m.getTenant().isOnboardingCompleted()));
    }

    static String firstNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return "there";
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    public record Session(String accessToken, int accessTtlSeconds, String refreshTokenRaw, AuthSessionResponse body) {}
}
