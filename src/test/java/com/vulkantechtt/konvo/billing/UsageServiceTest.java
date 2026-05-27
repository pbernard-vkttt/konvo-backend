package com.vulkantechtt.konvo.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock JdbcTemplate jdbc;

    @Test
    void overQuotaWhenAiRunsAtLimit() {
        UsageService usage = new UsageService(jdbc);

        Subscription sub = subscription(planLimits(500, 200, 50_000));
        boolean over = usage.isOverAiQuota(new UsageService.Snapshot(100, 200, 50_000), sub.getPlan());

        assertThat(over).isTrue();
    }

    @Test
    void overQuotaWhenTokensExceed() {
        UsageService usage = new UsageService(jdbc);

        Subscription sub = subscription(planLimits(500, 200, 50_000));
        assertThat(usage.isOverAiQuota(new UsageService.Snapshot(10, 50, 60_000), sub.getPlan())).isTrue();
    }

    @Test
    void underQuota() {
        UsageService usage = new UsageService(jdbc);

        Subscription sub = subscription(planLimits(500, 200, 50_000));
        assertThat(usage.isOverAiQuota(new UsageService.Snapshot(10, 50, 1000), sub.getPlan())).isFalse();
    }

    @Test
    void snapshotReadsAllThreeCounters() {
        UsageService usage = new UsageService(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
                .thenReturn(12L, 34L, 5678L);

        UsageService.Snapshot s = usage.snapshot(UUID.randomUUID(), Instant.now(), Instant.now());
        assertThat(s.messagesSent()).isEqualTo(12);
        assertThat(s.aiRuns()).isEqualTo(34);
        assertThat(s.aiTokens()).isEqualTo(5678);
    }

    @Test
    void messageCounterUsesSentAtTimestamp() {
        UsageService usage = new UsageService(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
                .thenReturn(0L, 0L, 0L);

        usage.snapshot(UUID.randomUUID(), Instant.now(), Instant.now());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(3)).queryForObject(sql.capture(), eq(Long.class), any(), any(), any());
        assertThat(sql.getAllValues().get(0))
                .contains("from messages")
                .contains("sent_at")
                .doesNotContain("created_at");
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
