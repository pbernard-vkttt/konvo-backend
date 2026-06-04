package com.vulkantechtt.konvo.knowledge;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the <em>short</em> database transactions for knowledge indexing.
 * {@link KnowledgeIndexer} does the slow work — chunking and the embedding
 * provider call — with no transaction held, then calls in here to persist the
 * result. Keeping these methods in a separate bean ensures the
 * {@code @Transactional} proxy applies (a self-call from the indexer would
 * bypass it) and bounds each DB transaction to the local insert/update work
 * (audit H-3).
 */
@Component
public class KnowledgeChunkWriter {

    private static final String INSERT_CHUNK = """
            insert into knowledge_chunks (
                tenant_id, source_id, ordinal, content, token_count, embedding
            ) values (?, ?, ?, ?, ?, ?::vector)
            """;

    private final JdbcTemplate jdbc;

    public KnowledgeChunkWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Replaces all chunks for a source with the freshly-embedded set and flips
     * the source to {@code ready}. Inserts are batched in a single round-trip.
     */
    @Transactional
    public void replaceChunks(UUID tenantId, UUID sourceId, List<String> chunks, List<float[]> embeddings) {
        jdbc.update("delete from knowledge_chunks where source_id = ?", sourceId);

        jdbc.batchUpdate(INSERT_CHUNK, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                String content = chunks.get(i);
                int tokenEstimate = content.length() / 4; // cheap heuristic; OpenAI-ish
                ps.setObject(1, tenantId);
                ps.setObject(2, sourceId);
                ps.setInt(3, i);
                ps.setString(4, content);
                ps.setInt(5, tokenEstimate);
                ps.setString(6, VectorFormatter.toLiteral(embeddings.get(i)));
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });

        jdbc.update("update knowledge_sources set status = 'ready', chunk_count = ?, updated_at = now() where id = ?",
                chunks.size(), sourceId);
    }

    /** Source had no indexable content — mark ready with zero chunks. */
    @Transactional
    public void markEmpty(UUID sourceId) {
        jdbc.update("delete from knowledge_chunks where source_id = ?", sourceId);
        jdbc.update("update knowledge_sources set status = 'ready', chunk_count = 0, updated_at = now() where id = ?",
                sourceId);
    }

    /** Provider/JDBC error during indexing — mark failed for retry/visibility. */
    @Transactional
    public void markFailed(UUID sourceId) {
        jdbc.update("update knowledge_sources set status = 'failed', updated_at = now() where id = ?", sourceId);
    }
}
