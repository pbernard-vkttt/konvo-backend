package com.vulkantechtt.konvo.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock JdbcTemplate jdbc;

    @Test
    void overQuotaWhenAiRunsAtLimit() {
        UsageService usage = new UsageService(jdbc);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(row(100, 200, 50_000));

        Subscription sub = subscription(planLimits(500, 200, 50_000));
        boolean over = usage.isOverAiQuota(sub.getTenantId(), sub);

        assertThat(over).isTrue();
    }

    @Test
    void overQuotaWhenTokensExceed() {
        UsageService usage = new UsageService(jdbc);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(row(10, 50, 60_000));

        Subscription sub = subscription(planLimits(500, 200, 50_000));
        assertThat(usage.isOverAiQuota(sub.getTenantId(), sub)).isTrue();
    }

    @Test
    void underQuota() {
        UsageService usage = new UsageService(jdbc);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(row(10, 50, 1000));

        Subscription sub = subscription(planLimits(500, 200, 50_000));
        assertThat(usage.isOverAiQuota(sub.getTenantId(), sub)).isFalse();
    }

    @Test
    void snapshotReadsAllThreeCounters() {
        UsageService usage = new UsageService(jdbc);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(row(12, 34, 5678));

        UsageService.Snapshot s = usage.snapshot(UUID.randomUUID(), Instant.now(), Instant.now());
        assertThat(s.messagesSent()).isEqualTo(12);
        assertThat(s.aiRuns()).isEqualTo(34);
        assertThat(s.aiTokens()).isEqualTo(5678);
    }

    private static Map<String, Object> row(long msgs, long aiRuns, long aiTokens) {
        Map<String, Object> r = new HashMap<>();
        r.put("msgs", msgs);
        r.put("ai_runs", aiRuns);
        r.put("ai_tokens", aiTokens);
        return r;
    }

    private static Plan planLimits(int msgs, int aiRuns, int aiTokens) {
        Plan p = new Plan();
        p.setId("free");
        p.setName("Free");
        p.setMonthlyPriceUsd(BigDecimal.ZERO);
        p.setMsgMonthlyLimit(msgs);
        p.setAiRunsMonthlyLimit(aiRuns);
        p.setAiTokensMonthlyLimit(aiTokens);
        p.setKnowledgeSourcesLimit(5);
        p.setMembersLimit(3);
        return p;
    }

    private static Subscription subscription(Plan plan) {
        Subscription s = new Subscription();
        s.setTenantId(UUID.randomUUID());
        s.setPlan(plan);
        s.setStatus(SubscriptionStatus.active);
        s.setPeriodStart(Instant.now().minusSeconds(86400));
        s.setPeriodEnd(Instant.now().plusSeconds(86400));
        return s;
    }
}
