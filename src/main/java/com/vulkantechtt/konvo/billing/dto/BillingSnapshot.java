package com.vulkantechtt.konvo.billing.dto;

import com.vulkantechtt.konvo.billing.SubscriptionStatus;
import java.time.Instant;
import java.util.UUID;

public record BillingSnapshot(
        UUID subscriptionId,
        SubscriptionStatus status,
        Instant periodStart,
        Instant periodEnd,
        PlanSummary plan,
        UsageSummary usage,
        boolean overAiQuota) {

    public record UsageSummary(long activeCustomers, long aiRuns, long knowledgeChars, long members) {}
}
