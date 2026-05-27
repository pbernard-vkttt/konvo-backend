package com.vulkantechtt.konvo.knowledge;

import com.vulkantechtt.konvo.ai.AiEmbeddingProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped top-k cosine retrieval. The pgvector {@code <=>} operator
 * is cosine distance, smaller is closer.
 */
@Service
public class KnowledgeRetriever {

    private static final String QUERY = """
            select kc.id, kc.source_id, kc.ordinal, kc.content,
                   kc.embedding <=> ?::vector as distance,
                   ks.title as source_title
            from knowledge_chunks kc
            join knowledge_sources ks on ks.id = kc.source_id
            where kc.tenant_id = ?
              and ks.status = 'ready'
            order by kc.embedding <=> ?::vector
            limit ?
            """;

    private final AiEmbeddingProvider embedder;
    private final JdbcTemplate jdbc;

    public KnowledgeRetriever(AiEmbeddingProvider embedder, JdbcTemplate jdbc) {
        this.embedder = embedder;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Hit> topK(UUID tenantId, String query, int k) {
        if (query == null || query.isBlank() || k <= 0) {
            return List.of();
        }
        float[] queryVector = embedder.embedOne(query);
        String literal = VectorFormatter.toLiteral(queryVector);
        return jdbc.query(
                QUERY,
                (rs, i) -> new Hit(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("source_id"),
                        rs.getString("source_title"),
                        rs.getInt("ordinal"),
                        rs.getString("content"),
                        rs.getDouble("distance")),
                literal, tenantId, literal, k);
    }

    public record Hit(
            UUID chunkId,
            UUID sourceId,
            String sourceTitle,
            int ordinal,
            String content,
            double distance) {}
}
