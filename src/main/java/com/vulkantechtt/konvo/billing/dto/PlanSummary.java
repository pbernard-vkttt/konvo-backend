package com.vulkantechtt.konvo.billing.dto;

import java.math.BigDecimal;

public record PlanSummary(
        String id,
        String name,
        BigDecimal monthlyPriceUsd,
        int msgMonthlyLimit,
        int aiRunsMonthlyLimit,
        int aiTokensMonthlyLimit,
        int knowledgeSourcesLimit,
        int membersLimit) {
}
