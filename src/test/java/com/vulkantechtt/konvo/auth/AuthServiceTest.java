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
        assertThat(response.tenant().slug()).isEqualTo("owner-workspace");
        assertThat(response.tenant().role()).isEqualTo(Role.OWNER);
    }

    private static Subscription subscription(String planId) {
        Plan plan = new Plan();
        plan.setId(planId);
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setPlan(plan);
        return subscription;
    }
}
