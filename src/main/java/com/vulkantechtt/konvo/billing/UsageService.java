package com.vulkantechtt.konvo.billing;

import java.time.Instant;
import java.util.Map;
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

    private static final String SQL_USAGE = """
            select
              coalesce(sum(case when m.direction = 'outbound' then 1 else 0 end), 0) as msgs,
              coalesce((select count(*) from ai_runs r
                        where r.tenant_id = ? and r.created_at >= ? and r.created_at < ?), 0) as ai_runs,
              coalesce((select sum(prompt_tokens + completion_tokens) from ai_runs r
                        where r.tenant_id = ? and r.created_at >= ? and r.created_at < ?), 0) as ai_tokens
            from messages m
            where m.tenant_id = ? and m.created_at >= ? and m.created_at < ?
            """;

    private final JdbcTemplate jdbc;

    public UsageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID tenantId, Instant periodStart, Instant periodEnd) {
        Map<String, Object> row = jdbc.queryForMap(SQL_USAGE,
                tenantId, periodStart, periodEnd,
                tenantId, periodStart, periodEnd,
                tenantId, periodStart, periodEnd);
        long msgs    = asLong(row.get("msgs"));
        long aiRuns  = asLong(row.get("ai_runs"));
        long tokens  = asLong(row.get("ai_tokens"));
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
        Plan plan = sub.getPlan();
        Snapshot s = snapshot(tenantId, sub.getPeriodStart(), sub.getPeriodEnd());
        return s.aiRuns() >= plan.getAiRunsMonthlyLimit()
                || s.aiTokens() >= plan.getAiTokensMonthlyLimit();
    }

    private static long asLong(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    public record Snapshot(long messagesSent, long aiRuns, long aiTokens) {}
}
