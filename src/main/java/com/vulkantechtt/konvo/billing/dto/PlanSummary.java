package com.vulkantechtt.konvo.billing.dto;

import java.math.BigDecimal;

public record PlanSummary(
        String id,
        String name,
        BigDecimal monthlyPriceTtd,
        BigDecimal monthlyPriceUsd,
        int customerMonthlyLimit,
        int aiRunsMonthlyLimit,
        int knowledgeCharsLimit,
        int membersLimit) {
}
