package com.vulkantechtt.konvo.billing;

import com.vulkantechtt.konvo.billing.dto.BillingSnapshot;
import com.vulkantechtt.konvo.billing.dto.PlanSummary;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
        Subscription sub = subscriptions.activeFor(principal.tenantId());
        UsageService.Snapshot s = usage.snapshot(principal.tenantId(),
                sub.getPeriodStart(), sub.getPeriodEnd());
        Plan plan = sub.getPlan();
        BillingSnapshot body = new BillingSnapshot(
                sub.getId(),
                sub.getStatus(),
                sub.getPeriodStart(),
                sub.getPeriodEnd(),
                new PlanSummary(
                        plan.getId(),
                        plan.getName(),
                        plan.getMonthlyPriceUsd(),
                        plan.getMsgMonthlyLimit(),
                        plan.getAiRunsMonthlyLimit(),
                        plan.getAiTokensMonthlyLimit(),
                        plan.getKnowledgeSourcesLimit(),
                        plan.getMembersLimit()),
                new BillingSnapshot.UsageSummary(s.messagesSent(), s.aiRuns(), s.aiTokens()),
                usage.isOverAiQuota(s, plan));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePrivate())
                .body(body);
    }
}
