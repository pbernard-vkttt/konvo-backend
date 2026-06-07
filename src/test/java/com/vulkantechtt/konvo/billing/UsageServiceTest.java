package com.vulkantechtt.konvo.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock JdbcTemplate jdbc;

    @Test
    void overQuotaWhenAiRunsAtLimit() {
        UsageService usage = new UsageService(jdbc);

        Subscription sub = subscription(planLimits(500, 200, 50_000));
        boolean over = usage.isOverAiQuota(new UsageService.Snapshot(100, 200, 50_000, 3), sub.getPlan());

        assertThat(over).isTrue();
    }

    @Test
    void ignoresKnowledgeAndSeatUsageForAiQuota() {
        UsageService usage = new UsageService(jdbc);

        Subscription sub = subscription(planLimits(500, 200, 50_000));
        assertThat(usage.isOverAiQuota(new UsageService.Snapshot(10, 50, 60_000, 99), sub.getPlan())).isFalse();
    }

    @Test
    void underQuota() {
        UsageService usage = new UsageService(jdbc);

        Subscription sub = subscription(planLimits(500, 200, 50_000));
        assertThat(usage.isOverAiQuota(new UsageService.Snapshot(10, 50, 1000, 1), sub.getPlan())).isFalse();
    }

    @Test
    void snapshotReadsAllFourBillingCounters() {
        UsageService usage = new UsageService(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
                .thenReturn(12L, 34L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(5678L, 4L);

        UsageService.Snapshot s = usage.snapshot(UUID.randomUUID(), Instant.now(), Instant.now());
        assertThat(s.activeCustomers()).isEqualTo(12);
        assertThat(s.aiRuns()).isEqualTo(34);
        assertThat(s.knowledgeChars()).isEqualTo(5678);
        assertThat(s.members()).isEqualTo(4);
    }

    @Test
    void activeCustomerCounterUsesMessageActivityAndConversationCustomer() {
        UsageService usage = new UsageService(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
                .thenReturn(0L, 0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(0L, 0L);

        usage.snapshot(UUID.randomUUID(), Instant.now(), Instant.now());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForObject(sql.capture(), eq(Long.class), any(), any(), any());
        assertThat(sql.getAllValues().get(0))
                .contains("from messages")
                .contains("join conversations")
                .contains("count(distinct c.customer_id)")
                .contains("sent_at")
                .doesNotContain("created_at");
        assertThat(sql.getAllValues().get(1))
                .contains("from ai_runs")
                .contains("purpose = 'reply'")
                .contains("status = 'ok'");
    }

    @Test
    void snapshotBindsPeriodInstantsWithExplicitTimestampType() {
        UsageService usage = new UsageService(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
                .thenReturn(0L, 0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(0L, 0L);

        Instant start = Instant.parse("2026-05-01T00:00:00Z");
        Instant end = Instant.parse("2026-06-01T00:00:00Z");

        usage.snapshot(UUID.randomUUID(), start, end);

        ArgumentCaptor<Object> startParam = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> endParam = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, times(2)).queryForObject(anyString(), eq(Long.class),
                any(), startParam.capture(), endParam.capture());

        assertThat(startParam.getAllValues()).allSatisfy(value -> assertThat(value)
                .asInstanceOf(type(SqlParameterValue.class))
                .satisfies(param -> {
                    assertThat(param.getSqlType()).isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
                    assertThat(param.getValue()).isEqualTo(OffsetDateTime.parse("2026-05-01T00:00:00Z"));
                }));
        assertThat(endParam.getAllValues()).allSatisfy(value -> assertThat(value)
                .asInstanceOf(type(SqlParameterValue.class))
                .satisfies(param -> {
                    assertThat(param.getSqlType()).isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
                    assertThat(param.getValue()).isEqualTo(OffsetDateTime.parse("2026-06-01T00:00:00Z"));
                }));
    }

    private static Plan planLimits(int msgs, int aiRuns, int aiTokens) {
        Plan p = new Plan();
        p.setId("free");
        p.setName("Free");
        p.setMonthlyPriceUsd(BigDecimal.ZERO);
        p.setMonthlyPriceTtd(BigDecimal.ZERO);
        p.setMsgMonthlyLimit(msgs);
        p.setCustomerMonthlyLimit(msgs);
        p.setAiRunsMonthlyLimit(aiRuns);
        p.setAiTokensMonthlyLimit(aiTokens);
        p.setKnowledgeSourcesLimit(5);
        p.setKnowledgeCharsLimit(5000);
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
