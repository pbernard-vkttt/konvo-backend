package com.vulkantechtt.konvo.ai;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inserts {@code ai_runs} audit rows. Plain JdbcTemplate to keep the JPA
 * model thin (no entity needed; this table is append-only telemetry).
 */
@Component
public class AiRunRecorder {

    private static final String INSERT = """
            insert into ai_runs (
                tenant_id, conversation_id, purpose, provider, model,
                prompt_tokens, completion_tokens, cost_estimate_usd,
                latency_ms, status, error_message
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public AiRunRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void recordOk(UUID tenantId, UUID conversationId, String purpose,
                          String provider, String model,
                          int promptTokens, int completionTokens, double cost,
                          int latencyMs) {
        jdbc.update(INSERT, tenantId, conversationId, purpose, provider, model,
                promptTokens, completionTokens, cost, latencyMs, "ok", null);
    }

    public void recordFailure(UUID tenantId, UUID conversationId, String purpose,
                               String provider, String model, int latencyMs, String error) {
        jdbc.update(INSERT, tenantId, conversationId, purpose, provider, model,
                0, 0, 0.0, latencyMs, "failed", truncate(error, 4000));
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
