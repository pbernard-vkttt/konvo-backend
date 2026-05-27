package com.vulkantechtt.konvo.billing;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads usage for the current subscription period directly from raw tables
 * (`messages`, `ai_runs`). When per-tenant volumes grow past the point where
 * scanning is cheap (low millions of rows), we'll fill the V006
 * {@code usage_counters} table via a daily roll-up and read from there.
 */
@Service
public class UsageService {

    private static final String SQL_MESSAGES_SENT = """
            select count(*)
            from messages
            where tenant_id = ?
              and direction = 'outbound'
              and sent_at >= ?
              and sent_at < ?
            """;

    private static final String SQL_AI_RUNS = """
            select count(*)
            from ai_runs
            where tenant_id = ?
              and created_at >= ?
              and created_at < ?
            """;

    private static final String SQL_AI_TOKENS = """
            select coalesce(sum(prompt_tokens + completion_tokens), 0)
            from ai_runs
            where tenant_id = ?
              and created_at >= ?
              and created_at < ?
            """;

    private final JdbcTemplate jdbc;

    public UsageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID tenantId, Instant periodStart, Instant periodEnd) {
        long msgs = asLong(jdbc.queryForObject(SQL_MESSAGES_SENT, Long.class,
                tenantId, periodStart, periodEnd));
        long aiRuns = asLong(jdbc.queryForObject(SQL_AI_RUNS, Long.class,
                tenantId, periodStart, periodEnd));
        long tokens = asLong(jdbc.queryForObject(SQL_AI_TOKENS, Long.class,
                tenantId, periodStart, periodEnd));
        return new Snapshot(msgs, aiRuns, tokens);
    }

    /**
     * True when the tenant is over any plan ceiling that matters for AI
     * replies. We only block AI auto-reply on quota — outbound text sends
     * the agent triggers still go through; the agent stays informed via the
     * banner on the billing page.
     */
    @Transactional(readOnly = true)
    public boolean isOverAiQuota(UUID tenantId, Subscription sub) {
        return isOverAiQuota(snapshot(tenantId, sub.getPeriodStart(), sub.getPeriodEnd()), sub.getPlan());
    }

    public boolean isOverAiQuota(Snapshot s, Plan plan) {
        return s.aiRuns() >= plan.getAiRunsMonthlyLimit()
                || s.aiTokens() >= plan.getAiTokensMonthlyLimit();
    }

    private static long asLong(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    public record Snapshot(long messagesSent, long aiRuns, long aiTokens) {}
}
