package com.vulkantechtt.konvo.billing;

import com.vulkantechtt.konvo.common.KonvoException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscription lifecycle. M6 only ships free-tier provisioning + read; plan
 * changes + payments arrive with M8+. The provisioning hook is invoked from
 * {@code AuthService.registerOwner} so every new tenant lands on the free
 * plan automatically.
 */
@Service
public class SubscriptionService {

    public static final String DEFAULT_PLAN_ID = "free";
    private static final Duration PERIOD_LENGTH = Duration.ofDays(30);

    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;

    public SubscriptionService(PlanRepository plans, SubscriptionRepository subscriptions) {
        this.plans = plans;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public Subscription provisionFreePlan(UUID tenantId) {
        Plan plan = plans.findById(DEFAULT_PLAN_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Default plan '" + DEFAULT_PLAN_ID + "' missing — V006 must run before this"));
        Subscription sub = new Subscription();
        sub.setTenantId(tenantId);
        sub.setPlan(plan);
        sub.setStatus(SubscriptionStatus.active);
        Instant now = Instant.now();
        sub.setPeriodStart(now);
        sub.setPeriodEnd(now.plus(PERIOD_LENGTH));
        return subscriptions.save(sub);
    }

    @Transactional(readOnly = true)
    public Subscription activeFor(UUID tenantId) {
        return subscriptions.findFirstByTenantIdAndStatus(tenantId, SubscriptionStatus.active)
                .orElseThrow(() -> KonvoException.notFound("Subscription", tenantId));
    }
}
