package com.vulkantechtt.konvo.usage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly aggregator for {@code usage_counters}. For every tenant, computes
 * the current calendar-month counts of outbound messages, AI runs, and AI
 * tokens, then upserts a single row keyed by {@code (tenant_id, period_key)}.
 *
 * The billing snapshot still computes from raw tables for the current
 * subscription period (which doesn't align with calendar months), but
 * dashboards and the Insights page can read pre-aggregated counts cheaply
 * once this has run at least once.
 *
 * Cron: 02:15 daily UTC — chosen to be well after midnight rollover and
 * outside the typical local-evening traffic peak. Single-instance only;
 * a multi-pod deploy needs ShedLock or similar.
 */
@Component
public class UsageRollupJob {

    private static final Logger log = LoggerFactory.getLogger(UsageRollupJob.class);

    private static final String UPSERT_SQL = """
            insert into usage_counters (tenant_id, period_key, messages_sent, ai_runs, ai_tokens, updated_at)
            select
              t.id,
              ?,
              coalesce((select count(*) from messages m
                        where m.tenant_id = t.id
                          and m.direction = 'outbound'
                          and m.created_at >= ? and m.created_at < ?), 0),
              coalesce((select count(*) from ai_runs r
                        where r.tenant_id = t.id
                          and r.created_at >= ? and r.created_at < ?), 0),
              coalesce((select sum(prompt_tokens + completion_tokens) from ai_runs r
                        where r.tenant_id = t.id
                          and r.created_at >= ? and r.created_at < ?), 0),
              now()
            from tenants t
            on conflict (tenant_id, period_key) do update set
              messages_sent = excluded.messages_sent,
              ai_runs       = excluded.ai_runs,
              ai_tokens     = excluded.ai_tokens,
              updated_at    = now()
            """;

    private final JdbcTemplate jdbc;

    public UsageRollupJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 15 2 * * *", zone = "UTC")
    @Transactional
    public void rollupCurrentMonth() {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        rollupMonth(current);
    }

    /** Exposed for tests + manual triggering. */
    @Transactional
    public int rollupMonth(YearMonth month) {
        Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        String periodKey = String.format("%04d-%02d", month.getYear(), month.getMonthValue());
        int rows = jdbc.update(UPSERT_SQL,
                periodKey,
                start, end,
                start, end,
                start, end);
        log.info("usage_counters rollup for {} touched {} tenant rows", periodKey, rows);
        return rows;
    }

    public static String periodKeyFor(LocalDate date) {
        return String.format("%04d-%02d", date.getYear(), date.getMonthValue());
    }
}
