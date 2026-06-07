package com.vulkantechtt.konvo.billing;

import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Sort;
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
    private final TenantRepository tenants;
    private final AuditService audit;

    public SubscriptionService(
            PlanRepository plans,
            SubscriptionRepository subscriptions,
            TenantRepository tenants,
            AuditService audit) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.tenants = tenants;
        this.audit = audit;
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

    @Transactional(readOnly = true)
    public List<Plan> publicPlans() {
        return plans.findAll(Sort.by(Sort.Direction.ASC, "monthlyPriceUsd")).stream()
                .filter(Plan::isPublic)
                .toList();
    }

    @Transactional
    public Subscription changePlan(KonvoPrincipal actor, String planId) {
        String normalizedPlanId = normalizePlanId(planId);
        Subscription sub = activeFor(actor.tenantId());
        Plan current = sub.getPlan();
        Plan target = plans.findById(normalizedPlanId)
                .filter(Plan::isPublic)
                .orElseThrow(() -> KonvoException.badRequest("Plan is not available: " + normalizedPlanId));

        if (Objects.equals(current.getId(), target.getId())) {
            syncTenantPlan(actor.tenantId(), target.getId());
            return sub;
        }

        sub.setPlan(target);
        Subscription saved = subscriptions.save(sub);
        syncTenantPlan(actor.tenantId(), target.getId());
        audit.record(actor, AuditAction.SUBSCRIPTION_PLAN_CHANGED, saved.getId(),
                "Changed subscription plan from " + current.getName() + " to " + target.getName(),
                Map.of("from", current.getId(), "to", target.getId()));
        return saved;
    }

    private static String normalizePlanId(String planId) {
        if (planId == null || planId.isBlank()) {
            throw KonvoException.badRequest("Plan is required");
        }
        return planId.strip().toLowerCase();
    }

    private void syncTenantPlan(UUID tenantId, String planId) {
        tenants.findById(tenantId).ifPresent(tenant -> {
            if (!Objects.equals(tenant.getPlan(), planId)) {
                tenant.setPlan(planId);
                tenants.save(tenant);
            }
        });
    }
}
