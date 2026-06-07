package com.vulkantechtt.konvo.billing;

import com.vulkantechtt.konvo.billing.dto.BillingSnapshot;
import com.vulkantechtt.konvo.billing.dto.ChangePlanRequest;
import com.vulkantechtt.konvo.billing.dto.PlanSummary;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
@PreAuthorize("isAuthenticated()")
public class BillingController {

    private final SubscriptionService subscriptions;
    private final UsageService usage;

    public BillingController(SubscriptionService subscriptions, UsageService usage) {
        this.subscriptions = subscriptions;
        this.usage = usage;
    }

    @GetMapping("/me")
    public ResponseEntity<BillingSnapshot> me(@AuthenticationPrincipal KonvoPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(snapshot(principal.tenantId()));
    }

    @GetMapping("/plans")
    public List<PlanSummary> plans() {
        return subscriptions.publicPlans().stream()
                .map(BillingController::toSummary)
                .toList();
    }

    @PatchMapping("/me/plan")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<BillingSnapshot> changePlan(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @Valid @RequestBody ChangePlanRequest req) {
        subscriptions.changePlan(principal, req.planId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(snapshot(principal.tenantId()));
    }

    private BillingSnapshot snapshot(UUID tenantId) {
        Subscription sub = subscriptions.activeFor(tenantId);
        UsageService.Snapshot s = usage.snapshot(tenantId,
                sub.getPeriodStart(), sub.getPeriodEnd());
        Plan plan = sub.getPlan();
        return new BillingSnapshot(
                sub.getId(),
                sub.getStatus(),
                sub.getPeriodStart(),
                sub.getPeriodEnd(),
                toSummary(plan),
                new BillingSnapshot.UsageSummary(
                        s.activeCustomers(),
                        s.aiRuns(),
                        s.knowledgeChars(),
                        s.members()),
                usage.isOverAiQuota(s, plan));
    }

    private static PlanSummary toSummary(Plan plan) {
        return new PlanSummary(
                plan.getId(),
                plan.getName(),
                plan.getMonthlyPriceTtd(),
                plan.getMonthlyPriceUsd(),
                plan.getCustomerMonthlyLimit(),
                plan.getAiRunsMonthlyLimit(),
                plan.getKnowledgeCharsLimit(),
                plan.getMembersLimit());
    }
}
