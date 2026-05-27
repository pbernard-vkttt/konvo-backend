package com.vulkantechtt.konvo.knowledge;

import com.vulkantechtt.konvo.ai.AiEmbeddingProvider;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chunks a {@link KnowledgeSource}, calls the embedding provider, and inserts
 * one row per chunk through {@link JdbcTemplate} so the {@code vector(1536)}
 * column can be written with a {@code ?::vector} cast.
 *
 * Runs async via {@code @EnableAsync} (set on the application class in M1).
 * The source row's {@code status} transitions {@code indexing → ready} on
 * success or {@code failed} on any provider/JDBC error.
 */
@Service
public class KnowledgeIndexer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexer.class);

    private static final String INSERT_CHUNK = """
            insert into knowledge_chunks (
                tenant_id, source_id, ordinal, content, token_count, embedding
            ) values (?, ?, ?, ?, ?, ?::vector)
            """;

    private final KnowledgeSourceRepository sources;
    private final AiEmbeddingProvider embedder;
    private final JdbcTemplate jdbc;

    public KnowledgeIndexer(
            KnowledgeSourceRepository sources,
            AiEmbeddingProvider embedder,
            JdbcTemplate jdbc) {
        this.sources = sources;
        this.embedder = embedder;
        this.jdbc = jdbc;
    }

    @Async
    @Transactional
    public void indexAsync(UUID sourceId) {
        KnowledgeSource source = sources.findById(sourceId).orElse(null);
        if (source == null) {
            log.warn("Index requested for missing source {}", sourceId);
            return;
        }
        try {
            doIndex(source);
            source.setStatus(KnowledgeSourceStatus.ready);
            sources.save(source);
        } catch (Exception e) {
            log.error("Indexing failed for source {}", sourceId, e);
            source.setStatus(KnowledgeSourceStatus.failed);
            sources.save(source);
        }
    }

    private void doIndex(KnowledgeSource source) {
        // Wipe any previous chunks (cascade isn't sufficient on re-index).
        jdbc.update("delete from knowledge_chunks where source_id = ?", source.getId());

        List<String> chunks = Chunker.chunk(source.getContent());
        if (chunks.isEmpty()) {
            source.setChunkCount(0);
            return;
        }
        List<float[]> embeddings = embedder.embed(chunks);
        if (embeddings.size() != chunks.size()) {
            throw new IllegalStateException(
                    "Embedding count mismatch: " + embeddings.size() + " vs " + chunks.size());
        }

        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i);
            int tokenEstimate = content.length() / 4; // cheap heuristic; OpenAI-ish
            jdbc.update(INSERT_CHUNK,
                    source.getTenantId(),
                    source.getId(),
                    i,
                    content,
                    tokenEstimate,
                    VectorFormatter.toLiteral(embeddings.get(i)));
        }
        source.setChunkCount(chunks.size());
        log.info("Indexed source {} into {} chunks ({} embedder)",
                source.getId(), chunks.size(), embedder.name());
    }
}
