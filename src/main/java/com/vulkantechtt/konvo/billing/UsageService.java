package com.vulkantechtt.konvo.billing;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads billing usage for the current subscription period directly from raw
 * tables. The app billing surface tracks the advertised self-serve plan
 * dimensions: monthly active customers, AI replies, knowledge-base characters,
 * and active seats. The older {@code usage_counters} roll-up remains an
 * analytics path for message/token history, not the billing contract.
 */
@Service
public class UsageService {

    private static final String SQL_ACTIVE_CUSTOMERS = """
            select count(distinct c.customer_id)
            from messages m
            join conversations c on c.id = m.conversation_id
            where m.tenant_id = ?
              and m.sent_at >= ?
              and m.sent_at < ?
            """;

    private static final String SQL_AI_REPLIES = """
            select count(*)
            from ai_runs
            where tenant_id = ?
              and purpose = 'reply'
              and status = 'ok'
              and created_at >= ?
              and created_at < ?
            """;

    private static final String SQL_KNOWLEDGE_CHARS = """
            select coalesce(sum(char_count), 0)
            from knowledge_sources
            where tenant_id = ?
            """;

    private static final String SQL_ACTIVE_MEMBERS = """
            select count(*)
            from tenant_memberships
            where tenant_id = ?
              and status = 'active'
            """;

    private final JdbcTemplate jdbc;

    public UsageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID tenantId, Instant periodStart, Instant periodEnd) {
        SqlParameterValue start = timestampParam(periodStart);
        SqlParameterValue end = timestampParam(periodEnd);
        long activeCustomers = asLong(jdbc.queryForObject(SQL_ACTIVE_CUSTOMERS, Long.class,
                tenantId, start, end));
        long aiReplies = asLong(jdbc.queryForObject(SQL_AI_REPLIES, Long.class,
                tenantId, start, end));
        long knowledgeChars = asLong(jdbc.queryForObject(SQL_KNOWLEDGE_CHARS, Long.class, tenantId));
        long members = asLong(jdbc.queryForObject(SQL_ACTIVE_MEMBERS, Long.class, tenantId));
        return new Snapshot(activeCustomers, aiReplies, knowledgeChars, members);
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
        return s.aiRuns() >= plan.getAiRunsMonthlyLimit();
    }

    private static long asLong(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static SqlParameterValue timestampParam(Instant value) {
        return new SqlParameterValue(
                Types.TIMESTAMP_WITH_TIMEZONE,
                OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
    }

    public record Snapshot(long activeCustomers, long aiRuns, long knowledgeChars, long members) {}
}
