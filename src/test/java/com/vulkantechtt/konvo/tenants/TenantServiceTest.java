package com.vulkantechtt.konvo.tenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.knowledge.WorkspaceKnowledgeRollupService;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.dto.UpdateTenantSettingsRequest;
import com.vulkantechtt.konvo.tenants.dto.UpdateOnboardingWorkspaceRequest;
import com.vulkantechtt.konvo.users.MembershipStatus;
import com.vulkantechtt.konvo.users.Role;
import com.vulkantechtt.konvo.users.TenantMembership;
import com.vulkantechtt.konvo.users.TenantMembershipRepository;
import com.vulkantechtt.konvo.users.User;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantRepository tenants;
    @Mock TenantMembershipRepository memberships;
    @Mock AuditService audit;
    @Mock WorkspaceKnowledgeRollupService workspaceKnowledgeRollup;

    private TenantService service;

    @BeforeEach
    void setUp() {
        service = new TenantService(tenants, memberships, audit, workspaceKnowledgeRollup);
    }

    @Test
    void meReturnsWorkspaceSettings() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 18);
        tenant.setWorkingHours("Mon-Fri, 9 am to 5 pm");
        tenant.setBusinessOfferings("Lunch menu and catering trays");
        tenant.setCustomSystemPrompt("Keep answers short.");
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        var response = service.me(tenantId);

        assertThat(response.customerMemoryMessageLimit()).isEqualTo(18);
        assertThat(response.workingHours()).isEqualTo("Mon-Fri, 9 am to 5 pm");
        assertThat(response.businessOfferings()).isEqualTo("Lunch menu and catering trays");
        assertThat(response.customSystemPrompt()).isEqualTo("Keep answers short.");
    }

    @Test
    void updateSettingsPersistsAuditsAndSyncsWorkspaceKnowledge() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenants.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateSettings(
                principal(tenantId),
                new UpdateTenantSettingsRequest(
                        24,
                        " Mon-Fri, 9 am to 5 pm ",
                        "Breakfast menu\nLunch specials",
                        " Keep answers short and mention curbside pickup. "));

        assertThat(response.customerMemoryMessageLimit()).isEqualTo(24);
        assertThat(response.workingHours()).isEqualTo("Mon-Fri, 9 am to 5 pm");
        assertThat(response.businessOfferings()).isEqualTo("Breakfast menu\nLunch specials");
        assertThat(response.customSystemPrompt()).isEqualTo("Keep answers short and mention curbside pickup.");
        verify(workspaceKnowledgeRollup).sync(any(KonvoPrincipal.class), org.mockito.ArgumentMatchers.same(tenant));
        verify(audit).record(
                any(KonvoPrincipal.class),
                org.mockito.ArgumentMatchers.eq(AuditAction.WORKSPACE_SETTINGS_UPDATED),
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq("Updated workspace settings"),
                org.mockito.ArgumentMatchers.<Map<String, ?>>any());
    }

    @Test
    void updateSettingsSkipsAuditWhenLimitIsUnchanged() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenants.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateSettings(principal(tenantId), new UpdateTenantSettingsRequest(12, "", "", ""));

        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void updateSettingsPreservesProfileFieldsWhenTheyAreOmitted() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        tenant.setWorkingHours("Mon-Fri, 9 am to 5 pm");
        tenant.setBusinessOfferings("Lunch menu");
        tenant.setCustomSystemPrompt("Answer politely.");
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenants.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateSettings(
                principal(tenantId),
                new UpdateTenantSettingsRequest(20, null, null, null));

        assertThat(response.workingHours()).isEqualTo("Mon-Fri, 9 am to 5 pm");
        assertThat(response.businessOfferings()).isEqualTo("Lunch menu");
        assertThat(response.customSystemPrompt()).isEqualTo("Answer politely.");
    }

    @Test
    void updateSettingsDoesNotMutateWorkspaceIdentity() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        tenant.setName("Original Workspace");
        tenant.setSlug("original-workspace");
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenants.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateSettings(
                principal(tenantId),
                new UpdateTenantSettingsRequest(
                        30,
                        "Mon-Fri, 9 am to 5 pm",
                        "Lunch menu",
                        ""));

        assertThat(response.name()).isEqualTo("Original Workspace");
        assertThat(response.slug()).isEqualTo("original-workspace");
    }

    @Test
    void draftOwnerCanUpdateOnboardingWorkspaceDetails() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        tenant.setOnboardingCompleted(false);
        when(memberships.findByTenantIdAndUserId(tenantId, actorId))
                .thenReturn(Optional.of(membership(tenant, actorId, Role.OWNER, MembershipStatus.active)));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenants.findBySlug("northside-hq")).thenReturn(Optional.empty());
        when(tenants.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateOnboardingWorkspace(
                principal(tenantId, actorId, Role.OWNER),
                new UpdateOnboardingWorkspaceRequest(
                        "Northside HQ",
                        "northside-hq",
                        "retail",
                        " Mon-Fri, 9 am to 5 pm ",
                        "Breakfast menu\nLunch specials",
                        " Keep answers short. "));

        assertThat(response.name()).isEqualTo("Northside HQ");
        assertThat(response.slug()).isEqualTo("northside-hq");
        assertThat(response.industry()).isEqualTo("retail");
        assertThat(response.workingHours()).isEqualTo("Mon-Fri, 9 am to 5 pm");
        assertThat(response.businessOfferings()).isEqualTo("Breakfast menu\nLunch specials");
        assertThat(response.customSystemPrompt()).isEqualTo("Keep answers short.");
        assertThat(response.customerMemoryMessageLimit()).isEqualTo(12);
        verify(workspaceKnowledgeRollup).sync(any(KonvoPrincipal.class), org.mockito.ArgumentMatchers.same(tenant));
        verify(audit).record(
                any(KonvoPrincipal.class),
                org.mockito.ArgumentMatchers.eq(AuditAction.WORKSPACE_SETTINGS_UPDATED),
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq("Updated onboarding workspace details"),
                org.mockito.ArgumentMatchers.<Map<String, ?>>any());
    }

    @Test
    void completedTenantCannotBeRenamedFromOnboardingEndpoint() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        tenant.setOnboardingCompleted(true);
        when(memberships.findByTenantIdAndUserId(tenantId, actorId))
                .thenReturn(Optional.of(membership(tenant, actorId, Role.OWNER, MembershipStatus.active)));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> service.updateOnboardingWorkspace(
                principal(tenantId, actorId, Role.OWNER),
                new UpdateOnboardingWorkspaceRequest(
                        "Northside HQ",
                        "northside-hq",
                        "retail",
                        "",
                        "",
                        "")))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("already complete");

        verify(tenants, never()).save(any());
    }

    @Test
    void nonOwnerMembershipCannotUpdateOnboardingWorkspaceDetails() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        when(memberships.findByTenantIdAndUserId(tenantId, actorId))
                .thenReturn(Optional.of(membership(tenant, actorId, Role.ADMIN, MembershipStatus.active)));

        assertThatThrownBy(() -> service.updateOnboardingWorkspace(
                principal(tenantId, actorId, Role.ADMIN),
                new UpdateOnboardingWorkspaceRequest("Northside", "northside", "retail", "", "", "")))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("Only workspace owners");

        verify(tenants, never()).findById(tenantId);
        verify(tenants, never()).save(any());
    }

    @Test
    void inactiveMembershipCannotUpdateOnboardingWorkspaceDetails() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        when(memberships.findByTenantIdAndUserId(tenantId, actorId))
                .thenReturn(Optional.of(membership(tenant, actorId, Role.OWNER, MembershipStatus.disabled)));

        assertThatThrownBy(() -> service.updateOnboardingWorkspace(
                principal(tenantId, actorId, Role.OWNER),
                new UpdateOnboardingWorkspaceRequest("Northside", "northside", "retail", "", "", "")))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("session no longer matches");

        verify(tenants, never()).findById(tenantId);
        verify(tenants, never()).save(any());
    }

    @Test
    void onboardingWorkspaceSlugCollisionReturnsConflict() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        Tenant other = tenant(UUID.randomUUID(), 12);
        other.setSlug("northside-hq");
        when(memberships.findByTenantIdAndUserId(tenantId, actorId))
                .thenReturn(Optional.of(membership(tenant, actorId, Role.OWNER, MembershipStatus.active)));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenants.findBySlug("northside-hq")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updateOnboardingWorkspace(
                principal(tenantId, actorId, Role.OWNER),
                new UpdateOnboardingWorkspaceRequest("Northside HQ", "northside-hq", "retail", "", "", "")))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("slug is taken");

        verify(tenants, never()).save(any());
    }

    @Test
    void completeOnboardingRequiresActiveOwnerMembership() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        when(memberships.findByTenantIdAndUserId(tenantId, actorId))
                .thenReturn(Optional.of(membership(tenant, actorId, Role.ADMIN, MembershipStatus.active)));

        assertThatThrownBy(() -> service.completeOnboarding(principal(tenantId, actorId, Role.ADMIN)))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("Only workspace owners");

        verify(tenants, never()).findById(tenantId);
        verify(tenants, never()).save(any());
    }

    @Test
    void completeOnboardingMarksDraftCompleteForActiveOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        when(memberships.findByTenantIdAndUserId(tenantId, actorId))
                .thenReturn(Optional.of(membership(tenant, actorId, Role.OWNER, MembershipStatus.active)));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenants.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.completeOnboarding(principal(tenantId, actorId, Role.OWNER));

        assertThat(response.onboardingCompleted()).isTrue();
    }

    private static Tenant tenant(UUID id, int memoryLimit) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setName("Acme Co");
        t.setSlug("acme");
        t.setCustomerMemoryMessageLimit(memoryLimit);
        return t;
    }

    private static KonvoPrincipal principal(UUID tenantId) {
        return new KonvoPrincipal(UUID.randomUUID(), "owner@example.com", "Owner", tenantId, Role.OWNER);
    }

    private static KonvoPrincipal principal(UUID tenantId, UUID userId, Role role) {
        return new KonvoPrincipal(userId, "owner@example.com", "Owner", tenantId, role);
    }

    private static TenantMembership membership(Tenant tenant, UUID userId, Role role, MembershipStatus status) {
        TenantMembership membership = new TenantMembership();
        membership.setId(UUID.randomUUID());
        membership.setTenant(tenant);
        User user = new User();
        user.setId(userId);
        user.setEmail("owner@example.com");
        user.setFullName("Owner");
        membership.setUser(user);
        membership.setRole(role);
        membership.setStatus(status);
        return membership;
    }
}
