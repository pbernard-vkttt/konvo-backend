package com.vulkantechtt.konvo.knowledge;

import com.vulkantechtt.konvo.ai.AiEmbeddingProvider;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Chunks a {@link KnowledgeSource}, calls the embedding provider, and persists
 * one row per chunk.
 *
 * <p>Runs async via {@code @EnableAsync} (set on the application class in M1).
 * Crucially it is <em>not</em> {@code @Transactional}: the chunking and the
 * embedding provider call (a potentially slow OpenAI/Groq HTTP request) run
 * with no database transaction open, so a slow embed never pins a pooled DB
 * connection (audit H-3). The actual writes happen in short transactions owned
 * by {@link KnowledgeChunkWriter}, which also batches the chunk inserts.
 *
 * <p>The source row's {@code status} transitions {@code indexing → ready} on
 * success or {@code failed} on any provider/JDBC error.
 */
@Service
public class KnowledgeIndexer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexer.class);

    private final KnowledgeSourceRepository sources;
    private final AiEmbeddingProvider embedder;
    private final KnowledgeChunkWriter writer;

    public KnowledgeIndexer(
            KnowledgeSourceRepository sources,
            AiEmbeddingProvider embedder,
            KnowledgeChunkWriter writer) {
        this.sources = sources;
        this.embedder = embedder;
        this.writer = writer;
    }

    @Async
    public void indexAsync(UUID sourceId) {
        KnowledgeSource source = sources.findById(sourceId).orElse(null);
        if (source == null) {
            log.warn("Index requested for missing source {}", sourceId);
            return;
        }
        try {
            // Slow work, no transaction held: chunk + call the embedding provider.
            List<String> chunks = Chunker.chunk(source.getContent());
            if (chunks.isEmpty()) {
                writer.markEmpty(sourceId);
                return;
            }
            List<float[]> embeddings = embedder.embed(chunks);
            if (embeddings.size() != chunks.size()) {
                throw new IllegalStateException(
                        "Embedding count mismatch: " + embeddings.size() + " vs " + chunks.size());
            }

            // Short transaction: replace chunks + flip status to ready.
            writer.replaceChunks(source.getTenantId(), sourceId, chunks, embeddings);
            log.info("Indexed source {} into {} chunks ({} embedder)",
                    sourceId, chunks.size(), embedder.name());
        } catch (Exception e) {
            log.error("Indexing failed for source {}", sourceId, e);
            writer.markFailed(sourceId);
        }
    }
}
