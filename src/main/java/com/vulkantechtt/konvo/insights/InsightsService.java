package com.vulkantechtt.konvo.insights;

import com.vulkantechtt.konvo.insights.dto.InsightsSnapshot;
import com.vulkantechtt.konvo.insights.dto.InsightsSnapshot.AiCostPoint;
import com.vulkantechtt.konvo.insights.dto.InsightsSnapshot.ChannelBreakdownRow;
import com.vulkantechtt.konvo.insights.dto.InsightsSnapshot.Kpis;
import com.vulkantechtt.konvo.insights.dto.InsightsSnapshot.ResponseTimeBucket;
import com.vulkantechtt.konvo.insights.dto.InsightsSnapshot.TopCustomerRow;
import com.vulkantechtt.konvo.insights.dto.InsightsSnapshot.TrafficPoint;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * On-demand analytics aggregations read straight from {@code messages},
 * {@code ai_runs}, {@code conversations}, {@code customers}, {@code channels}.
 * Plenty fast for early scale; the {@link com.vulkantechtt.konvo.usage.UsageRollupJob}
 * pre-aggregate handles the case where this gets pricey for a single tenant.
 */
@Service
public class InsightsService {

    private final JdbcTemplate jdbc;

    public InsightsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public InsightsSnapshot snapshot(UUID tenantId, int rangeDays) {
        if (rangeDays < 1) rangeDays = 1;
        if (rangeDays > 365) rangeDays = 365;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDay = today.minusDays(rangeDays - 1L);
        Instant start = startDay.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        SqlParameterValue startParam = timestampParam(start);
        SqlParameterValue endParam = timestampParam(end);

        return new InsightsSnapshot(
                rangeDays,
                start,
                end,
                kpis(tenantId, startParam, endParam),
                trafficByDay(tenantId, startParam, endParam, startDay, rangeDays),
                aiCostByDay(tenantId, startParam, endParam, startDay, rangeDays),
                topCustomers(tenantId, startParam, endParam),
                responseTimeBuckets(tenantId, startParam, endParam),
                channelBreakdown(tenantId, startParam, endParam));
    }

    private Kpis kpis(UUID tenantId, SqlParameterValue start, SqlParameterValue end) {
        Long conversations = jdbc.queryForObject("""
                select count(distinct conversation_id) from messages
                where tenant_id = ? and created_at >= ? and created_at < ?
                """, Long.class, tenantId, start, end);

        Map<String, Object> aiRow = jdbc.queryForMap("""
                select coalesce(count(*),0) as runs,
                       coalesce(sum(case when status='ok' then 1 else 0 end),0) as ok_runs,
                       coalesce(sum(cost_estimate_usd),0) as cost,
                       coalesce(avg(latency_ms),0) as avg_latency
                from ai_runs
                where tenant_id = ? and created_at >= ? and created_at < ?
                """, tenantId, start, end);

        long runs = asLong(aiRow.get("runs"));
        long okRuns = asLong(aiRow.get("ok_runs"));
        double cost = asDouble(aiRow.get("cost"));
        long avgLatency = asLong(aiRow.get("avg_latency"));

        return new Kpis(
                conversations == null ? 0 : conversations,
                runs,
                okRuns,
                cost,
                avgLatency);
    }

