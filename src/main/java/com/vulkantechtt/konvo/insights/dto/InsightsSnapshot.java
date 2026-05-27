package com.vulkantechtt.konvo.insights.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InsightsSnapshot(
        int rangeDays,
        Instant rangeStart,
        Instant rangeEnd,
        Kpis kpis,
        List<TrafficPoint> trafficByDay,
        List<AiCostPoint> aiCostByDay,
        List<TopCustomerRow> topCustomers,
        List<ResponseTimeBucket> responseTimeBuckets,
        List<ChannelBreakdownRow> channelBreakdown) {

    public record Kpis(
            long conversations,
            long aiRuns,
            long aiRunsOk,
            double aiCostUsd,
            long aiAvgLatencyMs) {}

    public record TrafficPoint(String day, long inbound, long outbound) {}

    public record AiCostPoint(String day, long runs, long tokens, double costUsd) {}

    public record TopCustomerRow(UUID customerId, String displayName, String phone, long messageCount) {}

    public record ResponseTimeBucket(String key, String label, long count) {}

    public record ChannelBreakdownRow(UUID channelId, String displayName, String provider, long messageCount) {}
}
