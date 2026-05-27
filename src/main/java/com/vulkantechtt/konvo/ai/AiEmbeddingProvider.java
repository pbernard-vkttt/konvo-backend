package com.vulkantechtt.konvo.ai;

import java.util.List;

/**
 * Embedding side of the AI layer. Used by the knowledge indexer + retriever
 * for cosine search. Only OpenAI (text-embedding-3-small) and the dev stub
 * implement it in M5 — Groq doesn't ship embeddings, so when a tenant runs
 * chat=groq we still call OpenAI for embeddings (or fall back to stub for
 * dev). Configured via {@code konvo.ai.embedding-provider}.
 */
public interface AiEmbeddingProvider {

    String name();

    /** Dimension of the embedding vector produced. Must match the
     *  {@code vector(N)} column type in the schema. */
    int dimensions();

    /** Embed a single piece of text. Convenience over {@link #embed(List)}. */
    default float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }

    /** Embed a batch. Returns vectors in input order. */
    List<float[]> embed(List<String> inputs);
}
