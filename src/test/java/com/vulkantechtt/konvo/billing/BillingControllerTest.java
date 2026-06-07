package com.vulkantechtt.konvo.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.billing.dto.ChangePlanRequest;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingControllerTest {

    @Mock SubscriptionService subscriptions;
    @Mock UsageService usage;

    @Test
    void meReturnsSnapshotWithoutCaching() {
        UUID tenantId = UUID.randomUUID();
        KonvoPrincipal principal = principal(tenantId, Role.OWNER);
        Subscription sub = subscription(tenantId, plan("pro", "Pro", 99));
        when(subscriptions.activeFor(tenantId)).thenReturn(sub);
        when(usage.snapshot(tenantId, sub.getPeriodStart(), sub.getPeriodEnd()))
                .thenReturn(new UsageService.Snapshot(10, 20, 30));

        var response = new BillingController(subscriptions, usage).me(principal);

        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().plan().id()).isEqualTo("pro");
        assertThat(response.getBody().usage().aiRuns()).isEqualTo(20);
    }

    @Test
    void plansReturnsPublicPlanSummaries() {
        when(subscriptions.publicPlans()).thenReturn(List.of(
                plan("free", "Free", 0),
                plan("pro", "Pro", 99)));

        var plans = new BillingController(subscriptions, usage).plans();

        assertThat(plans).extracting("id").containsExactly("free", "pro");
    }

    @Test
    void changePlanDelegatesAndReturnsFreshSnapshotWithoutCaching() {
        UUID tenantId = UUID.randomUUID();
        KonvoPrincipal principal = principal(tenantId, Role.ADMIN);
        Subscription sub = subscription(tenantId, plan("pro", "Pro", 99));
        when(subscriptions.activeFor(tenantId)).thenReturn(sub);
        when(usage.snapshot(tenantId, sub.getPeriodStart(), sub.getPeriodEnd()))
                .thenReturn(new UsageService.Snapshot(10, 20, 30));

        var response = new BillingController(subscriptions, usage)
                .changePlan(principal, new ChangePlanRequest("pro"));

        verify(subscriptions).changePlan(principal, "pro");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().plan().id()).isEqualTo("pro");
    }

    private static KonvoPrincipal principal(UUID tenantId, Role role) {
        return new KonvoPrincipal(UUID.randomUUID(), role.name().toLowerCase() + "@example.com",
                role.name(), tenantId, role);
    }

    private static Plan plan(String id, String name, int priceUsd) {
        Plan p = new Plan();
        p.setId(id);
        p.setName(name);
        p.setMonthlyPriceUsd(BigDecimal.valueOf(priceUsd));
        p.setMsgMonthlyLimit(1000);
        p.setAiRunsMonthlyLimit(500);
        p.setAiTokensMonthlyLimit(100_000);
        p.setKnowledgeSourcesLimit(10);
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
}