    private List<TrafficPoint> trafficByDay(UUID tenantId, SqlParameterValue start, SqlParameterValue end,
                                             LocalDate startDay, int rangeDays) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select date_trunc('day', created_at at time zone 'UTC')::date as day,
                       sum(case when direction='inbound'  then 1 else 0 end) as inbound,
                       sum(case when direction='outbound' then 1 else 0 end) as outbound
                from messages
                where tenant_id = ? and created_at >= ? and created_at < ?
                group by 1
                """, tenantId, start, end);
        Map<LocalDate, TrafficPoint> byDay = new HashMap<>();
        for (Map<String, Object> r : rows) {
            LocalDate d = ((java.sql.Date) r.get("day")).toLocalDate();
            byDay.put(d, new TrafficPoint(d.toString(), asLong(r.get("inbound")), asLong(r.get("outbound"))));
        }
        List<TrafficPoint> out = new ArrayList<>(rangeDays);
        for (int i = 0; i < rangeDays; i++) {
            LocalDate d = startDay.plusDays(i);
            out.add(byDay.getOrDefault(d, new TrafficPoint(d.toString(), 0, 0)));
        }
        return out;
    }

    private List<AiCostPoint> aiCostByDay(UUID tenantId, SqlParameterValue start, SqlParameterValue end,
                                           LocalDate startDay, int rangeDays) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select date_trunc('day', created_at at time zone 'UTC')::date as day,
                       count(*) as runs,
                       coalesce(sum(prompt_tokens + completion_tokens),0) as tokens,
                       coalesce(sum(cost_estimate_usd),0) as cost
                from ai_runs
                where tenant_id = ? and created_at >= ? and created_at < ?
                group by 1
                """, tenantId, start, end);
        Map<LocalDate, AiCostPoint> byDay = new HashMap<>();
        for (Map<String, Object> r : rows) {
            LocalDate d = ((java.sql.Date) r.get("day")).toLocalDate();
            byDay.put(d, new AiCostPoint(d.toString(), asLong(r.get("runs")),
                    asLong(r.get("tokens")), asDouble(r.get("cost"))));
        }
        List<AiCostPoint> out = new ArrayList<>(rangeDays);
        for (int i = 0; i < rangeDays; i++) {
            LocalDate d = startDay.plusDays(i);
            out.add(byDay.getOrDefault(d, new AiCostPoint(d.toString(), 0, 0, 0.0)));
        }
        return out;
    }

    private List<TopCustomerRow> topCustomers(UUID tenantId, SqlParameterValue start, SqlParameterValue end) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select c.id as customer_id,
                       coalesce(nullif(c.display_name, ''), nullif(c.profile_name, ''), c.phone) as name,
                       c.phone as phone,
                       count(m.id) as message_count
                from customers c
                join conversations cv on cv.customer_id = c.id and cv.tenant_id = c.tenant_id
                join messages m on m.conversation_id = cv.id and m.tenant_id = cv.tenant_id
                where c.tenant_id = ? and m.created_at >= ? and m.created_at < ?
                group by c.id, name, c.phone
                order by message_count desc
                limit 5
                """, tenantId, start, end);
        List<TopCustomerRow> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            out.add(new TopCustomerRow(
                    (UUID) r.get("customer_id"),
                    (String) r.get("name"),
                    (String) r.get("phone"),
                    asLong(r.get("message_count"))));
        }
        return out;
    }

    /**
     * First-response latency: for each inbound message that received an
     * outbound reply, the seconds between them. Bucketed into <10s,
     * 10s–1m, 1m–5m, 5m–30m, >30m.
     */
    private List<ResponseTimeBucket> responseTimeBuckets(UUID tenantId, SqlParameterValue start, SqlParameterValue end) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                with paired as (
                  select m.id as inbound_id, m.conversation_id, m.created_at as inbound_at,
                         (select min(o.created_at) from messages o
                          where o.conversation_id = m.conversation_id
                            and o.direction = 'outbound'
                            and o.created_at > m.created_at) as first_outbound
                  from messages m
                  where m.tenant_id = ?
                    and m.direction = 'inbound'
                    and m.created_at >= ? and m.created_at < ?
                )
                select case
                         when first_outbound is null then 'no_reply'
                         when extract(epoch from (first_outbound - inbound_at)) < 10 then 'lt_10s'
                         when extract(epoch from (first_outbound - inbound_at)) < 60 then 'lt_1m'
                         when extract(epoch from (first_outbound - inbound_at)) < 300 then 'lt_5m'
                         when extract(epoch from (first_outbound - inbound_at)) < 1800 then 'lt_30m'
                         else 'gt_30m'
                       end as bucket,
                       count(*) as ct
                from paired
                group by 1
                """, tenantId, start, end);
        Map<String, Long> totals = new HashMap<>();
        for (Map<String, Object> r : rows) {
            totals.put((String) r.get("bucket"), asLong(r.get("ct")));
        }
        LinkedHashMap<String, String> bucketLabels = new LinkedHashMap<>();
        bucketLabels.put("lt_10s",  "<10s");
        bucketLabels.put("lt_1m",   "10s–1m");
        bucketLabels.put("lt_5m",   "1m–5m");
        bucketLabels.put("lt_30m",  "5m–30m");
        bucketLabels.put("gt_30m",  ">30m");
        bucketLabels.put("no_reply", "No reply");
        List<ResponseTimeBucket> out = new ArrayList<>(bucketLabels.size());
        for (Map.Entry<String, String> e : bucketLabels.entrySet()) {
            out.add(new ResponseTimeBucket(e.getKey(), e.getValue(),
                    totals.getOrDefault(e.getKey(), 0L)));
        }
        return out;
    }

    private List<ChannelBreakdownRow> channelBreakdown(UUID tenantId, SqlParameterValue start, SqlParameterValue end) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select ch.id as channel_id,
                       coalesce(ch.display_name, ch.provider) as name,
                       ch.provider as provider,
                       count(m.id) as message_count
                from channels ch
                left join conversations cv on cv.channel_id = ch.id and cv.tenant_id = ch.tenant_id
                left join messages m on m.conversation_id = cv.id and m.tenant_id = cv.tenant_id
                  and m.created_at >= ? and m.created_at < ?
                where ch.tenant_id = ?
                group by ch.id, name, ch.provider
                order by message_count desc
                """, start, end, tenantId);
        List<ChannelBreakdownRow> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            out.add(new ChannelBreakdownRow(
                    (UUID) r.get("channel_id"),
                    (String) r.get("name"),
                    String.valueOf(r.get("provider")),
                    asLong(r.get("message_count"))));
        }
        return out;
    }

    private static long asLong(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
    private static double asDouble(Object o) { return o == null ? 0.0 : ((Number) o).doubleValue(); }

    private static SqlParameterValue timestampParam(Instant value) {
        return new SqlParameterValue(
                Types.TIMESTAMP_WITH_TIMEZONE,
                OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
    }

    @SuppressWarnings("unused") // reserved for tests
    static long daysBetween(Instant a, Instant b) {
        return ChronoUnit.DAYS.between(a, b);
    }
}
