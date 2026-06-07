package com.vulkantechtt.konvo.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.Tenant;
import com.vulkantechtt.konvo.tenants.TenantRepository;
import com.vulkantechtt.konvo.users.Role;
import java.math.BigDecimal;
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
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock PlanRepository plans;
    @Mock SubscriptionRepository subscriptions;
    @Mock TenantRepository tenants;
    @Mock AuditService audit;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(plans, subscriptions, tenants, audit);
    }

    @Test
    void publicPlansReturnsPublicCataloguePlans() {
        Plan enterprise = plan("enterprise", "Enterprise", 0);
        enterprise.setPublic(false);
        List<Plan> catalogue = List.of(
                plan("free", "Free", 0),
                plan("starter", "Starter", 44),
                plan("growth", "Growth", 148),
                plan("business", "Business", 325),
                enterprise);
        when(plans.findAll(any(Sort.class))).thenReturn(catalogue);

        assertThat(service.publicPlans()).extracting(Plan::getId)
                .containsExactly("free", "starter", "growth", "business");
    }

    @Test
    void changePlanUpdatesActiveSubscriptionSyncsTenantPlanAndAudits() {
        UUID tenantId = UUID.randomUUID();
        KonvoPrincipal actor = principal(tenantId);
        Plan free = plan("free", "Free", 0);
        Plan business = plan("business", "Business", 325);
        Subscription sub = subscription(tenantId, free);
        Tenant tenant = tenant(tenantId, "free");
        when(subscriptions.findFirstByTenantIdAndStatus(tenantId, SubscriptionStatus.active))
                .thenReturn(Optional.of(sub));
        when(plans.findById("business")).thenReturn(Optional.of(business));
        when(subscriptions.save(sub)).thenReturn(sub);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        Subscription changed = service.changePlan(actor, " BUSINESS ");

        assertThat(changed.getPlan().getId()).isEqualTo("business");
        assertThat(tenant.getPlan()).isEqualTo("business");
        verify(subscriptions).save(sub);
        verify(tenants).save(tenant);

        ArgumentCaptor<Map<String, ?>> diff = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq(actor), eq(AuditAction.SUBSCRIPTION_PLAN_CHANGED), eq(sub.getId()),
                eq("Changed subscription plan from Free to Business"), diff.capture());
        assertThat(diff.getValue().get("from")).isEqualTo("free");
        assertThat(diff.getValue().get("to")).isEqualTo("business");
    }

    @Test
    void samePlanRequestSyncsTenantPlanWithoutSavingSubscriptionOrAuditing() {
        UUID tenantId = UUID.randomUUID();
        Plan business = plan("business", "Business", 325);
        Subscription sub = subscription(tenantId, business);
        Tenant tenant = tenant(tenantId, "free");
        when(subscriptions.findFirstByTenantIdAndStatus(tenantId, SubscriptionStatus.active))
                .thenReturn(Optional.of(sub));
        when(plans.findById("business")).thenReturn(Optional.of(business));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        Subscription unchanged = service.changePlan(principal(tenantId), "business");

        assertThat(unchanged).isSameAs(sub);
        assertThat(tenant.getPlan()).isEqualTo("business");
        verify(tenants).save(tenant);
        verify(subscriptions, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsLegacyProPlanWhenUnavailable() {
        UUID tenantId = UUID.randomUUID();
        Subscription sub = subscription(tenantId, plan("free", "Free", 0));
        when(subscriptions.findFirstByTenantIdAndStatus(tenantId, SubscriptionStatus.active))
                .thenReturn(Optional.of(sub));
        when(plans.findById("pro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePlan(principal(tenantId), "pro"))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("Plan is not available");

        verify(subscriptions, never()).save(any());
        verify(tenants, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsUnknownPlan() {
        UUID tenantId = UUID.randomUUID();
        Subscription sub = subscription(tenantId, plan("free", "Free", 0));
        when(subscriptions.findFirstByTenantIdAndStatus(tenantId, SubscriptionStatus.active))
                .thenReturn(Optional.of(sub));
        when(plans.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePlan(principal(tenantId), "missing"))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("Plan is not available");

        verify(subscriptions, never()).save(any());
        verify(tenants, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsNonPublicPlan() {
        UUID tenantId = UUID.randomUUID();
        Plan hidden = plan("enterprise", "Enterprise", 0);
        hidden.setPublic(false);
        Subscription sub = subscription(tenantId, plan("free", "Free", 0));
        when(subscriptions.findFirstByTenantIdAndStatus(tenantId, SubscriptionStatus.active))
                .thenReturn(Optional.of(sub));
        when(plans.findById("enterprise")).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.changePlan(principal(tenantId), "enterprise"))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("Plan is not available");

        verify(subscriptions, never()).save(any());
        verify(tenants, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    private static KonvoPrincipal principal(UUID tenantId) {
        return new KonvoPrincipal(UUID.randomUUID(), "owner@example.com", "Owner", tenantId, Role.OWNER);
    }

    private static Plan plan(String id, String name, int priceUsd) {
        Plan p = new Plan();
        p.setId(id);
        p.setName(name);
        p.setMonthlyPriceUsd(BigDecimal.valueOf(priceUsd));
        p.setMonthlyPriceTtd(BigDecimal.valueOf(priceUsd * 6L));
        p.setMsgMonthlyLimit(1000);
        p.setCustomerMonthlyLimit(500);
        p.setAiRunsMonthlyLimit(500);
        p.setAiTokensMonthlyLimit(100_000);
        p.setKnowledgeSourcesLimit(10);
        p.setKnowledgeCharsLimit(25_000);
        p.setMembersLimit(5);
        p.setPublic(true);
        return p;
    }

    private static Subscription subscription(UUID tenantId, Plan plan) {
        Subscription s = new Subscription();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setPlan(plan);
        s.setStatus(SubscriptionStatus.active);
        s.setPeriodStart(Instant.parse("2026-06-01T00:00:00Z"));
        s.setPeriodEnd(Instant.parse("2026-07-01T00:00:00Z"));
        return s;
    }

    private static Tenant tenant(UUID tenantId, String planId) {
        Tenant t = new Tenant();
        t.setId(tenantId);
        t.setName("Acme");
        t.setSlug("acme");
        t.setPlan(planId);
        return t;
    }
}
