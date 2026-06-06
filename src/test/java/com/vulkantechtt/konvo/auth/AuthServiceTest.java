package com.vulkantechtt.konvo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.billing.Plan;
import com.vulkantechtt.konvo.billing.Subscription;
import com.vulkantechtt.konvo.billing.SubscriptionService;
import com.vulkantechtt.konvo.auth.dto.LoginRequest;
import com.vulkantechtt.konvo.auth.dto.RegisterOwnerRequest;
import com.vulkantechtt.konvo.auth.dto.ResetPasswordRequest;
import com.vulkantechtt.konvo.email.EmailSender;
import com.vulkantechtt.konvo.email.EmailTemplateRenderer;
import com.vulkantechtt.konvo.notifications.NotificationService;
import com.vulkantechtt.konvo.tenants.Tenant;
import com.vulkantechtt.konvo.tenants.TenantRepository;
import com.vulkantechtt.konvo.users.MembershipStatus;
import com.vulkantechtt.konvo.users.Role;
import com.vulkantechtt.konvo.users.TenantMembership;
import com.vulkantechtt.konvo.users.TenantMembershipRepository;
import com.vulkantechtt.konvo.users.User;
import com.vulkantechtt.konvo.users.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock TenantRepository tenantRepository;
    @Mock TenantMembershipRepository membershipRepository;
    @Mock UserInvitationRepository invitationRepository;
    @Mock PasswordResetTokenRepository passwordResetRepository;
    @Mock EmailVerificationTokenRepository emailVerificationRepository;
    @Mock AuthIdentityRepository identityRepository;
    @Mock RefreshTokenService refreshTokens;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SubscriptionService subscriptions;
    @Mock AuditService audit;
    @Mock NotificationService notifications;
    @Mock EmailSender email;
    @Mock EmailTemplateRenderer templates;
    @Mock HttpServletRequest http;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                userRepository,
                tenantRepository,
                membershipRepository,
                invitationRepository,
                passwordResetRepository,
                emailVerificationRepository,
                identityRepository,
                refreshTokens,
                jwtService,
                passwordEncoder,
                subscriptions,
                audit,
                notifications,
                email,
                templates,
                "http://localhost:4200");

        lenient().when(jwtService.issueAccessToken(any(), anyString(), anyString(), any(), any()))
                .thenReturn("access-token");
        lenient().when(jwtService.accessTokenTtlSeconds()).thenReturn(900);
        lenient().when(templates.render(anyString(), any(Map.class))).thenReturn("<html></html>");
        lenient().when(refreshTokens.issue(any(), any(), any())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            UUID tenantId = invocation.getArgument(1);
            RefreshToken token = new RefreshToken();
            token.setId(UUID.randomUUID());
            token.setUserId(userId);
            token.setTenantId(tenantId);
            token.setExpiresAt(Instant.now().plusSeconds(3600));
            return new RefreshTokenService.Issued("refresh-token", token);
        });
    }

    @Test
    void loginWithGoogleCreatesOwnerWorkspaceForNewUserWithoutInvite() {
        when(identityRepository.findByProviderAndSubject(AuthIdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.empty());
        when(invitationRepository.findByEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull("alice@example.com"))
                .thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            if (tenant.getId() == null) {
                tenant.setId(UUID.randomUUID());
            }
            return tenant;
        });
        when(membershipRepository.findByUserIdAndStatus(any(), any())).thenReturn(List.of());
        when(membershipRepository.findByTenantIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(membershipRepository.save(any(TenantMembership.class))).thenAnswer(invocation -> {
            TenantMembership membership = invocation.getArgument(0);
            if (membership.getId() == null) {
                membership.setId(UUID.randomUUID());
            }
            return membership;
        });
        when(subscriptions.provisionFreePlan(any())).thenReturn(subscription("free"));

        AuthService.Session session = service.loginWithGoogle(
                new GoogleProfile("google-sub", "alice@example.com", "Alice Joe", "https://example.com/pic"),
                http);

        assertThat(session.body().user().email()).isEqualTo("alice@example.com");
        assertThat(session.body().user().emailVerified()).isTrue();
        assertThat(session.body().tenant().role()).isEqualTo(Role.OWNER);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().getName()).isEqualTo("Alice Joe's workspace");
        assertThat(tenantCaptor.getValue().getSlug()).startsWith("alice-joe");

        ArgumentCaptor<AuthIdentity> identityCaptor = ArgumentCaptor.forClass(AuthIdentity.class);
        verify(identityRepository).save(identityCaptor.capture());
        assertThat(identityCaptor.getValue().getProvider()).isEqualTo(AuthIdentityProvider.GOOGLE);
        assertThat(identityCaptor.getValue().getSubject()).isEqualTo("google-sub");
    }

    @Test
    void registerOwnerCreatesProvisionalWorkspaceFromOwnerIdentity() {
        when(userRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
        when(tenantRepository.existsBySlug("owner-name")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            if (tenant.getId() == null) {
                tenant.setId(UUID.randomUUID());
            }
            return tenant;
        });
        when(membershipRepository.findByTenantIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(membershipRepository.save(any(TenantMembership.class))).thenAnswer(invocation -> {
            TenantMembership membership = invocation.getArgument(0);
            if (membership.getId() == null) {
                membership.setId(UUID.randomUUID());
            }
            return membership;
        });
        when(passwordEncoder.encode("super-secret-password")).thenReturn("hashed-password");
        when(subscriptions.provisionFreePlan(any())).thenReturn(subscription("free"));

        AuthService.Session session = service.registerOwner(
                new RegisterOwnerRequest("Owner Name", "owner@example.com", "super-secret-password"),
                "old-refresh-token",
                http);

        assertThat(session.body().tenant().name()).isEqualTo("Owner Name's workspace");
        assertThat(session.body().tenant().slug()).isEqualTo("owner-name");
        assertThat(session.body().user().emailVerified()).isFalse();
        verify(refreshTokens).revoke("old-refresh-token");

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().getName()).isEqualTo("Owner Name's workspace");
        assertThat(tenantCaptor.getValue().getSlug()).isEqualTo("owner-name");
    }

    @Test
    void loginWithGoogleAcceptsSinglePendingInvitation() {
        UUID tenantId = UUID.randomUUID();
        UserInvitation invitation = new UserInvitation();
        invitation.setTenantId(tenantId);
        invitation.setEmail("agent@example.com");
        invitation.setRole(Role.ADMIN);
        invitation.setExpiresAt(Instant.now().plusSeconds(3600));

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Northside");
        tenant.setSlug("northside");

        when(identityRepository.findByProviderAndSubject(AuthIdentityProvider.GOOGLE, "google-agent"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("agent@example.com")).thenReturn(Optional.empty());
        when(invitationRepository.findByEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull("agent@example.com"))
                .thenReturn(List.of(invitation));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(membershipRepository.findByTenantIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(membershipRepository.save(any(TenantMembership.class))).thenAnswer(invocation -> {
            TenantMembership membership = invocation.getArgument(0);
            if (membership.getId() == null) {
                membership.setId(UUID.randomUUID());
            }
            return membership;
        });

        AuthService.Session session = service.loginWithGoogle(
                new GoogleProfile("google-agent", "agent@example.com", "Agent Smith", null),
                http);

        assertThat(session.body().tenant().role()).isEqualTo(Role.ADMIN);
        assertThat(invitation.getAcceptedAt()).isNotNull();
        verify(tenantRepository, never()).existsBySlug(anyString());
    }

    @Test
    void loginWithGoogleDoesNotOverwriteExistingPrimaryEmail() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("owner@old.test");
        user.setEmailVerified(false);
        user.setFullName("Owner Name");

        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthIdentityProvider.GOOGLE);
        identity.setSubject("google-owner");
        identity.setEmail("previous-google.test");

        TenantMembership membership = membership(
                "Original Workspace",
                "original-workspace",
                true,
                Instant.parse("2026-06-01T12:00:00Z"),
                Role.OWNER);
        membership.setUser(user);

        when(identityRepository.findByProviderAndSubject(AuthIdentityProvider.GOOGLE, "google-owner"))
                .thenReturn(Optional.of(identity));
        when(userRepository.save(user)).thenReturn(user);
        when(invitationRepository.findByEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull("google@personal.test"))
                .thenReturn(List.of());
        when(membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.active))
                .thenReturn(List.of(membership));
        when(identityRepository.save(identity)).thenReturn(identity);

        AuthService.Session session = service.loginWithGoogle(
                new GoogleProfile("google-owner", "google@personal.test", "Google Name", null),
                http);

        assertThat(user.getEmail()).isEqualTo("owner@old.test");
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(identity.getEmail()).isEqualTo("google@personal.test");
        assertThat(session.body().user().email()).isEqualTo("owner@old.test");
        assertThat(session.body().user().emailVerified()).isFalse();
    }

    @Test
    void resendVerificationEmailNoOpsForAlreadyVerifiedUser() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("owner@example.com");
        user.setFullName("Owner");
        user.setEmailVerified(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.resendVerificationEmail(new com.vulkantechtt.konvo.security.KonvoPrincipal(
                userId,
                "owner@example.com",
                "Owner",
                UUID.randomUUID(),
                Role.OWNER));

        verify(emailVerificationRepository, never()).save(any());
        verify(email, never()).send(any());
    }

    @Test
    void resendVerificationEmailSendsNewTokenForUnverifiedUser() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("owner@example.com");
        user.setFullName("Owner");
        user.setEmailVerified(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.resendVerificationEmail(new com.vulkantechtt.konvo.security.KonvoPrincipal(
                userId,
                "owner@example.com",
                "Owner",
                UUID.randomUUID(),
                Role.OWNER));

        verify(emailVerificationRepository).save(any(EmailVerificationToken.class));
        verify(email).send(any(EmailSender.EmailMessage.class));
    }

    @Test
    void completePasswordResetRevokesExistingRefreshTokens() {
        UUID userId = UUID.randomUUID();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setTokenHash(TokenHasher.hash("raw-reset"));
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        User user = new User();
        user.setId(userId);
        user.setEmail("owner@example.com");
        user.setFullName("Owner");

        when(passwordResetRepository.findByTokenHash(TokenHasher.hash("raw-reset")))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");

        service.completePasswordReset(new ResetPasswordRequest("raw-reset", "new-password-123"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.getConsumedAt()).isNotNull();
        verify(refreshTokens).revokeAllForUser(userId);
    }

    @Test
    void loginPrefersRequestedTenantSlug() {
        User user = loginUser();
        TenantMembership defaultMembership = membership(
                "Default Workspace",
                "default-workspace",
                true,
                Instant.parse("2026-06-03T12:00:00Z"),
                Role.OWNER);
        defaultMembership.setUser(user);
        TenantMembership requestedMembership = membership(
                "Requested Workspace",
                "requested-workspace",
                false,
                Instant.parse("2026-06-01T12:00:00Z"),
                Role.ADMIN);
        requestedMembership.setUser(user);

        when(userRepository.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hash")).thenReturn(true);
        when(membershipRepository.findByUserIdAndStatus(user.getId(), MembershipStatus.active))
                .thenReturn(List.of(defaultMembership, requestedMembership));
        when(userRepository.save(user)).thenReturn(user);

        AuthService.Session session = service.login(
                new LoginRequest("owner@example.com", "correct-password", "requested-workspace"),
                http);

        assertThat(session.body().tenant().slug()).isEqualTo("requested-workspace");
        assertThat(session.body().tenant().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void loginPrefersCompletedWorkspaceThenNewestMembership() {
        User user = loginUser();
        TenantMembership incompleteNewest = membership(
                "Incomplete",
                "incomplete",
                false,
                Instant.parse("2026-06-04T12:00:00Z"),
                Role.OWNER);
        incompleteNewest.setUser(user);
        TenantMembership completedOlder = membership(
                "Completed Older",
                "completed-older",
                true,
                Instant.parse("2026-06-01T12:00:00Z"),
                Role.ADMIN);
        completedOlder.setUser(user);
        TenantMembership completedNewest = membership(
                "Completed Newest",
                "completed-newest",
                true,
                Instant.parse("2026-06-03T12:00:00Z"),
                Role.OWNER);
        completedNewest.setUser(user);

        when(userRepository.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hash")).thenReturn(true);
        when(membershipRepository.findByUserIdAndStatus(user.getId(), MembershipStatus.active))
                .thenReturn(List.of(incompleteNewest, completedOlder, completedNewest));
        when(userRepository.save(user)).thenReturn(user);

        AuthService.Session session = service.login(
                new LoginRequest("owner@example.com", "correct-password", null),
                http);

        assertThat(session.body().tenant().slug()).isEqualTo("completed-newest");
    }

    @Test
    void sessionFromPrincipalReturnsAuthSessionResponse() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        user.setEmail("owner@example.com");
        user.setFullName("Owner Name");

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Owner Workspace");
        tenant.setSlug("owner-workspace");

        TenantMembership membership = new TenantMembership();
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setRole(Role.OWNER);
        membership.setStatus(MembershipStatus.active);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.of(membership));

        var response = service.sessionFromPrincipal(
                new com.vulkantechtt.konvo.security.KonvoPrincipal(
                        userId,
                        "owner@example.com",
                        "Owner Name",
                        tenantId,
                        Role.OWNER));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.user().fullName()).isEqualTo("Owner Name");
        assertThat(response.user().emailVerified()).isFalse();
        assertThat(response.tenant().slug()).isEqualTo("owner-workspace");
        assertThat(response.tenant().role()).isEqualTo(Role.OWNER);
    }

    @Test
    void acceptInvitationMarksExistingUserEmailVerified() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserInvitation invitation = new UserInvitation();
        invitation.setTenantId(tenantId);
        invitation.setEmail("member@example.com");
        invitation.setRole(Role.AGENT);
        invitation.setTokenHash(TokenHasher.hash("raw-invite"));
        invitation.setExpiresAt(Instant.now().plusSeconds(3600));

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Northside");
        tenant.setSlug("northside");

        User user = new User();
        user.setId(userId);
        user.setEmail("member@example.com");
        user.setFullName("Member Name");
        user.setEmailVerified(false);

        when(invitationRepository.findByTokenHash(TokenHasher.hash("raw-invite")))
                .thenReturn(Optional.of(invitation));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(membershipRepository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(TenantMembership.class))).thenAnswer(invocation -> {
            TenantMembership membership = invocation.getArgument(0);
            if (membership.getId() == null) {
                membership.setId(UUID.randomUUID());
            }
            return membership;
        });

        AuthService.Session session = service.acceptInvitation(
                new com.vulkantechtt.konvo.auth.dto.AcceptInvitationRequest("raw-invite", "Member Name", "password-123"),
                http);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(session.body().user().emailVerified()).isTrue();
        verify(userRepository).save(user);
    }

    private static Subscription subscription(String planId) {
        Plan plan = new Plan();
        plan.setId(planId);
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setPlan(plan);
        return subscription;
    }

    private static User loginUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("owner@example.com");
        user.setFullName("Owner Name");
        user.setPasswordHash("hash");
        user.setStatus(com.vulkantechtt.konvo.users.UserStatus.active);
        return user;
    }

    private static TenantMembership membership(
            String tenantName,
            String tenantSlug,
            boolean onboardingCompleted,
            Instant createdAt,
            Role role) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(tenantName);
        tenant.setSlug(tenantSlug);
        tenant.setOnboardingCompleted(onboardingCompleted);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("member@example.com");
        user.setFullName("Member Name");

        TenantMembership membership = new TenantMembership();
        membership.setId(UUID.randomUUID());
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setRole(role);
        membership.setStatus(MembershipStatus.active);
        ReflectionTestUtils.setField(membership, "createdAt", createdAt);
        return membership;
    }
}
